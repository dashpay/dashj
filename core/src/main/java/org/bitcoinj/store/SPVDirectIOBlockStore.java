package org.bitcoinj.store;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.*;
import org.slf4j.*;

import javax.annotation.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.*;

import static com.google.common.base.Preconditions.*;

/**
 * An SPVDirectIOBlockStore holds a limited number of block headers in a memory mapped ring buffer.
 * With such a store, you may not be able to process very deep re-orgs and could be disconnected from the chain
 * (requiring a replay), but as they are virtually unheard of this is not a significant risk.
 *
 * Unlike {@link SPVBlockStore} this class uses direct IO instead of mmap and will be slower.
 */
public class SPVDirectIOBlockStore implements BlockStore {
    private static final Logger log = LoggerFactory.getLogger(SPVDirectIOBlockStore.class);

    /** The default number of headers that will be stored in the ring buffer. */
    public static final int DEFAULT_CAPACITY = 5000;
    public static final String HEADER_MAGIC = "SPVB";

    protected ByteBuffer buffer;
    protected final NetworkParameters params;

    protected ReentrantLock lock = Threading.lock("SPVBlockStore");

    // The entire ring-buffer is accessed through a FileChannel and ByteBuffer, avoiding MappedByteBuffer issues.
    protected FileChannel fileChannel;
    protected FileLock fileLock = null;
    protected RandomAccessFile randomAccessFile;
    private final int fileLength;

    protected LinkedHashMap<Sha256Hash, StoredBlock> blockCache = new LinkedHashMap<Sha256Hash, StoredBlock>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, StoredBlock> entry) {
            return size() > 2050;  // Slightly more than the difficulty transition period.
        }
    };

    private static final Object NOT_FOUND_MARKER = new Object();
    protected LinkedHashMap<Sha256Hash, Object> notFoundCache = new LinkedHashMap<Sha256Hash, Object>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, Object> entry) {
            return size() > 100;  // This was chosen arbitrarily.
        }
    };

    /**
     * Creates and initializes an SPV block store that can hold {@link #DEFAULT_CAPACITY} block headers. Will create the
     * given file if it's missing. This operation will block on disk.
     * @param file file to use for the block store
     * @throws BlockStoreException if something goes wrong
     */
    public SPVDirectIOBlockStore(NetworkParameters params, File file) throws BlockStoreException {
        this(params, file, DEFAULT_CAPACITY, false);
    }

    /**
     * Creates and initializes an SPV block store that can hold a given amount of blocks. Will create the given file if
     * it's missing. This operation will block on disk.
     * @param file file to use for the block store
     * @param capacity custom capacity in number of block headers
     * @throws BlockStoreException if something goes wrong
     */
    public SPVDirectIOBlockStore(NetworkParameters params, File file, int capacity, boolean grow) throws BlockStoreException {
        checkNotNull(file);
        this.params = checkNotNull(params);
        checkArgument(capacity > 0);
        try {
            boolean exists = file.exists();
            // Set up the backing file.
            randomAccessFile = new RandomAccessFile(file, "rw");
            fileLength = getFileSize(capacity);

            if (!exists) {
                log.info("Creating new SPV block chain file " + file);
                randomAccessFile.setLength(fileLength);
            } else if (randomAccessFile.length() != fileLength) {
                final long currentLength = randomAccessFile.length();
                if (currentLength != fileLength) {
                    if ((currentLength - FILE_PROLOGUE_BYTES) % RECORD_SIZE != 0)
                        throw new BlockStoreException(
                                "File size on disk indicates this is not a block store: " + currentLength);
                    else if (!grow)
                        throw new BlockStoreException("File size on disk does not match expected size: " + currentLength
                                + " vs " + fileLength);
                    else if (fileLength < randomAccessFile.length())
                        throw new BlockStoreException(
                                "Shrinking is unsupported: " + currentLength + " vs " + fileLength);
                    else
                        randomAccessFile.setLength(fileLength);
                }
            }
            fileChannel = randomAccessFile.getChannel();
            fileLock = fileChannel.tryLock();
            if (fileLock == null)
                throw new ChainFileLockedException("Store file is already locked by another process");

            buffer = ByteBuffer.allocateDirect(fileLength);
            int bytesRead = fileChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip(); // Only flip if data was read
                //log.info("Buffer state before flip: position=" + buffer.position() + ", limit=" + buffer.limit());
            } else {
                buffer.clear(); // Clear if no data
                //log.info("Buffer state before flip: position=" + buffer.position() + ", limit=" + buffer.limit());
            }

            if (!exists) {
                initNewStore(params);
            } else {
                byte[] header = new byte[4];
                buffer.get(header);
                if (!new String(header, StandardCharsets.US_ASCII).equals(HEADER_MAGIC)) {
                    throw new BlockStoreException("Invalid file header");
                }
            }
        } catch (Exception e) {
            try {
                if (randomAccessFile != null) randomAccessFile.close();
            } catch (IOException e2) {
                throw new BlockStoreException(e2);
            }
            throw new BlockStoreException(e);
        }
    }

    private void initNewStore(NetworkParameters params) throws IOException, BlockStoreException {
        byte[] header = HEADER_MAGIC.getBytes(StandardCharsets.US_ASCII);
        buffer.put(header);

        lock.lock();
        try {
            setRingCursor(buffer, FILE_PROLOGUE_BYTES);
        } finally {
            lock.unlock();
        }
        Block genesis = params.getGenesisBlock().cloneAsHeader();
        StoredBlock storedGenesis = new StoredBlock(genesis, genesis.getWork(), 0);
        put(storedGenesis);
        setChainHead(storedGenesis);
        flushBuffer();
    }

    /** Returns the size in bytes of the file that is used to store the chain with the current parameters. */
    public static final int getFileSize(int capacity) {
        return RECORD_SIZE * capacity + FILE_PROLOGUE_BYTES /* extra kilobyte for stuff */;
    }

    @Override
    public void put(StoredBlock block) throws BlockStoreException {
        final ByteBuffer buffer = this.buffer;
        if (buffer == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            int cursor = getRingCursor(buffer);
            if (cursor == buffer.capacity()) {
                cursor = FILE_PROLOGUE_BYTES;
            }
            buffer.position(cursor);
            Sha256Hash hash = block.getHeader().getHash();
            notFoundCache.remove(hash);
            buffer.put(hash.getBytes());
            block.serializeCompact(buffer);
            setRingCursor(buffer, buffer.position());
            blockCache.put(hash, block);
            flushBuffer();
        } catch (IOException e) {
            throw new BlockStoreException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Nullable
    public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        final ByteBuffer buffer = this.buffer;
        if (buffer == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            StoredBlock cacheHit = blockCache.get(hash);
            if (cacheHit != null)
                return cacheHit;
            if (notFoundCache.get(hash) != null)
                return null;

            int cursor = getRingCursor(buffer);
            final int startingPoint = cursor;
            final byte[] targetHashBytes = hash.getBytes();
            byte[] scratch = new byte[32];
            do {
                cursor -= RECORD_SIZE;
                if (cursor < FILE_PROLOGUE_BYTES) {
                    // We hit the start, so wrap around.
                    cursor = buffer.capacity() - RECORD_SIZE;
                }
                // Cursor is now at the start of the next record to check, so read the hash and compare it.
                buffer.position(cursor);
                buffer.get(scratch);
                if (Arrays.equals(scratch, targetHashBytes)) {
                    StoredBlock storedBlock = StoredBlock.deserializeCompact(params, buffer);
                    blockCache.put(hash, storedBlock);
                    return storedBlock;
                }
            } while (cursor != startingPoint);
            // Not found.
            notFoundCache.put(hash, NOT_FOUND_MARKER);
            return null;
        } catch (ProtocolException e) {
            throw new BlockStoreException(e);
        } finally {
            lock.unlock();
        }
    }

    protected StoredBlock lastChainHead = null;

    @Override
    public StoredBlock getChainHead() throws BlockStoreException {
        final ByteBuffer buffer = this.buffer;
        if (buffer == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            if (lastChainHead == null) {
                byte[] headHash = new byte[32];
                buffer.position(8);
                buffer.get(headHash);
                Sha256Hash hash = Sha256Hash.wrap(headHash);
                StoredBlock block = get(hash);
                if (block == null)
                    throw new BlockStoreException("Corrupted block store: could not find chain head: " + hash);
                lastChainHead = block;
            }
            return lastChainHead;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        if (buffer == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            lastChainHead = chainHead;
            byte[] headHash = chainHead.getHeader().getHash().getBytes();
            buffer.position(8);
            buffer.put(headHash);
            flushBuffer();
        } catch (IOException e) {
            throw new BlockStoreException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws BlockStoreException {
        try {
            flushBuffer();
            fileChannel.close();
            randomAccessFile.close();
            blockCache.clear();
        } catch (IOException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }

    protected static final int RECORD_SIZE = 32 /* hash */ + StoredBlock.COMPACT_SERIALIZED_SIZE;

    // File format:
    //   4 header bytes = "SPVB"
    //   4 cursor bytes, which indicate the offset from the first kb where the next block header should be written.
    //   32 bytes for the hash of the chain head
    //
    // For each header (128 bytes)
    //   32 bytes hash of the header
    //   12 bytes of chain work
    //    4 bytes of height
    //   80 bytes of block header data
    protected static final int FILE_PROLOGUE_BYTES = 1024;

    private int getRingCursor(ByteBuffer buffer) {
        if (buffer.limit() < 8) {
            throw new IllegalStateException("Buffer does not contain enough data to read ring cursor.");
        }
        int c = buffer.getInt(4);
        checkState(c >= FILE_PROLOGUE_BYTES, "Integer overflow");
        return c;
    }

    private void setRingCursor(ByteBuffer buffer, int newCursor) {
        checkArgument(newCursor >= 0);
        buffer.putInt(4, newCursor);
    }

    private void flushBuffer() throws IOException {
        if (buffer.position() > 0) { // Only flip if there's data to write
            buffer.flip();
            fileChannel.position(0);
            fileChannel.write(buffer);
            buffer.compact(); // Preserve any remaining data
        }
    }

    @Nullable
    public StoredBlock get(int blockHeight) throws BlockStoreException {

        lock.lock();
        try {
            StoredBlock cursor = getChainHead();

            if(cursor.getHeight() < blockHeight)
                return null;


            while (cursor != null) {
                if(cursor.getHeight() == blockHeight)
                    return cursor;

                cursor = get(cursor.getHeader().getPrevBlockHash());
            }

            return null;
        } finally { lock.unlock(); }
    }

    @Override
    public StoredBlock getChainHeadFromHash(Sha256Hash hash) throws BlockStoreException {

        StoredBlock cursor = get(hash);
        StoredBlock current = cursor;
        while (cursor != null) {
            cursor = getNextBlock(cursor.getHeader().getHash());
            if (cursor == null)
                return current;
            current = cursor;
        }

        return null;
    }

    private StoredBlock getNextBlock(Sha256Hash hash) {
        for (Map.Entry<Sha256Hash, StoredBlock> entry : blockCache.entrySet()) {
            if (entry.getValue().getHeader().getPrevBlockHash().equals(hash))
                return entry.getValue();
        }
        return null;
    }

    public void clear() throws Exception {
        lock.lock();
        try {
            // Clear caches
            blockCache.clear();
            notFoundCache.clear();
            // Clear file content
            buffer.position(0);
            long fileLength = randomAccessFile.length();
            for (int i = 0; i < fileLength; i++) {
                buffer.put((byte)0);
            }
            // Initialize store again
            buffer.position(0);
            initNewStore(params);
        } finally { lock.unlock(); }
    }
}

