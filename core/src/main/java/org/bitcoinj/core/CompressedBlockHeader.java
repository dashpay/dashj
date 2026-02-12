/*
 * Copyright 2026 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents a compressed block header as defined in DIP-0025.
 *
 * <p>The compression uses a 1-byte bitfield to indicate which fields are present:</p>
 * <ul>
 *   <li>Bits 0-2: Version handling (index 0-6 into last 7 distinct versions, or 7 = new version follows)</li>
 *   <li>Bit 3: Previous block hash (0=omitted, 1=included)</li>
 *   <li>Bit 4: Timestamp (0=2-byte signed offset from previous, 1=full 4-byte value)</li>
 *   <li>Bit 5: nBits (0=same as previous, 1=new 4-byte value follows)</li>
 *   <li>Bits 6-7: Reserved (must be 0)</li>
 * </ul>
 *
 * <p>The first header in a batch MUST include all fields (no compression).</p>
 *
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 *
 * @see <a href="https://github.com/dashpay/dips/blob/master/dip-0025.md">DIP-0025</a>
 */
public class CompressedBlockHeader extends Message {
    private static final Logger log = LoggerFactory.getLogger(CompressedBlockHeader.class);

    // Bitfield masks and values
    /** Mask for version bits (bits 0-2) */
    public static final int VERSION_BIT_MASK = 0x07;
    /** Value indicating new version follows (0b111) */
    public static final int VERSION_BIT_NEW = 0x07;
    /** Bit 3: prevBlockHash is included */
    public static final int PREV_BLOCK_HASH_BIT = 0x08;
    /** Bit 4: Full 4-byte timestamp (vs 2-byte offset) */
    public static final int TIMESTAMP_FULL_BIT = 0x10;
    /** Bit 5: nBits has new value (vs same as previous) */
    public static final int NBITS_NEW_BIT = 0x20;
    /** Mask for reserved bits (bits 6-7) */
    public static final int RESERVED_BITS_MASK = 0xC0;

    /** Maximum signed short value for 2-byte timestamp offset */
    private static final int MAX_TIMESTAMP_OFFSET = 32767;
    /** Minimum signed short value for 2-byte timestamp offset */
    private static final int MIN_TIMESTAMP_OFFSET = -32768;

    // Header fields
    private int bitfield;
    private long version;
    private Sha256Hash prevBlockHash;
    private Sha256Hash merkleRoot;
    private long time;
    private long difficultyTarget;
    private long nonce;

    // Compression state
    private boolean isFirstInBatch;
    private long timestampOffset; // Used during serialization

    /**
     * Creates a CompressedBlockHeader by parsing from payload bytes.
     *
     * @param params the network parameters
     * @param payload the raw bytes
     * @param offset the offset into the payload to start parsing
     * @param context the compression context for decompression
     * @param isFirst true if this is the first header in a batch
     * @throws ProtocolException if parsing fails
     */
    public CompressedBlockHeader(NetworkParameters params, byte[] payload, int offset,
                                  CompressedHeaderContext context, boolean isFirst)
            throws ProtocolException {
        super(params);
        this.payload = payload;
        this.cursor = offset;
        this.offset = offset;
        this.isFirstInBatch = isFirst;
        parseWithContext(context);
    }

    /**
     * Creates a CompressedBlockHeader from a full Block header for serialization.
     *
     * @param params the network parameters
     * @param header the full block header to compress
     * @param context the compression context
     * @param isFirst true if this is the first header in a batch
     */
    public CompressedBlockHeader(NetworkParameters params, Block header,
                                  CompressedHeaderContext context, boolean isFirst) {
        super(params);
        this.isFirstInBatch = isFirst;
        this.version = header.getVersion();
        this.prevBlockHash = header.getPrevBlockHash();
        this.merkleRoot = header.getMerkleRoot();
        this.time = header.getTimeSeconds();
        this.difficultyTarget = header.getDifficultyTarget();
        this.nonce = header.getNonce();
        computeBitfield(context);
        computeLength();
    }

    @Override
    protected void parse() throws ProtocolException {
        // Basic parse - actual parsing is done in parseWithContext
    }


    /* last good blocik
     block:
   hash: 000000ed3db503b4f783c1afd086b0ecd7111909fa3fbddef7a0b7d7847560d2
   version: 536872960 (0x20000800) (BIP34, BIP66, BIP65)
   previous block: 000000791c40db19eed02b1be9b2f10d63a50e6b98ff507983ca6095f990cfc4
   merkle root: d6441f36d4c5ecfa182cd5245233258e2607a1f39084fb3f8d0e6a2dec639919
   time: 1732730462 (2024-11-27T18:01:02Z)
   difficulty target (nBits): 503378505
   nonce: 3220765

     */
    /**
     * Parse the compressed header using the provided context for decompression.
     */
    private void parseWithContext(CompressedHeaderContext context) throws ProtocolException {
        // Read bitfield (1 byte)
        bitfield = readBytes(1)[0] & 0xFF;

        // Note: We intentionally ignore reserved bits (6-7) for forward compatibility.
        // Future protocol versions may use these bits for new features.

        // Parse version (bits 0-2)
        // Based on C++ IsVersionCompressed(): returns (versionBits != 0)
        // - versionBits == 0: NOT compressed, version in stream (4 bytes)
        // - versionBits 1-7: compressed, use table at index (versionBits - 1)
        int versionBits = bitfield & VERSION_BIT_MASK;
        if (versionBits == 0) {
            // Version is in stream - read and save as most recent
            version = readUint32();
            context.saveVersionAsMostRecent(version);
        } else {
            // Use version from table at index (versionBits - 1)
            int tableIndex = versionBits - 1;
            if (tableIndex < context.getVersionTableSize()) {
                version = context.getVersionAt(tableIndex);
                // Mark this version as most recently used (moves to front of LRU list)
                context.markVersionAsMostRecent(tableIndex);
            } else {
                // Table doesn't have this index yet - this shouldn't happen in normal operation
                // but handle gracefully by reading from stream
                log.warn("Version table index {} not available (table size {}), versionBits={}",
                        tableIndex, context.getVersionTableSize(), versionBits);
                version = readUint32();
                context.saveVersionAsMostRecent(version);
            }
        }

        // Parse prevBlockHash (bit 3)
        int cursorBeforePrevHash = cursor;
        if ((bitfield & PREV_BLOCK_HASH_BIT) != 0) {
            // Full 32-byte hash follows
            prevBlockHash = readHash();
            //log.info("Read prevBlockHash from stream: {}", prevBlockHash);
        } else {
            // Derived from previous header's hash
            prevBlockHash = context.getPreviousBlockHash();
            //log.info("Using prevBlockHash from context: {}", prevBlockHash);
        }
//        log.info("After prevBlockHash: cursorBefore={}, cursorAfter={}, bytesRead={}",
//                cursorBeforePrevHash, cursor, cursor - cursorBeforePrevHash);

        // merkleRoot is always present (32 bytes)
        int cursorBeforeMerkle = cursor;
        // Log raw bytes before parsing merkleRoot
//        if (log.isInfoEnabled() && cursor + 32 <= payload.length) {
//            StringBuilder sb = new StringBuilder();
//            for (int i = 0; i < Math.min(40, payload.length - cursor); i++) {
//                sb.append(String.format("%02x", payload[cursor + i] & 0xFF));
//            }
//            log.info("Raw bytes at merkleRoot position (cursor={}): {}", cursor, sb.toString());
//        }
        merkleRoot = readHash();
//        log.info("After merkleRoot: cursorBefore={}, cursorAfter={}, merkleRoot={}",
//                cursorBeforeMerkle, cursor, merkleRoot);

        // Parse timestamp (bit 4)
        if ((bitfield & TIMESTAMP_FULL_BIT) != 0) {
            // Full 4-byte timestamp
            time = readUint32();
        } else {
            // 2-byte signed offset from previous timestamp
            int offsetValue = readUint16();
            // Sign extend if the high bit is set (negative offset)
            if ((offsetValue & 0x8000) != 0) {
                offsetValue |= 0xFFFF0000;
            }
            time = context.getPreviousTimestamp() + offsetValue;
        }

        // Parse nBits (bit 5)
        if ((bitfield & NBITS_NEW_BIT) != 0) {
            // Full 4-byte nBits
            difficultyTarget = readUint32();
        } else {
            // Same as previous
            difficultyTarget = context.getPreviousNBits();
        }

        // nonce is always present (4 bytes)
        nonce = readUint32();

        // Calculate message length
        length = cursor - offset;

//        log.info("Parsed compressed header: version={}, prevHash={}, time={}, nBits={}, nonce={}, bytesRead={}",
//                version, prevBlockHash, time, difficultyTarget, nonce, length);
//        log.info("{}", toBlock());
    }

    /**
     * Compute the bitfield for serialization based on what can be compressed.
     * Also updates the context's version table (LRU).
     */
    private void computeBitfield(CompressedHeaderContext context) {
        bitfield = 0;

        if (isFirstInBatch) {
            // First header: versionBits=0 means version in stream, include all fields
            bitfield = PREV_BLOCK_HASH_BIT | TIMESTAMP_FULL_BIT | NBITS_NEW_BIT;
            // versionBits stays 0 (version will be written to stream)
            // Save version to LRU table
            context.saveVersionAsMostRecent(version);
            return;
        }

        // Version encoding:
        // - versionBits 0: version in stream (not using table)
        // - versionBits 1-7: use table at index (versionBits - 1), i.e., indices 0-6
        int versionIndex = context.getVersionIndex(version);
        if (versionIndex >= 0 && versionIndex < 7) {
            // Can use table: versionBits = tableIndex + 1 (1-7)
            bitfield |= (versionIndex + 1);
            // Mark as most recently used
            context.markVersionAsMostRecent(versionIndex);
        } else {
            // Version not in table, versionBits stays 0 (version in stream)
            context.saveVersionAsMostRecent(version);
        }

        // prevBlockHash - include if not derivable from previous header hash
        // Note: in a contiguous sequence, prevBlockHash should equal the hash of the previous header
        // For safety, we always include it if it doesn't match
        if (!prevBlockHash.equals(context.getPreviousBlockHash())) {
            bitfield |= PREV_BLOCK_HASH_BIT;
        }

        // Timestamp - check if 2-byte offset is sufficient
        long offset = time - context.getPreviousTimestamp();
        if (offset < MIN_TIMESTAMP_OFFSET || offset > MAX_TIMESTAMP_OFFSET) {
            bitfield |= TIMESTAMP_FULL_BIT;
        } else {
            timestampOffset = offset;
        }

        // nBits - include if changed from previous
        if (difficultyTarget != context.getPreviousNBits()) {
            bitfield |= NBITS_NEW_BIT;
        }
    }

    /**
     * Compute the serialized length based on the bitfield.
     */
    private void computeLength() {
        length = 1; // bitfield

        // Version: present only if versionBits == 0 (not compressed)
        int versionBits = bitfield & VERSION_BIT_MASK;
        if (versionBits == 0) {
            length += 4;
        }

        // prevBlockHash
        if ((bitfield & PREV_BLOCK_HASH_BIT) != 0) {
            length += 32;
        }

        // merkleRoot (always present)
        length += 32;

        // Timestamp
        if ((bitfield & TIMESTAMP_FULL_BIT) != 0) {
            length += 4;
        } else {
            length += 2;
        }

        // nBits
        if ((bitfield & NBITS_NEW_BIT) != 0) {
            length += 4;
        }

        // nonce (always present)
        length += 4;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        // Write bitfield
        stream.write(bitfield);

        // Version: write only if versionBits == 0 (not compressed)
        int versionBits = bitfield & VERSION_BIT_MASK;
        if (versionBits == 0) {
            Utils.uint32ToByteStreamLE(version, stream);
        }

        // prevBlockHash
        if ((bitfield & PREV_BLOCK_HASH_BIT) != 0) {
            stream.write(prevBlockHash.getReversedBytes());
        }

        // merkleRoot (always present)
        stream.write(merkleRoot.getReversedBytes());

        // Timestamp
        if ((bitfield & TIMESTAMP_FULL_BIT) != 0) {
            Utils.uint32ToByteStreamLE(time, stream);
        } else {
            Utils.uint16ToByteStreamLE((int) timestampOffset, stream);
        }

        // nBits
        if ((bitfield & NBITS_NEW_BIT) != 0) {
            Utils.uint32ToByteStreamLE(difficultyTarget, stream);
        }

        // nonce (always present)
        Utils.uint32ToByteStreamLE(nonce, stream);
    }

    /**
     * Convert this compressed header to a full Block header.
     *
     * @return a new Block object containing the decompressed header data
     */
    public Block toBlock() {
        Block block = new Block(params, version, prevBlockHash, merkleRoot,
                time, difficultyTarget, nonce, java.util.Collections.emptyList());
        return block.cloneAsHeader();
    }

    // Getters

    public int getBitfield() {
        return bitfield;
    }

    public long getVersion() {
        return version;
    }

    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    public Sha256Hash getMerkleRoot() {
        return merkleRoot;
    }

    public long getTimeSeconds() {
        return time;
    }

    public long getDifficultyTarget() {
        return difficultyTarget;
    }

    public long getNonce() {
        return nonce;
    }

    public boolean isFirstInBatch() {
        return isFirstInBatch;
    }

    @Override
    public String toString() {
        return "CompressedBlockHeader{" +
                "bitfield=0x" + Integer.toHexString(bitfield) +
                ", version=" + version +
                ", prevBlockHash=" + prevBlockHash +
                ", merkleRoot=" + merkleRoot +
                ", time=" + time +
                ", difficultyTarget=" + difficultyTarget +
                ", nonce=" + nonce +
                ", length=" + length +
                '}';
    }
}
