/*
 * Copyright 2011 Noa Resare
 * Copyright 2014 Andreas Schildbach
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
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.*;

public class BitcoinSerializerTest {
    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final byte[] ADDRESS_MESSAGE_BYTES = HEX.decode("bf0c6bbd6164647200000000000000001f000000" +
            "ed52399b01e215104d010000000000000000000000000000000000ffff0a000001208d");

    private static final byte[] TRANSACTION_MESSAGE_BYTES = HEX.withSeparator(" ", 2).decode(
            "bf 0c 6b bd 74 78 00 00  00 00 00 00 00 00 00 00" +
            "02 01 00 00 e2 93 cd be  01 00 00 00 01 6d bd db" +
            "08 5b 1d 8a f7 51 84 f0  bc 01 fa d5 8d 12 66 e9" +
            "b6 3b 50 88 19 90 e4 b4  0d 6a ee 36 29 00 00 00" +
            "00 8b 48 30 45 02 21 00  f3 58 1e 19 72 ae 8a c7" +
            "c7 36 7a 7a 25 3b c1 13  52 23 ad b9 a4 68 bb 3a" +
            "59 23 3f 45 bc 57 83 80  02 20 59 af 01 ca 17 d0" +
            "0e 41 83 7a 1d 58 e9 7a  a3 1b ae 58 4e de c2 8d" +
            "35 bd 96 92 36 90 91 3b  ae 9a 01 41 04 9c 02 bf" +
            "c9 7e f2 36 ce 6d 8f e5  d9 40 13 c7 21 e9 15 98" +
            "2a cd 2b 12 b6 5d 9b 7d  59 e2 0a 84 20 05 f8 fc" +
            "4e 02 53 2e 87 3d 37 b9  6f 09 d6 d4 51 1a da 8f" +
            "14 04 2f 46 61 4a 4c 70  c0 f1 4b ef f5 ff ff ff" +
            "ff 02 40 4b 4c 00 00 00  00 00 19 76 a9 14 1a a0" +
            "cd 1c be a6 e7 45 8a 7a  ba d5 12 a9 d9 ea 1a fb" +
            "22 5e 88 ac 80 fa e9 c7  00 00 00 00 19 76 a9 14" +
            "0e ab 5b ea 43 6a 04 84  cf ab 12 48 5e fd a0 b7" +
            "8b 4e cc 52 88 ac 00 00  00 00");
    private static final byte[] TRANSACTION_MESSAGE_BYTES1 = HEX.decode(
            "bf0c6bbd"+ //magic
                    "747800000000000000000000" +//tx
                    "e1000000"+ //size 225
                    "" + //checksum
                    "0100000001926cdbfc59ae9276e09658759baf1792fa91636511fd639aaced4db2157c081d000000006a473044022063e34eb24f1af68166aa5fe3cbfd86e50cea0ecdd2c08a2a72d52f15ff97f0bc02200fc944e5bad0dda595c2a63f07c7ed3423bc54b2358af6b16c7373135f2678be012102bc42016328bfa02822955ff7490fc963318350779537875e82ce155338f9656fffffffff02c0047700000000001976a91420804bbc0868bda507f436e4aeec0c813ff26de188acc08c6c05000000001976a91465d5ed3c9ed684c3fe6e0bc48a164557a6892cd488ac00000000");

    @Test
    public void testAddr() throws Exception {
        MessageSerializer serializer = MAINNET.getDefaultSerializer();
        // the actual data from https://en.bitcoin.it/wiki/Protocol_specification#addr
        AddressMessage addressMessage = (AddressMessage) serializer.deserialize(ByteBuffer.wrap(ADDRESS_MESSAGE_BYTES));
        assertEquals(1, addressMessage.getAddresses().size());
        PeerAddress peerAddress = addressMessage.getAddresses().get(0);
        assertEquals(8333, peerAddress.getPort());
        assertEquals("10.0.0.1", peerAddress.getAddr().getHostAddress());
        ByteArrayOutputStream bos = new ByteArrayOutputStream(ADDRESS_MESSAGE_BYTES.length);
        serializer.serialize(addressMessage, bos);

        assertEquals(31, addressMessage.getMessageSize());
        addressMessage.addAddress(new PeerAddress(MAINNET, InetAddress.getLocalHost()));
        assertEquals(61, addressMessage.getMessageSize());
        addressMessage.removeAddress(0);
        assertEquals(31, addressMessage.getMessageSize());

        //this wont be true due to dynamic timestamps.
        //assertTrue(LazyParseByteCacheTest.arrayContains(bos.toByteArray(), addrMessage));
    }

    @Test
    public void testCachedParsing() throws Exception {
        MessageSerializer serializer = MAINNET.getSerializer(true);
        
        // first try writing to a fields to ensure uncaching and children are not affected
        Transaction transaction = (Transaction) serializer.deserialize(ByteBuffer.wrap(TRANSACTION_MESSAGE_BYTES));
        assertNotNull(transaction);
        assertTrue(transaction.isCached());

        transaction.setLockTime(1);
        // parent should have been uncached
        assertFalse(transaction.isCached());
        // child should remain cached.
        assertTrue(transaction.getInputs().get(0).isCached());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        serializer.serialize(transaction, bos);
        assertFalse(Arrays.equals(TRANSACTION_MESSAGE_BYTES, bos.toByteArray()));

        // now try writing to a child to ensure uncaching is propagated up to parent but not to siblings
        transaction = (Transaction) serializer.deserialize(ByteBuffer.wrap(TRANSACTION_MESSAGE_BYTES));
        assertNotNull(transaction);
        assertTrue(transaction.isCached());

        transaction.getInputs().get(0).setSequenceNumber(1);
        // parent should have been uncached
        assertFalse(transaction.isCached());
        // so should child
        assertFalse(transaction.getInputs().get(0).isCached());

        bos = new ByteArrayOutputStream();
        serializer.serialize(transaction, bos);
        assertFalse(Arrays.equals(TRANSACTION_MESSAGE_BYTES, bos.toByteArray()));

        // deserialize/reserialize to check for equals.
        transaction = (Transaction) serializer.deserialize(ByteBuffer.wrap(TRANSACTION_MESSAGE_BYTES));
        assertNotNull(transaction);
        assertTrue(transaction.isCached());
        bos = new ByteArrayOutputStream();
        serializer.serialize(transaction, bos);
        assertArrayEquals(TRANSACTION_MESSAGE_BYTES, bos.toByteArray());

        // deserialize/reserialize to check for equals.  Set a field to it's existing value to trigger uncache
        transaction = (Transaction) serializer.deserialize(ByteBuffer.wrap(TRANSACTION_MESSAGE_BYTES));
        assertNotNull(transaction);
        assertTrue(transaction.isCached());

        transaction.getInputs().get(0).setSequenceNumber(transaction.getInputs().get(0).getSequenceNumber());

        bos = new ByteArrayOutputStream();
        serializer.serialize(transaction, bos);
        assertArrayEquals(TRANSACTION_MESSAGE_BYTES, bos.toByteArray());
    }

    /**
     * Get 1 header of the block number 1 (the first one is 0) in the chain
     */
    @Test
    public void testHeaders1() throws Exception {
        MessageSerializer serializer = MAINNET.getDefaultSerializer();

        byte[] headersMessageBytes1 = HEX.decode("bf0c6bbd686561" +
                "646572730000000000520000005d4fab8101010000006fe28c0ab6f1b372c1a6a246ae6" +
                "3f74f931e8365e15a089c68d6190000000000982051fd1e4ba744bbbe680e1fee14677b" +
                "a1a3c3540bf7b1cdb606e857233e0e61bc6649ffff001d01e3629900");
        byte[] headersMessageBytes = HEX.decode("bf0c6bbd686561" +
                "64657273000000000052000000aaf4d8a1"+
                "0102000000b67a40f3cd5804437a108f105533739c37e6229bc1adcab385140b59fd0f0000a71c1aade44bf8425bec0deb611c20b16da3442818ef20489ca1e2512be43eef814cdb52f0ff0f1edbf7010000");
        HeadersMessage headersMessage = (HeadersMessage) serializer.deserialize(ByteBuffer.wrap(headersMessageBytes));

        // The first block after the genesis
        // http://blockexplorer.com/b/1
        Block block = headersMessage.getBlockHeaders().get(0);
        assertEquals("000007d91d1254d60e2dd1ae580383070a4ddffa4c64c2eeb4a2f9ecc0414343", block.getHashAsString());
        assertNotNull(block.transactions);
        assertEquals("ef3ee42b51e2a19c4820ef182844a36db1201c61eb0dec5b42f84be4ad1a1ca7", Utils.HEX.encode(block.getMerkleRoot().getBytes()));

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        serializer.serialize(headersMessage, byteArrayOutputStream);
        byte[] serializedBytes = byteArrayOutputStream.toByteArray();
        assertArrayEquals(headersMessageBytes, serializedBytes);
    }

    /**
     * Get 6 headers of blocks 1-6 in the chain
     */
    @Test
    public void testHeaders2() throws Exception {
        MessageSerializer serializer = MAINNET.getDefaultSerializer();

        byte[] headersMessageBytes1 = HEX.decode("f9beb4d96865616465" +
                "72730000000000e701000085acd4ea06010000006fe28c0ab6f1b372c1a6a246ae63f74f931e" +
                "8365e15a089c68d6190000000000982051fd1e4ba744bbbe680e1fee14677ba1a3c3540bf7b1c" +
                "db606e857233e0e61bc6649ffff001d01e3629900010000004860eb18bf1b1620e37e9490fc8a" +
                "427514416fd75159ab86688e9a8300000000d5fdcc541e25de1c7a5addedf24858b8bb665c9f36" +
                "ef744ee42c316022c90f9bb0bc6649ffff001d08d2bd610001000000bddd99ccfda39da1b108ce1" +
                "a5d70038d0a967bacb68b6b63065f626a0000000044f672226090d85db9a9f2fbfe5f0f9609b387" +
                "af7be5b7fbb7a1767c831c9e995dbe6649ffff001d05e0ed6d00010000004944469562ae1c2c74" +
                "d9a535e00b6f3e40ffbad4f2fda3895501b582000000007a06ea98cd40ba2e3288262b28638cec" +
                "5337c1456aaf5eedc8e9e5a20f062bdf8cc16649ffff001d2bfee0a9000100000085144a84488e" +
                "a88d221c8bd6c059da090e88f8a2c99690ee55dbba4e00000000e11c48fecdd9e72510ca84f023" +
                "370c9a38bf91ac5cae88019bee94d24528526344c36649ffff001d1d03e4770001000000fc33f5" +
                "96f822a0a1951ffdbf2a897b095636ad871707bf5d3162729b00000000379dfb96a5ea8c81700ea4" +
                "ac6b97ae9a9312b2d4301a29580e924ee6761a2520adc46649ffff001d189c4c9700");
        byte[] headersMessageBytes = HEX.decode("bf0c6bbd6865616465" +
                "72730000000000e7010000d8ee9a83"+
                "06"+
                "02000000b67a40f3cd5804437a108f105533739c37e6229bc1adcab385140b59fd0f0000a71c1aade44bf8425bec0deb611c20b16da3442818ef20489ca1e2512be43eef814cdb52f0ff0f1edbf7010000"+
                "02000000434341c0ecf9a2b4eec2644cfadf4d0a07830358aed12d0ed654121dd90700004bdcd337231c40e16ad4d46356dd3369d69acb46f9647106a52c03b0ce973a3b864cdb52f0ff0f1ef7d2010000"+
                "02000000aefe1eef743a873769ccf70ee174b541ef4775886f435c7cce1e57ccaf0b0000386851f9d572dce5b95554329a2f6706b0d4d3dcaaf291735479c8151d3329ec954cdb52f0ff0f1e8f07060000"+
                "02000000ffe08d0dce84f320f81eed925141b231909b2ca0e1d2c67bd956557069020000ae075ab56576f7f01000970ebd7c4b9e21bd90bd51ba62471552ca7db956026e9e4cdb52f0ff0f1e3c27030000"+
                "02000000e4679be8b765214df4923942bd37dccc2c629210a1d96e9925be6ce4fc06000025a0c01db1cfa0db1847bce702e92e0905d814f3a8fb6f04e0060d75765efd17a14cdb52f0ff0f1e2120010000"+
                "02000000a210ba37364085b6482d437071506c0b5e722604ccacfa687f2f15567f090000c125f400644f069983dac9a19d85a6cf65026ce6ca036cb4eadccf567fbedf95aa4cdb52f0ff0f1e079f030000");

        HeadersMessage headersMessage = (HeadersMessage) serializer.deserialize(ByteBuffer.wrap(headersMessageBytes));

        assertEquals(6, headersMessage.getBlockHeaders().size());

        // index 0 block is the number 1 block in the block chain
        // http://blockexplorer.com/b/1
        Block zeroBlock = headersMessage.getBlockHeaders().get(0);
        assertEquals("000007d91d1254d60e2dd1ae580383070a4ddffa4c64c2eeb4a2f9ecc0414343",
                zeroBlock.getHashAsString());
        assertEquals(128987, zeroBlock.getNonce());

        // index 3 block is the number 4 block in the block chain
        // http://blockexplorer.com/b/4
        Block thirdBlock = headersMessage.getBlockHeaders().get(3);
        assertEquals("000006fce46cbe25996ed9a11092622cccdc37bd423992f44d2165b7e89b67e4",
                thirdBlock.getHashAsString());
        assertEquals(206652, thirdBlock.getNonce());

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        serializer.serialize(headersMessage, byteArrayOutputStream);
        byte[] serializedBytes = byteArrayOutputStream.toByteArray();
        assertArrayEquals(headersMessageBytes, serializedBytes);
    }

    @Test(expected = BufferUnderflowException.class)
    public void testBitcoinPacketHeaderTooShort() {
        new BitcoinSerializer.BitcoinPacketHeader(ByteBuffer.wrap(new byte[] { 0 }));
    }

    @Test(expected = ProtocolException.class)
    public void testBitcoinPacketHeaderTooLong() {
        // Message with a Message size which is 1 too big, in little endian format.
        byte[] wrongMessageLength = HEX.decode("000000000000000000000000010000020000000000");
        new BitcoinSerializer.BitcoinPacketHeader(ByteBuffer.wrap(wrongMessageLength));
    }

    @Test(expected = BufferUnderflowException.class)
    public void testSeekPastMagicBytes() {
        // Fail in another way, there is data in the stream but no magic bytes.
        byte[] brokenMessage = HEX.decode("000000");
        MAINNET.getDefaultSerializer().seekPastMagicBytes(ByteBuffer.wrap(brokenMessage));
    }

    /**
     * Tests serialization of an unknown message.
     */
    @Test(expected = Error.class)
    public void testSerializeUnknownMessage() throws Exception {
        MessageSerializer serializer = MAINNET.getDefaultSerializer();

        Message unknownMessage = new Message() {
            @Override
            protected void parse() throws ProtocolException {
            }
        };
        ByteArrayOutputStream bos = new ByteArrayOutputStream(ADDRESS_MESSAGE_BYTES.length);
        serializer.serialize(unknownMessage, bos);
    }

    @Test
    public void protocolVersionSerializer() {
        MessageSerializer serializer = MAINNET.getSerializer(false, 70229);
        assertEquals(70229, serializer.getProtocolVersion());
        assertNotEquals(NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion(), serializer.getProtocolVersion());
    }
}
