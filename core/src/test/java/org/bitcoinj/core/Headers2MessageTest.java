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

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class Headers2MessageTest {
    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final NetworkParameters UNITTEST = UnitTestParams.get();

    private Block createBlock(long version, Sha256Hash prevHash, Sha256Hash merkleRoot,
                              long time, long difficultyTarget, long nonce) {
        return new Block(UNITTEST, version, prevHash, merkleRoot, time, difficultyTarget, nonce,
                Collections.emptyList()).cloneAsHeader();
    }

    private Sha256Hash randomHash() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            bytes[i] = (byte) (Math.random() * 256);
        }
        return Sha256Hash.wrap(bytes);
    }

    // --- Headers2Message tests ---

    @Test
    public void roundTripSingleHeader() throws Exception {
        Block block = createBlock(536872960L, randomHash(), randomHash(),
                1732730462L, 503378505L, 3220765L);

        Headers2Message original = new Headers2Message(UNITTEST, block);
        byte[] serialized = original.bitcoinSerialize();

        Headers2Message deserialized = new Headers2Message(UNITTEST, serialized);
        List<Block> headers = deserialized.getBlockHeaders();

        assertEquals(1, headers.size());
        assertEquals(block.getVersion(), headers.get(0).getVersion());
        assertEquals(block.getPrevBlockHash(), headers.get(0).getPrevBlockHash());
        assertEquals(block.getMerkleRoot(), headers.get(0).getMerkleRoot());
        assertEquals(block.getTimeSeconds(), headers.get(0).getTimeSeconds());
        assertEquals(block.getDifficultyTarget(), headers.get(0).getDifficultyTarget());
        assertEquals(block.getNonce(), headers.get(0).getNonce());
    }

    @Test
    public void roundTripMultipleHeaders() throws Exception {
        List<Block> blocks = new ArrayList<>();
        Sha256Hash prevHash = randomHash();
        long baseTime = 1732730000L;

        for (int i = 0; i < 10; i++) {
            Block block = createBlock(536872960L, prevHash, randomHash(),
                    baseTime + (i * 150), 503378505L, i * 1000L);
            blocks.add(block);
            prevHash = block.getHash();
        }

        Headers2Message original = new Headers2Message(UNITTEST, blocks);
        byte[] serialized = original.bitcoinSerialize();

        Headers2Message deserialized = new Headers2Message(UNITTEST, serialized);
        List<Block> headers = deserialized.getBlockHeaders();

        assertEquals(blocks.size(), headers.size());
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals("Header " + i + " version mismatch",
                    blocks.get(i).getVersion(), headers.get(i).getVersion());
            assertEquals("Header " + i + " prevBlockHash mismatch",
                    blocks.get(i).getPrevBlockHash(), headers.get(i).getPrevBlockHash());
            assertEquals("Header " + i + " merkleRoot mismatch",
                    blocks.get(i).getMerkleRoot(), headers.get(i).getMerkleRoot());
            assertEquals("Header " + i + " time mismatch",
                    blocks.get(i).getTimeSeconds(), headers.get(i).getTimeSeconds());
            assertEquals("Header " + i + " difficultyTarget mismatch",
                    blocks.get(i).getDifficultyTarget(), headers.get(i).getDifficultyTarget());
            assertEquals("Header " + i + " nonce mismatch",
                    blocks.get(i).getNonce(), headers.get(i).getNonce());
        }
    }

    @Test
    public void compressionSavesSpace() throws Exception {
        // Create a chain of headers with same version and difficulty (compressible)
        List<Block> blocks = new ArrayList<>();
        Sha256Hash prevHash = randomHash();
        long baseTime = 1732730000L;

        for (int i = 0; i < 10; i++) {
            Block block = createBlock(536872960L, prevHash, randomHash(),
                    baseTime + (i * 150), 503378505L, i * 1000L);
            blocks.add(block);
            prevHash = block.getHash();
        }

        // Serialize as Headers2Message (compressed)
        Headers2Message headers2 = new Headers2Message(UNITTEST, blocks);
        byte[] compressed = headers2.bitcoinSerialize();

        // Serialize as HeadersMessage (uncompressed)
        HeadersMessage headers1 = new HeadersMessage(UNITTEST, blocks);
        byte[] uncompressed = headers1.bitcoinSerialize();

        // Compressed should be smaller
        assertTrue("Compressed (" + compressed.length + ") should be smaller than uncompressed (" +
                uncompressed.length + ")", compressed.length < uncompressed.length);
    }

    @Test
    public void headerCountFromVarargs() {
        Block b1 = createBlock(1L, randomHash(), randomHash(), 1000L, 0x1e0fffffL, 1L);
        Block b2 = createBlock(1L, randomHash(), randomHash(), 2000L, 0x1e0fffffL, 2L);

        Headers2Message msg = new Headers2Message(UNITTEST, b1, b2);
        assertEquals(2, msg.getBlockHeaders().size());
    }

    @Test
    public void emptyHeadersList() throws Exception {
        Headers2Message msg = new Headers2Message(UNITTEST, new ArrayList<>());
        byte[] serialized = msg.bitcoinSerialize();

        Headers2Message deserialized = new Headers2Message(UNITTEST, serialized);
        assertEquals(0, deserialized.getBlockHeaders().size());
    }

    @Test(expected = ProtocolException.class)
    public void tooManyHeadersThrows() throws Exception {
        // Create a payload that claims to have MAX_HEADERS + 1 headers
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new VarInt(Headers2Message.MAX_HEADERS + 1).encode());
        new Headers2Message(UNITTEST, bos.toByteArray());
    }

    @Test
    public void varyingVersions() throws Exception {
        // Test with multiple different versions to exercise version table
        List<Block> blocks = new ArrayList<>();
        Sha256Hash prevHash = randomHash();
        long baseTime = 1732730000L;
        long[] versions = {1L, 2L, 3L, 4L, 536872960L, 536870912L, 536870913L, 536872960L, 1L};

        for (int i = 0; i < versions.length; i++) {
            Block block = createBlock(versions[i], prevHash, randomHash(),
                    baseTime + (i * 150), 503378505L, i * 1000L);
            blocks.add(block);
            prevHash = block.getHash();
        }

        Headers2Message original = new Headers2Message(UNITTEST, blocks);
        byte[] serialized = original.bitcoinSerialize();

        Headers2Message deserialized = new Headers2Message(UNITTEST, serialized);
        List<Block> headers = deserialized.getBlockHeaders();

        assertEquals(blocks.size(), headers.size());
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals("Header " + i + " version mismatch",
                    blocks.get(i).getVersion(), headers.get(i).getVersion());
        }
    }

    @Test
    public void varyingDifficulty() throws Exception {
        // Test with changing difficulty targets
        List<Block> blocks = new ArrayList<>();
        Sha256Hash prevHash = randomHash();
        long baseTime = 1732730000L;
        long[] diffs = {503378505L, 503378505L, 503378600L, 503378600L, 503378700L};

        for (int i = 0; i < diffs.length; i++) {
            Block block = createBlock(536872960L, prevHash, randomHash(),
                    baseTime + (i * 150), diffs[i], i * 1000L);
            blocks.add(block);
            prevHash = block.getHash();
        }

        Headers2Message original = new Headers2Message(UNITTEST, blocks);
        byte[] serialized = original.bitcoinSerialize();

        Headers2Message deserialized = new Headers2Message(UNITTEST, serialized);
        List<Block> headers = deserialized.getBlockHeaders();

        assertEquals(blocks.size(), headers.size());
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals("Header " + i + " difficulty mismatch",
                    blocks.get(i).getDifficultyTarget(), headers.get(i).getDifficultyTarget());
        }
    }

    @Test
    public void fullTimestampForLargeOffset() throws Exception {
        // If timestamp offset > 32767, full timestamp should be used
        List<Block> blocks = new ArrayList<>();
        Sha256Hash prevHash = randomHash();

        Block first = createBlock(536872960L, prevHash, randomHash(),
                1732730000L, 503378505L, 1L);
        blocks.add(first);
        prevHash = first.getHash();

        // Large time jump (> 32767 seconds)
        Block second = createBlock(536872960L, prevHash, randomHash(),
                1732730000L + 40000L, 503378505L, 2L);
        blocks.add(second);

        Headers2Message original = new Headers2Message(UNITTEST, blocks);
        byte[] serialized = original.bitcoinSerialize();

        Headers2Message deserialized = new Headers2Message(UNITTEST, serialized);
        assertEquals(1732730000L + 40000L, deserialized.getBlockHeaders().get(1).getTimeSeconds());
    }

    @Test
    public void negativeTimestampOffset() throws Exception {
        // Timestamp going backwards slightly should use 2-byte offset
        List<Block> blocks = new ArrayList<>();
        Sha256Hash prevHash = randomHash();

        Block first = createBlock(536872960L, prevHash, randomHash(),
                1732730000L, 503378505L, 1L);
        blocks.add(first);
        prevHash = first.getHash();

        // Time goes backward by 100 seconds
        Block second = createBlock(536872960L, prevHash, randomHash(),
                1732730000L - 100L, 503378505L, 2L);
        blocks.add(second);

        Headers2Message original = new Headers2Message(UNITTEST, blocks);
        byte[] serialized = original.bitcoinSerialize();

        Headers2Message deserialized = new Headers2Message(UNITTEST, serialized);
        assertEquals(1732730000L - 100L, deserialized.getBlockHeaders().get(1).getTimeSeconds());
    }

    // --- CompressedHeaderContext tests ---

    @Test
    public void contextVersionTableLRU() {
        CompressedHeaderContext ctx = new CompressedHeaderContext();

        // Add versions 1-7
        for (long v = 1; v <= 7; v++) {
            ctx.saveVersionAsMostRecent(v);
        }

        assertEquals(7, ctx.getVersionTableSize());
        // Most recent (7) should be at index 0
        assertEquals(7L, ctx.getVersionAt(0));
        // Oldest (1) should be at index 6
        assertEquals(1L, ctx.getVersionAt(6));

        // Adding an 8th should evict the oldest (1)
        ctx.saveVersionAsMostRecent(8L);
        assertEquals(7, ctx.getVersionTableSize());
        assertEquals(8L, ctx.getVersionAt(0));
        assertEquals(-1, ctx.getVersionIndex(1L)); // evicted
    }

    @Test
    public void contextMarkMostRecent() {
        CompressedHeaderContext ctx = new CompressedHeaderContext();

        ctx.saveVersionAsMostRecent(1L);
        ctx.saveVersionAsMostRecent(2L);
        ctx.saveVersionAsMostRecent(3L);
        // Order: [3, 2, 1]

        assertEquals(0, ctx.getVersionIndex(3L));
        assertEquals(1, ctx.getVersionIndex(2L));
        assertEquals(2, ctx.getVersionIndex(1L));

        // Mark version at index 2 (which is version 1) as most recent
        ctx.markVersionAsMostRecent(2);
        // Order should be: [1, 3, 2]
        assertEquals(0, ctx.getVersionIndex(1L));
        assertEquals(1, ctx.getVersionIndex(3L));
        assertEquals(2, ctx.getVersionIndex(2L));
    }

    @Test
    public void contextUpdateAfterHeader() {
        CompressedHeaderContext ctx = new CompressedHeaderContext();

        assertEquals(Sha256Hash.ZERO_HASH, ctx.getPreviousBlockHash());
        assertEquals(0L, ctx.getPreviousTimestamp());
        assertEquals(0L, ctx.getPreviousNBits());

        Block block = createBlock(1L, randomHash(), randomHash(),
                1732730000L, 503378505L, 42L);
        ctx.updateAfterHeader(block);

        assertEquals(block.getHash(), ctx.getPreviousBlockHash());
        assertEquals(1732730000L, ctx.getPreviousTimestamp());
        assertEquals(503378505L, ctx.getPreviousNBits());
    }

    @Test
    public void contextReset() {
        CompressedHeaderContext ctx = new CompressedHeaderContext();
        ctx.saveVersionAsMostRecent(1L);
        Block block = createBlock(1L, randomHash(), randomHash(), 1000L, 100L, 1L);
        ctx.updateAfterHeader(block);

        ctx.reset();
        assertEquals(0, ctx.getVersionTableSize());
        assertEquals(Sha256Hash.ZERO_HASH, ctx.getPreviousBlockHash());
        assertEquals(0L, ctx.getPreviousTimestamp());
        assertEquals(0L, ctx.getPreviousNBits());
    }

    // --- CompressedBlockHeader tests ---

    @Test
    public void firstHeaderIncludesAllFields() throws Exception {
        Block block = createBlock(536872960L, randomHash(), randomHash(),
                1732730000L, 503378505L, 42L);

        CompressedHeaderContext ctx = new CompressedHeaderContext();
        CompressedBlockHeader compressed = new CompressedBlockHeader(
                UNITTEST, block, ctx, true);

        // First header should have prevBlockHash, full timestamp, and nBits bits set
        int bitfield = compressed.getBitfield();
        assertTrue("First header should include prevBlockHash",
                (bitfield & CompressedBlockHeader.PREV_BLOCK_HASH_BIT) != 0);
        assertTrue("First header should include full timestamp",
                (bitfield & CompressedBlockHeader.TIMESTAMP_FULL_BIT) != 0);
        assertTrue("First header should include nBits",
                (bitfield & CompressedBlockHeader.NBITS_NEW_BIT) != 0);
        // versionBits should be 0 (version in stream)
        assertEquals("First header version bits should be 0",
                0, bitfield & CompressedBlockHeader.VERSION_BIT_MASK);
    }

    @Test
    public void subsequentHeaderCompression() throws Exception {
        Block first = createBlock(536872960L, randomHash(), randomHash(),
                1732730000L, 503378505L, 42L);

        CompressedHeaderContext ctx = new CompressedHeaderContext();
        // Process first header
        CompressedBlockHeader comp1 = new CompressedBlockHeader(UNITTEST, first, ctx, true);
        ctx.updateAfterHeader(first);

        // Create second header: same version, same difficulty, connected chain, small time offset
        Block second = createBlock(536872960L, first.getHash(), randomHash(),
                1732730150L, 503378505L, 100L);

        CompressedBlockHeader comp2 = new CompressedBlockHeader(UNITTEST, second, ctx, false);
        int bitfield = comp2.getBitfield();

        // Version should be compressed (in table)
        assertTrue("Version should be compressed",
                (bitfield & CompressedBlockHeader.VERSION_BIT_MASK) != 0);
        // prevBlockHash should be omitted (derivable from chain)
        assertFalse("prevBlockHash should be omitted",
                (bitfield & CompressedBlockHeader.PREV_BLOCK_HASH_BIT) != 0);
        // Timestamp should use 2-byte offset
        assertFalse("Timestamp should use offset",
                (bitfield & CompressedBlockHeader.TIMESTAMP_FULL_BIT) != 0);
        // nBits should be omitted (same as previous)
        assertFalse("nBits should be omitted",
                (bitfield & CompressedBlockHeader.NBITS_NEW_BIT) != 0);
    }

    @Test
    public void compressedHeaderRoundTrip() throws Exception {
        Block block = createBlock(536872960L, randomHash(), randomHash(),
                1732730000L, 503378505L, 42L);

        CompressedHeaderContext encodeCtx = new CompressedHeaderContext();
        CompressedBlockHeader compressed = new CompressedBlockHeader(
                UNITTEST, block, encodeCtx, true);

        byte[] serialized = compressed.bitcoinSerialize();

        CompressedHeaderContext decodeCtx = new CompressedHeaderContext();
        CompressedBlockHeader parsed = new CompressedBlockHeader(
                UNITTEST, serialized, 0, decodeCtx, true);

        Block restored = parsed.toBlock();
        assertEquals(block.getVersion(), restored.getVersion());
        assertEquals(block.getPrevBlockHash(), restored.getPrevBlockHash());
        assertEquals(block.getMerkleRoot(), restored.getMerkleRoot());
        assertEquals(block.getTimeSeconds(), restored.getTimeSeconds());
        assertEquals(block.getDifficultyTarget(), restored.getDifficultyTarget());
        assertEquals(block.getNonce(), restored.getNonce());
    }

    // --- GetHeaders2Message tests ---

    @Test
    public void getHeaders2MessageRoundTrip() throws Exception {
        BlockLocator locator = new BlockLocator()
                .add(Sha256Hash.ZERO_HASH)
                .add(randomHash());

        Sha256Hash stopHash = randomHash();
        GetHeaders2Message original = new GetHeaders2Message(UNITTEST, locator, stopHash);
        byte[] serialized = original.bitcoinSerialize();

        GetHeaders2Message deserialized = new GetHeaders2Message(UNITTEST, serialized);
        assertEquals(original.getLocator(), deserialized.getLocator());
        assertEquals(original.getStopHash(), deserialized.getStopHash());
    }

    @Test
    public void getHeaders2MessageToString() {
        BlockLocator locator = new BlockLocator().add(Sha256Hash.ZERO_HASH);
        GetHeaders2Message msg = new GetHeaders2Message(UNITTEST, locator, Sha256Hash.ZERO_HASH);
        assertTrue(msg.toString().startsWith("getheaders2:"));
    }

    @Test
    public void getHeaders2NotEqualToGetHeaders() {
        BlockLocator locator = new BlockLocator().add(Sha256Hash.ZERO_HASH);
        Sha256Hash stop = Sha256Hash.ZERO_HASH;

        GetHeadersMessage headers1 = new GetHeadersMessage(UNITTEST, locator, stop);
        GetHeaders2Message headers2 = new GetHeaders2Message(UNITTEST, locator, stop);

        assertNotEquals(headers1, headers2);
        assertNotEquals(headers2, headers1);
    }

    // --- SendHeaders2Message tests ---

    @Test
    public void sendHeaders2MessageCreation() {
        SendHeaders2Message msg = new SendHeaders2Message();
        assertNotNull(msg);
    }

    @Test
    public void sendHeaders2MessageDeserialization() {
        SendHeaders2Message msg = new SendHeaders2Message(UNITTEST, new byte[0]);
        assertNotNull(msg);
    }

    // --- Serializer integration tests ---

    @Test
    public void serializerDeserializesHeaders2() throws Exception {
        // Build a Headers2Message, serialize with the full protocol serializer, and deserialize
        List<Block> blocks = new ArrayList<>();
        Sha256Hash prevHash = randomHash();
        long baseTime = 1732730000L;

        for (int i = 0; i < 5; i++) {
            Block block = createBlock(536872960L, prevHash, randomHash(),
                    baseTime + (i * 150), 503378505L, i * 1000L);
            blocks.add(block);
            prevHash = block.getHash();
        }

        Headers2Message original = new Headers2Message(MAINNET, blocks);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MessageSerializer serializer = MAINNET.getDefaultSerializer();
        serializer.serialize(original, bos);
        byte[] fullMessage = bos.toByteArray();

        Message deserialized = serializer.deserialize(ByteBuffer.wrap(fullMessage));
        assertTrue("Expected Headers2Message but got " + deserialized.getClass().getSimpleName(),
                deserialized instanceof Headers2Message);

        Headers2Message result = (Headers2Message) deserialized;
        assertEquals(5, result.getBlockHeaders().size());
    }

    @Test
    public void serializerDeserializesSendHeaders2() throws Exception {
        SendHeaders2Message original = new SendHeaders2Message();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MessageSerializer serializer = MAINNET.getDefaultSerializer();
        serializer.serialize(original, bos);
        byte[] fullMessage = bos.toByteArray();

        Message deserialized = serializer.deserialize(ByteBuffer.wrap(fullMessage));
        assertTrue("Expected SendHeaders2Message but got " + deserialized.getClass().getSimpleName(),
                deserialized instanceof SendHeaders2Message);
    }

    @Test
    public void maxHeadersConstant() {
        assertEquals(8000, Headers2Message.MAX_HEADERS);
        // Headers2 allows more headers than Headers (2000)
        assertTrue(Headers2Message.MAX_HEADERS > HeadersMessage.MAX_HEADERS);
    }
}