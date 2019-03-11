/*
 * Copyright 2011 Google Inc.
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

import com.google.common.io.ByteStreams;

import org.bitcoinj.core.AbstractBlockChain.NewBlockType;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.*;

public class BlockTest {
    private static final NetworkParameters TESTNET = TestNet3Params.get();
    private static final NetworkParameters UNITTEST = UnitTestParams.get();
    private static final NetworkParameters MAINNET = MainNetParams.get();

    private byte[] blockBytes;
    private Block block;

    @Before
    public void setUp() throws Exception {
        new Context(TESTNET);
        blockBytes = HEX.decode("00000020157dc13d40818f91eddb8d14dd2e8dcb43f2c4dd2935d4065238788d00000000ec269fd49d1d0c5613f918a686f71ddb3a8f92c7b85b385f3dfed4f0c5cd0a392b92f9584ee1001d059cf2910301000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4c0327de02042b92f95808fabe6d6dec5faa57bc58a0f6aeb6555f0900000092db0a8600000000ac88bd54c554c6e701000000000000006ffffff3710100000d2f6e6f64655374726174756d2f00000000022c460e43000000001976a914cf5783cdb4347cf4d6b4026fbdd895e460acbc5088ac2c460e43000000001976a91440312e1aeebc7e2e99047125d78f19f51df6180188ac0000000001000000032ec44449143edfba5bc741a899b22c9ea1797750195000cfc9a8317aca5b858a000000006b483045022100f04f5686febb8d9df0af2f8fee92e74048dd79ec6e44acb7c05a16b635c9320502207aed80d5b137463a6c0e5b2af859ce3e5475945ad167072f1216dccbd16ffee00121038ecdf3413d4e7e2797cc1b038f9ba9e42b83f261e64d9115569e46d741b2da24feffffffd9bb73a65adeeab77186fe0d33917a0661a6d6da7e94a64486fae91135adf71e000000006a473044022055ba9a7106dc28e90c72c2d41f937528ea6aacb371f79ca3a18f9305236b2c2d0220558d833028626f16ad93acfcb990ca68575d1874befc85a185ef78a3c11ccd5e0121038ecdf3413d4e7e2797cc1b038f9ba9e42b83f261e64d9115569e46d741b2da24feffffffdca428fda53beb894c01c398b7a92f744b60ae5c0e01ea29f985edf6305d322e000000006a47304402205edcea624377076f2db313346cc5a7f364594912cfd6a695e7beb77834c0ffc2022004d67d23b7c8b825d17c3f5a37cb57cee4fd5ff121c080b674d4d41decb52717012103353cc80209a47dbf0f434385bb293b1f08b92e2dd3917c51483e5c846ad77f71feffffff02c70a8d05000000001976a914c88b91c4e0e738d082f29fd784ff11de29eb244b88acf5741c86000000001976a914dc3e0793134b081145ec0c67a9c72a7b297df27c88ac25de0200010000000229035c9d173a3581cd3e751e30803d8007178debee5c43ab0667f22d3d1d85b5000000006a47304402205208c78c617e2847bde13b7d09c36b14aa048a55972f9c8b49f657ea82d1f5d602202fac89274602079cecd7bbe7cfd577e64084d68734531a56c2db0374f93ce0bb01210342d0a0181a33451fe901dec2a01ef22fc5ad570c5de507cfbf70417777fa3374feffffffdd2b4139ad014967454591a796045b2f02259def4d3c5a26c2c32e4f3d786f68000000006a47304402205f49906b990f0a35c5349de8453ebc7c70f3a59259cc524e1508c97a2eba09f00220745e5c0eb38421b9b7ac4e9c181bc3bb0d96b9c6eab26af9ea7ba5852664391e0121038ecdf3413d4e7e2797cc1b038f9ba9e42b83f261e64d9115569e46d741b2da24feffffff024b4a0f00000000001976a914f32788579f94cab7974f8119e7383c3d14357c5388ac40230e43000000001976a914dc3e0793134b081145ec0c67a9c72a7b297df27c88ac26de0200");
        block = TESTNET.getDefaultSerializer().makeBlock(blockBytes);

    }

    @Test
    public void testWork() throws Exception {
        BigInteger work = TESTNET.getGenesisBlock().getWork();
        // This number is printed by Dash Core at startup as the calculated value of chainWork on testnet:
        // 2017-04-22 19:20:20 UpdateTip: new best=00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c  height=0  log2_work=20.000022  tx=1  date=2014-01-25 16:10:06 progress=0.000002
        // log base 2 of 1048592 is 20.000022
        assertEquals(BigInteger.valueOf(1048592), work);
    }

    @Test
    public void testBlockVerification() throws Exception {
        Block block = TESTNET.getDefaultSerializer().makeBlock(blockBytes);
        block.verify(Block.BLOCK_HEIGHT_GENESIS, EnumSet.noneOf(Block.VerifyFlag.class));
        assertEquals("000000000fba0622132b6acd887021db720c541590d0408bc3ae525277fb2636", block.getHashAsString());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testDate() throws Exception {
        Block block = TESTNET.getDefaultSerializer().makeBlock(blockBytes);
        assertEquals("21 Apr 2017 05:01:31 GMT", block.getTime().toGMTString());
    }

    @Test
    public void testProofOfWork() throws Exception {
        // This params accepts any difficulty target.
        Block block = UNITTEST.getDefaultSerializer().makeBlock(blockBytes);
        block.setNonce(55584);
        try {
            block.verify(Block.BLOCK_HEIGHT_GENESIS, EnumSet.noneOf(Block.VerifyFlag.class));
            fail();
        } catch (VerificationException e) {
            // Expected.
        }
        // Blocks contain their own difficulty target. The BlockChain verification mechanism is what stops real blocks
        // from containing artificially weak difficulties.
        block.setDifficultyTarget(Block.EASIEST_DIFFICULTY_TARGET);
        // Now it should pass.
        block.verify(Block.BLOCK_HEIGHT_GENESIS, EnumSet.noneOf(Block.VerifyFlag.class));
        // Break the nonce again at the lower difficulty level so we can try solving for it.
        block.setNonce(1);
        try {
            block.verify(Block.BLOCK_HEIGHT_GENESIS, EnumSet.noneOf(Block.VerifyFlag.class));
            fail();
        } catch (VerificationException e) {
            // Expected to fail as the nonce is no longer correct.
        }
        // Should find an acceptable nonce.
        block.solve();
        block.verify(Block.BLOCK_HEIGHT_GENESIS, EnumSet.noneOf(Block.VerifyFlag.class));
        assertEquals(block.getNonce(), 2);
    }

    @Test
    public void testBadTransactions() throws Exception {
        // Re-arrange so the coinbase transaction is not first.
        Transaction tx1 = block.transactions.get(0);
        Transaction tx2 = block.transactions.get(1);
        block.transactions.set(0, tx2);
        block.transactions.set(1, tx1);
        try {
            block.verify(Block.BLOCK_HEIGHT_GENESIS, EnumSet.noneOf(Block.VerifyFlag.class));
            fail();
        } catch (VerificationException e) {
            // We should get here.
        }
    }

    @Test
    public void testHeaderParse() throws Exception {
        Block header = block.cloneAsHeader();
        Block reparsed = TESTNET.getDefaultSerializer().makeBlock(header.bitcoinSerialize());
        assertEquals(reparsed, header);
    }

    @Test
    public void testBitcoinSerialization() throws Exception {
        // We have to be able to reserialize everything exactly as we found it for hashing to work. This test also
        // proves that transaction serialization works, along with all its subobjects like scripts and in/outpoints.
        //
        // NB: This tests the bitcoin serialization protocol.
        assertArrayEquals(blockBytes, block.bitcoinSerialize());
    }
    
    @Test
    public void testUpdateLength() {
        Block block = UNITTEST.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, new ECKey().getPubKey(), Block.BLOCK_HEIGHT_GENESIS);
        assertEquals(block.bitcoinSerialize().length, block.length);
        final int origBlockLen = block.length;
        Transaction tx = new Transaction(UNITTEST);
        // this is broken until the transaction has > 1 input + output (which is required anyway...)
        //assertTrue(tx.length == tx.bitcoinSerialize().length && tx.length == 8);
        byte[] outputScript = new byte[10];
        Arrays.fill(outputScript, (byte) ScriptOpCodes.OP_FALSE);
        tx.addOutput(new TransactionOutput(UNITTEST, null, Coin.SATOSHI, outputScript));
        tx.addInput(new TransactionInput(UNITTEST, null, new byte[] {(byte) ScriptOpCodes.OP_FALSE},
                new TransactionOutPoint(UNITTEST, 0, Sha256Hash.of(new byte[] { 1 }))));
        int origTxLength = 8 + 2 + 8 + 1 + 10 + 40 + 1 + 1;
        assertEquals(tx.unsafeBitcoinSerialize().length, tx.length);
        assertEquals(origTxLength, tx.length);
        block.addTransaction(tx);
        assertEquals(block.unsafeBitcoinSerialize().length, block.length);
        assertEquals(origBlockLen + tx.length, block.length);
        block.getTransactions().get(1).getInputs().get(0).setScriptBytes(new byte[] {(byte) ScriptOpCodes.OP_FALSE, (byte) ScriptOpCodes.OP_FALSE});
        assertEquals(block.length, origBlockLen + tx.length);
        assertEquals(tx.length, origTxLength + 1);
        block.getTransactions().get(1).getInputs().get(0).clearScriptBytes();
        assertEquals(block.length, block.unsafeBitcoinSerialize().length);
        assertEquals(block.length, origBlockLen + tx.length);
        assertEquals(tx.length, origTxLength - 1);
        block.getTransactions().get(1).addInput(new TransactionInput(UNITTEST, null, new byte[] {(byte) ScriptOpCodes.OP_FALSE},
                new TransactionOutPoint(UNITTEST, 0, Sha256Hash.of(new byte[] { 1 }))));
        assertEquals(block.length, origBlockLen + tx.length);
        assertEquals(tx.length, origTxLength + 41); // - 1 + 40 + 1 + 1
    }

    @Test
    public void testCoinbaseHeightTestnet() throws Exception {
        // Testnet block 21066 (hash 0000000004053156021d8e42459d284220a7f6e087bf78f30179c3703ca4eefa)
        // contains a coinbase transaction whose height is two bytes, which is
        // shorter than we see in most other cases.

        //Block block = TESTNET.getDefaultSerializer().makeBlock(
        //    ByteStreams.toByteArray(getClass().getResourceAsStream("block_testnet21066.dat")));

        // Check block.
        assertEquals("000000000fba0622132b6acd887021db720c541590d0408bc3ae525277fb2636", block.getHashAsString());
        block.verify(187943, EnumSet.of(Block.VerifyFlag.HEIGHT_IN_COINBASE));

        // Testnet block 32768 (hash 000000007590ba495b58338a5806c2b6f10af921a70dbd814e0da3c6957c0c03)
        // contains a coinbase transaction whose height is three bytes, but could
        // fit in two bytes. This test primarily ensures script encoding checks
        // are applied correctly.

        //block = TESTNET.getDefaultSerializer().makeBlock(
        //    ByteStreams.toByteArray(getClass().getResourceAsStream("block_testnet32768.dat")));
        block = TESTNET.getDefaultSerializer().makeBlock(HEX.decode("04000000ad7226a87e8c16a4de493320a6f55535903b13dae0689bc6a76255ad0400000068208963dae191aa15c11dd82adfd7ba7184716b9acc649805904ec9a9d564079a3036578798311d3909b8000101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff050288130101ffffffff0100743ba40b0000002321033de1afba2a2aa9f88f06912a4b51d386af3142e89f3b1a69741cb62f3dd47cdaac00000000"));
        // Check block.
        assertEquals("0000001d3f1f66dd3b276f7fddaae344973e9ddf1179d904b0798bcfc503cd94", block.getHashAsString());
        block.verify(5000, EnumSet.of(Block.VerifyFlag.HEIGHT_IN_COINBASE));
    }

    @Test // This needs to be updated for Dash
    public void testReceiveCoinbaseTransaction() throws Exception {
        // Block 49389 from testnet (hash 0000000000a979f1ed3ab900dfd1aed39b47ab2aee9c011976d3f96eabbd276a)
        // contains coinbase transactions that are mining pool shares.
        // The private key MINERS_KEY is used to check transactions are received by a wallet correctly.

        // The address for this private key is yhmDZGmiwjCPJrTFFiBFZJw31PhvJFJAwq.
        final String MINING_PRIVATE_KEY = "cQoQpMDThg8DEojcYV5u24V6E7hduWsiMHG75PVJ7beRFbYmmJv4";

        final long BLOCK_NONCE = 1964091772L;
        final Coin BALANCE_AFTER_BLOCK = Coin.valueOf(945025609L);
        Block block49389 = TESTNET.getDefaultSerializer().makeBlock(
                ByteStreams.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("block_testnet49389.dat")))
        );

        // Check block.
        block49389.verify(49389, EnumSet.noneOf(Block.VerifyFlag.class));
        assertEquals(BLOCK_NONCE, block49389.getNonce());

        StoredBlock storedBlock = new StoredBlock(block49389, BigInteger.ONE, 49389); // Nonsense work - not used in test.

        // Create a wallet contain the miner's key that receives a spend from a coinbase.
        ECKey miningKey = DumpedPrivateKey.fromBase58(TESTNET, MINING_PRIVATE_KEY).getKey();
        assertNotNull(miningKey);
        Context context = new Context(TESTNET);
        Wallet wallet = new Wallet(context);
        wallet.importKey(miningKey);

        // Initial balance should be zero by construction.
        assertEquals(Coin.ZERO, wallet.getBalance());

        // Give the wallet the first transaction in the block - this is the coinbase tx.
        List<Transaction> transactions = block49389.getTransactions();
        assertNotNull(transactions);
        wallet.receiveFromBlock(transactions.get(0), storedBlock, NewBlockType.BEST_CHAIN, 0);

        // Coinbase transaction should have been received successfully but be unavailable to spend (too young).
        assertEquals(BALANCE_AFTER_BLOCK, wallet.getBalance(BalanceType.ESTIMATED));
        assertEquals(Coin.ZERO, wallet.getBalance(BalanceType.AVAILABLE));
    }

    @Test
    public void isBIPs() throws Exception {
        final Block genesis = MAINNET.getGenesisBlock();
        assertFalse(genesis.isBIP34());
        assertFalse(genesis.isBIP66());
        assertFalse(genesis.isBIP65());

        // 227835/00000000000001aa077d7aa84c532a4d69bdbff519609d1da0835261b7a74eb6: last version 1 block
        final Block block227835 = MAINNET.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block227835.dat")));
        assertFalse(block227835.isBIP34());
        assertFalse(block227835.isBIP66());
        assertFalse(block227835.isBIP65());

        // 227836/00000000000000d0dfd4c9d588d325dce4f32c1b31b7c0064cba7025a9b9adcc: version 2 block
        final Block block227836 = MAINNET.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block227836.dat")));
        assertTrue(block227836.isBIP34());
        assertFalse(block227836.isBIP66());
        assertFalse(block227836.isBIP65());

        // 363703/0000000000000000011b2a4cb91b63886ffe0d2263fd17ac5a9b902a219e0a14: version 3 block
        final Block block363703 = MAINNET.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block363703.dat")));
        assertTrue(block363703.isBIP34());
        assertTrue(block363703.isBIP66());
        assertFalse(block363703.isBIP65());

        // 383616/00000000000000000aab6a2b34e979b09ca185584bd1aecf204f24d150ff55e9: version 4 block
        final Block block383616 = MAINNET.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block383616.dat")));
        assertTrue(block383616.isBIP34());
        assertTrue(block383616.isBIP66());
        assertTrue(block383616.isBIP65());

        // 370661/00000000000000001416a613602d73bbe5c79170fd8f39d509896b829cf9021e: voted for BIP101
        final Block block370661 = MAINNET.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block370661.dat")));
        assertTrue(block370661.isBIP34());
        assertTrue(block370661.isBIP66());
        assertTrue(block370661.isBIP65());
    }

    @Test
    public void parseBlockWithHugeDeclaredTransactionsSize() throws Exception{
        Block block = new Block(UNITTEST, 1, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, 1, 1, 1, new ArrayList<Transaction>()) {
            @Override
            protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
                Utils.uint32ToByteStreamLE(getVersion(), stream);
                stream.write(getPrevBlockHash().getReversedBytes());
                stream.write(getMerkleRoot().getReversedBytes());
                Utils.uint32ToByteStreamLE(getTimeSeconds(), stream);
                Utils.uint32ToByteStreamLE(getDifficultyTarget(), stream);
                Utils.uint32ToByteStreamLE(getNonce(), stream);

                stream.write(new VarInt(Integer.MAX_VALUE).encode());
            }

            @Override
            public byte[] bitcoinSerialize() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    bitcoinSerializeToStream(baos);
                } catch (IOException e) {
                }
                return baos.toByteArray();
            }
        };
        byte[] serializedBlock = block.bitcoinSerialize();
        try {
            UNITTEST.getDefaultSerializer().makeBlock(serializedBlock, serializedBlock.length);
            fail("We expect ProtocolException with the fixed code and OutOfMemoryError with the buggy code, so this is weird");
        } catch (ProtocolException e) {
            //Expected, do nothing
        }
    }

    @Test
    public void testBlocks() {

        String block842285 = "000000200d8610dbc7f0d6537be001132d941eb36906eb3b4cc25cdf0c00000000000000720b4e2fb7622a07a8816a9ce6d117ab14854963ef9bd4457f5baae05aa3ecdd274bb75a7c2633195ac7d8e20c01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff2c032dda0c1b4d696e656420627920416e74506f6f6c20737a302024205ab74b27976a0000000000008d100000ffffffff024585f809000000001976a914db75d18a2635ce7e4aa3bf533ba3e259f8a065d488ac3e85f809000000001976a91472cb9904f21b41ba60626084c966e96bcbfdf00988ac000000000100000002e7fe01e0b7a1679dadc5af9061d81f0df5e5ffb6c239d9bcf8dc3b74060784cb010000006b48304502210098cb8b6ddc642558943e87d56506d7dab037b8d01f6d0bc431fc7164285a916902207bb1d10873772ec8a84a7951e723621cc1a4070398288d656da7d13b76a9810801210350a38f885ce361bf7cb227fe4540ab1d7cdf0af16e409076be9414237a124370ffffffffd39f70d6ebbb25413ba980e0171e74ee51bbe9b1b4368d1e26bea6727ddd920f000000006b483045022100d454056db7e37d74923581d7621d4d58fcc8bf8c1ac58e79ba768cc95d1e0d1c02200faf72dfea4c19a651d4a856128e0b62c9595a18e7baa23c62e90656eb22e38b0121033762a92a6ad32ea76adb9c89d9991b3bddd1fe24b3b763171637604d22b4ed97ffffffff0112229700000000001976a9147cf7c94ca1ba49789a07815896ad13ad7297bd1f88ac0000000001000000023f40ec92dff993bd53f29194c218b115dc8ed44f75537b530550d801ab7ec6d4000000006b483045022100b2126907c6f124d356035dfe358ca0b3d33889454d7f3ecf2fc015aafb37d50d02201ea20fcd3e096d850cc28d661257c76964f4c119273bdac58c80044efc919641012103c51c6f03df11c45b1a6a9aee1bbc77681c8de8a1a03398ac5db36acef14f5a78ffffffff107c4335a9c7200510bfaa942bf39ee5723b55402b0bd98c9a0558d9d299c147000000006a4730440220169c6675a1917beedbfd5c08dac9da23915f4da63144a41e9348e067c35164a802202d5c588b1c9a2ac7301a12a5073d459cbcc413196749936a6f8071eeb838ec1f012103bf40a7a64cd55aa17b06a409e20c5d6a786b2150dff1e946c153ec82c3013796ffffffff015c289700000000001976a9145dcd6bb75b64c0278395ff4bf8ab7e66f152bb5f88ac000000000100000001cd4960cfdc77d6a29f80c3c0d9a0e477515d5f68b24d977c19b5af2eaee4f4a6010000006a473044022015fd5d06825f1be382c36a0b14246aba685ac99ec33cfe0bc78d02b366f187aa02200a853f967cdb6c00c55cb09017ffda678a9ece70d0fc6264fabaa563d6a864fe0121036002e358bfa27b651bc0edf390b62cdd41573f0a096b7965021edeb0528fb8c7ffffffff02d7210000000000001976a914413ba4d7d23bf089acc7970108bc44bfb60937d288ac57600801000000001976a91400da822e881dafc6060ec3dc59ec44dd6161a08e88ac00000000010000000fa6d8772f25ae3252aa4410d3fb3a4bc2d0a7936a924242ad24fc091eaa0d7704090000006b483045022100fe9544c3feec13218b9702ee20e8cb75e8f05541de1d2da4409347cda584411c02202646a3cf1c182d713215b989821084443ab36733a13a8414fe60a48b0efc342381210373bcb12973a3713a7798b642cab1a018912f5efa7c3f4f57ac2223aa0ad2f898ffffffffa6d8772f25ae3252aa4410d3fb3a4bc2d0a7936a924242ad24fc091eaa0d77040a0000006b483045022100ce78a9a05f9928d98a1dc50be2081dc803b45e8492c90dbb22cc9346f4323002022018b26e410cbb38cc67af31419815a6b765d03554f0374541b59ef9f6e28fa76b81210296997528d0e28fc93e8603d7468cdd5b7b92b202d7079a5ec73c943d522c920cffffffffa6d8772f25ae3252aa4410d3fb3a4bc2d0a7936a924242ad24fc091eaa0d77040b0000006b483045022100c00da8b5162286d06c26343bae221e73e060c3ec3a9f421fcc2302fc226c58d90220021a119bfcde01e78a1e90d5fe82deb58eb4ef1964b2dde2cf59002b1bc1ea1381210245193a9467188b5d6e34875e06ec56abe59304e054c5f823828c2e89a3793588ffffffffa6d8772f25ae3252aa4410d3fb3a4bc2d0a7936a924242ad24fc091eaa0d77040e0000006a47304402205df8482311f8663dea9e4839d68c2bfe8d67ad00cd088996dcb7d459f82440eb02204e66831d2d478c0d40d28ca54d8317fdbba920915e18bc027a6a828a8002ad9b81210385e6b6f43c2c7ce9e634e736b35d535eff8d973f16862e90ca3ab547b54b8c08ffffffffa6d8772f25ae3252aa4410d3fb3a4bc2d0a7936a924242ad24fc091eaa0d77040f0000006a47304402201ab260bf8a813054250be529df207df83a99ff51fc15d8311eb9f0a43b32ea8902202e14683a3c5410d2f5766911d50bb674fc8d4104c7308312ceb376456d1b18588121020f04b43b8d37d761e52accddf582102c79258ac6aaee260cc68de9750999606cffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a000000006a47304402200895a2adc83675a20468f30f962d2806576819b6b95df793b24ad4f30c8f36ba0220378ed36864ea24ee43413a281e4d3505fea06716bb06e5d29a04428b4fa76aa18121021dbd5b55104c1abcc30d2a795d0cff14aea3d36fb53f0edcb2c644f6f07f848effffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a030000006a47304402204ef7d7fff53a5eb7dd489d3e5526aad88cf25d087b2422e6264e04b4879da71002203531c2f2b5ba491b4cdd76f1906894fb38912f00de2cab3ef3364e0bedafa71f81210343dee6e5eb8af6c7a9af7c5ec3143be215a98c448923132e9347954743cb5f47ffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a040000006b483045022100ba2781c4cedb1b137fe5ba055ece40ef2f68fef4b3b850985d73d671bb554c8402204b74fdf07b5ef40c8f48824cfcc34c7175ddee88de44c677756ad5b0c0c979f181210209e5821b4426832298f343c17a9766f8b2c84045e02c00becb1c9ac65d9c7e7bffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a050000006b483045022100b72e75b50fb08947c12a42b3279d9524d134dc65d3fafceb2d8e94caf03c3e9e02201d294b2908ad768106bfcc848ed974848a36370b9239317b41230aa5dbf14200812103c741e31becdab65f0e5da3a3d7a1225c00784d2bbc78664e9bdfeacd10b5330dffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a060000006a47304402202e2aeecf8aeae021acb0138de14b443c137ac1e56f19e70a92f5959e62430c5e02207fff05f6cac7c781de454d983f0d9dfeb31b53b0a0c516bb2b4ed716f013814a812102a960c95995e027a5e666fcc0ce99dce62090df2d9f388ea91e6bdec0bb4a7e30ffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a070000006a47304402201dfd7fda81aef683e1fd84e7c4744bbb8a5574da9aebf427500b2919eb9a06c902204cae1a00c1a337ca1e3d3be5287cb12d41cc8e50ecb9404755a966a1dc10ebc08121023c5b4ee9d57559cad5ad0f283653610fda0a0c2014ff8a8e1f698c14b298b3d6ffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a090000006b483045022100b9e5cd6a7bdb47b7dd4e6d64aad329f0aa761530d5f61fd9fac88bf02b40038002200bb1242fb04b3bb0d18dd28b587bb061293c09c3e92ddc88e08bf3208048e4e8812103e646e9cc8f9761c2bc44a25e2b5431fca1be7d0b4b7215d1b8aa9d313ebfd635ffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a0a0000006a473044022016ad08cdc0f395ddda258538c93d4571e70fbbf028d7481286b0b720ee4eacb302205d2b406bde24ddc8a5c0b84409be12424be105a7f6f83975fea9348bb0a8498b812103a39ea1e0896f726ff843aee9ceed1a12324eae772884e71670b66f7d943ea50fffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a0b0000006b483045022100b5c5b0d986d0cfc43570a1df4b5822da2c707e8932732529f4664270a90e0dbe02205851cedbedd1ec32e8089d6fb0244b2adfafe41bd0ffdfbb01bd62cd35d34fdd812103e094fd8164316fe65c7340803f56f2f0d5517acd528d037e7d982f7613124901ffffffffce816489b53854132093a1d0fa58f4f8b1713538c4a6a3e8f0e5657db18ac64a0d0000006b483045022100eb0fd64427ee48e264c93ad211f78a3d2cc97c4525d66d233fbaae3f16a6d5480220711fe49b17f9492bf38864262990f56d89e1209a2079498bbb61cd937c120d7e8121031f143268f37502b12ecc1e67b3badb8c0ffb5231f7b669f8d001ab0bf694b067ffffffff0f10f19a3b000000001976a9140aed5fab159af653b96b27b5700c1cbcbe08838688ac10f19a3b000000001976a914200f333ae6dc140fd7ba40807ba1c629dcc59a1688ac10f19a3b000000001976a9142258651f6ef6f38bbf0504d826a785ab018a50b788ac10f19a3b000000001976a914258d7b1388075577c2561870890a011563fa672c88ac10f19a3b000000001976a91430b31dc302439575c84e608dd0c213ab25b4984788ac10f19a3b000000001976a9143b4209f159d47157b6bb736a2a881951af926c5188ac10f19a3b000000001976a9144812b62c00182dc92e6ad5c3e727313907fc613b88ac10f19a3b000000001976a9146118f73a02adf52232f6c03b73673c603de1b76a88ac10f19a3b000000001976a9147fa4a1140ebf0536a8281b5caba713814828dd2f88ac10f19a3b000000001976a914a1ccca8467e6dedf1e8ab1078c50d8bd96062eb488ac10f19a3b000000001976a914b6a10d305fe2d09df952c0ddd172edd63fd5749688ac10f19a3b000000001976a914bc1e4afeb5c762cc938a4cfdb3d5eee41d70deda88ac10f19a3b000000001976a914deccffc1b999118e874695fa5eb6ed1208c5ec3888ac10f19a3b000000001976a914f1733485080a86c021dc5e45c0b86fdce17f11b388ac10f19a3b000000001976a914f8c04f5419b9943d3d1e403437b68c99667df5c388ac000000000100000001c24d6f31d6e23cb7f32fcf5d3b84c3de1c759332d58ad40c6811b0bb614e5e45010000006a473044022007679b4fa85e308fa4fa1012757231d855899658544e72f8139f0cf5c9d44fcb0220751fac09530c4a395758b34e422fb9ddfe0cdfa4534d04ae2beb855ccd47a962012103a0022744f7bb957ce012868b314ab9926df2c7460c68a3870ac5ffb25a0e0e36ffffffff0183bd520a000000001976a914e8e57b31b418dbb7a0c082b11d3f7fd5fcf273a788ac000000000100000001cc5e062347d868ae0eb848cdf4a4fe1d83a0b08b3572dc67b423566937a8c9a8000000006a47304402204f2c0ef83d4f91176d00b5f241700e656a5bb38a288fc24f8fcc66db801fd33e02200e31aa357eeec6527b31a458db5ad3a7bde41e81b24c71cc0a293f8f5d2867b10121028b13cf1966978ddaa91cae99f3b404091b160a60aa850500a2b1e86c49df07bafeffffff0a01bd0d00000000001976a914b9988221955e2c4e7195c68e06160800d2aeb99588ac3de00d00000000001976a914d26f76cd689c242aa0f03ac89fac2b55a8af8ba488ac54780e00000000001976a914659d8e4c49d617e46c2fc3c02db781030fa2ff4088ac18011000000000001976a9145bcf8e9d8065f180ccecb33a47cefc7c6f92df2888acdc1e1100000000001976a914009f28374dd6aeb61a8abefdf67c13dbdce9691288ac48093c00000000001976a914a9dab56baa1a3d964d87aa2ef81277dd3a17c97a88ac0339a800000000001976a914f2af442cf7135c2f94f238024f74f72d5c69379088ac89733501000000001976a914d51a9d822fa82219ee539366dc58b7a5bbaa7a1188aca71b0d03000000001976a914f47edf7a01de5a191a8b8f6e5f18503f59c01fa488ac5e158f04000000001976a914702463bdaa7ca1cefc39ff1e736f3fa8ce9d4e7b88ac2cda0c00010000000140f22b15d86bc5acc00d2796e2c8b0ccd31ffef4a1b36ef091785d999dd55672010000006b483045022100aeb91134f9b0507af947b5decfb4c474e55029aee12192bf38515319d151a45a02200b63a5058a2c3a9db27f3dfaea76b38569e7abe7ba83f3df89db82b53c998182012103f153c6aa23d0506622d2615de6a4e26350d9c48e8772714ef46bb7e4c52a763affffffff02581b0000000000001976a9143f32807eaeb6825b8c6b63a15b32199bfab199ca88ac68851500000000001976a914f90095b93ce56d003f152cea40d246eb7af8c56688ac000000000100000001944af2791b83aa6115671f60dc5f21390189afaab3e1b3d43aab135454e2a0480b0000006a473044022072bb998f03186e39babdf658d189694da3bb527e20b0e2ea3c0dfb90e1ab235402204f1de31d5b0cd2c5151f0d7952fc85b4e122ae5001ce3c2ff89cf174e85cf6fb0121024951cabffe12d7655cea5bd8b0707bc5f088362c257e82090ca588bd90d67082feffffff08ad140c00000000001976a914023b850ea817581570236061aaacd9ceeddd05b088ac4a420f00000000001976a9141d3605b734ad4e305edf8682163d4549d247f29388ac4a420f00000000001976a914473eff34430eb0c7050d3f99a957cc8a92593ef988ac4a420f00000000001976a9145a384da629222c0a1261d7b8720a2755f7bc499988ac4a420f00000000001976a9145bb0d551fc5e623a8bfddd437cc8d3f9e409974888ac4a420f00000000001976a9148fd9c1e8b93f5d4917c9e4c0284fea34d7d29ea288ac4a420f00000000001976a914b087cf3ed068539da2d4affc25abeb42d5bde3d688ac4a420f00000000001976a914b711104f7d22c946d1da83a2fdb395190fc5700688ac2cda0c0001000000014a39a1fcbe937828a94665d844b9e903c31d8cfc5d6216d9b12ee78bc27db79d010000006b48304502210093db94259ab8d175031700f6490e7f6f1382fbebd2047286eefb6595e82d228b02207c919ee1aaa6e3f8658537ca6332f86def08acd67069adddac8657c8203ed6c00121038e5c77df4b7e7e135becb0fe8125b0d8e9e2fcc89558d60dfe59702473324789feffffff0298b70000000000001976a914684a86f5add671e782525fe15440e17dc70045a288acc7a71500000000001976a9146bcf721c4648aa867f39c8dc4f2e7463b2c6e6cd88ac2cda0c00010000000265f918f75a620b2ac101ec48e85c88f4e4877c11d28f1dbc58bffbcbdf534199010000006b483045022100cd65147708dacacd06dea7ee4e4e65627908e2e3aa4e67daf19698ed389e72db02207ced1a25f1a37a87017da05bf4ab8397cdaf3dd5e836585cefb77c1f05756d16012103ed1d1831d0555592c52636296eec2695a683577486aa74b9e929097e1789282efeffffff55f97448ffaceb8643306990f12dbf1569242dc9cb8a112321d100542087d2d0010000006a47304402201fac8f570cc322c96cab7d90b912744eb7172cb60261d7869a116fa23ab13360022016969822adb6a3e46de28bb916d725654224a88ea78828b5ca1812efcc2fc59c012103ed1d1831d0555592c52636296eec2695a683577486aa74b9e929097e1789282efeffffff24801a0600000000001976a9147c7e5d3a3ad80d9b9d139543b3e7e4f88ecbf82f88ac4a420f00000000001976a914064613d18d39a8ac1c2c8567ea44deef8638a1c488ac4a420f00000000001976a914440df24077acf192e35263197b78fa6e1f3aa14688ac4a420f00000000001976a914612890e2222ece98e733c0190ce125acee1c844288ac4a420f00000000001976a9146806cf8ee84edde7659e0b891945987f6b35869988ac4a420f00000000001976a9147698e3108e934eca10e7e20223a076f5a78fd9b288ac4a420f00000000001976a914d0fafcd445af8fdad3206232adf87cca26c17b9288ac4a420f00000000001976a914e2bc7f9141921e6de598c38308b18cc7d0e3576e88ac4a420f00000000001976a914ed69c6a3567b11b738950c4bb388b780e979895188ac4a420f00000000001976a914f63bedb65425e2587d9d0c2e8c2a53ee8c41f9db88ac4a420f00000000001976a914f71c5a2dbe6a94c966dd3736767838cb6e1b644388ac4a420f00000000001976a914fd7e0029c0c8f85102e897ddef6fea083b6d765488ace4969800000000001976a914180400d7187f98383d2572d363e9b52432a1674588ace4969800000000001976a91425d88718481661e957675807993cbb9d32a7b60388ace4969800000000001976a91440011989594cf394d17d7e8ebedec01dffa643e488ace4969800000000001976a9144538f85b10ab4df378481af4f935561fcccb7df188ace4969800000000001976a91495f096692a628f3da69b13a04ddef9cbd28add1888ace4969800000000001976a914979f1e4bebb174c65426bb7ff1a29cac98dd4fe488ace4969800000000001976a9149f271dd93552043ca2f209e790bf1515d86b913588ace4969800000000001976a914a636934e3948b0cdd3f6de8de6f23cd8382f906f88ace4969800000000001976a914ab0cad550fbc4c6e3eca6c7033cfb1825e2e01bb88ace4969800000000001976a914b7b9e35d467dbc777b0ad966ffb1fcc70decd0f288ace4969800000000001976a914f34cb2f44ffa34a3930b5fa445775d802b427e9188acc9511602000000001976a914dc6fbb4ef7371ee8e5d7a6d2fbe78be05fa27a2388ace8e4f505000000001976a91404f8471808e3023ac2504caeeb6b0d807d00696888ace8e4f505000000001976a9140572b02c2e2afd7603228c7e32ed84810c5d7d8188ace8e4f505000000001976a91422b59b3002e6b603bba3caf890f267f63d86914a88ace8e4f505000000001976a91425f59fcfb329357512c9ac617ec80c3a9506c8a788ace8e4f505000000001976a914447cc1142cf3cf9018fe1a141a75f2029fd91eff88ace8e4f505000000001976a914ad5ac6387543b06c442e06b94030298a9705a70688ace8e4f505000000001976a914b711107691c525bac305014c5d54ab1ccef46ae788ace8e4f505000000001976a914bbd76bd64a55b605781996ad488e8008e62b3c9488ace8e4f505000000001976a914d4deb394e5d4aa26a16e199aa4452e797bc36d1588ace8e4f505000000001976a914ddb0e85422c93982e3898bda6506fc654d1a340b88ace8e4f505000000001976a914e21da5d36e93786a8391800c9a4f0c2c9f37562f88ac10f19a3b000000001976a9145c93eef04ee1e2741df150205b7cfff2e702ba8188ac2cda0c00010000000107bf877182315e336aed7e0a14f4a90ed17ee5d4af3d4d5e72eb8038210ff14d010000006b483045022100af3f969b357d44b7aad5fa4773affe10b0377abeb04f6691882748416bf0bf04022056b29f3ac01ac2f4d837b2b21e0561c63ebb4264c35eea8b31843342acba754c012103a10f02674f0e19dbfeca23836034bb78f0c3f1a19f945688b4aaf1c543a5afeefeffffff054a420f00000000001976a91418d12fa317275210b8f2428e23bb0c1d706839ff88ac4a420f00000000001976a9141e2682976a450897509abbbfab1d12dac7e9347788ac4a420f00000000001976a9144559284629520ce65714d2b811a90dac1a422b1888ac4a420f00000000001976a914eea5e047ab0a07f87928c533459a2bbbbca4d73e88aca0480f00000000001976a914e6d167fa2f26f0ede6e346544985558f9416e8e288ac2cda0c00";
        String block842284 = "00000020e5feebe1f3f076a3e492e78ff6821cc1683b7beaebb7dffb11000000000000000cc5c803e8993ba38e6babdf4ee003fdd27b56d73c44d9682b70ca6ef0bcb188a84ab75a11e5311910d143c00e00000020010000000000000000000000000000000000000000000000000000000000000000ffffffff3d032cda0c03293917082000013142040000fabe6d6d9d09e8ed69133710b552bbd84406a03e46d0fc496006792d9c1619d19f7ea90d01000000000000000000000002fe0ff909000000001976a914299ee3285d170b3248c758705a351c5468a1dea088ac0410f909000000001976a9140e8aa46b35ea5fee4371bc059b66286395090a8988ac000000000100000001181193ad25b8ce9aed121bb94e8671397045a4e9e461c3849f9547cc2184b917010000006a4730440220495c387ff87778f0b6809c9e677b4045fd26049ae9cb01df5629da013cc6b8a902207a086213cd71f6e8f5766fceeafa668e6f0aec30de4304e5a66f531d7205fa0d0121039c9619e25ca7011d32d75eabd85e78aa7a605e8c97ad8361a40db5fa85f9c7cefeffffff030cef5900000000001976a914755787fa3fc22e9ec42b2657b27483215e1728f388ac84ba8b80000000001976a914dc6fbb4ef7371ee8e5d7a6d2fbe78be05fa27a2388ac6c59e291020000001976a9144fcfc1e3bdf871bbd81a545be9410aa05dc5070a88ac2bda0c0001000000023765de7759fb2d4c29f06315a1abea9f30b588d711a4c0a421eb30816717c0d8010000006a47304402204d457f35274071ba3a39b3a281988e385bc900f722ab4ef19c4e7b2c196ca48e0220345229714880e98b319d90a0690bb5521f1af955adc2f55108ec19c69c8a3733012103b22f52b5cb16bcdfde08491db90b92d4d58bb7ad556989c62ba4252c43dd985bffffffff999a1cbeeeaaec23dfa8c0bb51bf526706bbc7271b54e5b617be34bc36f0124b000000006a47304402207f27da889a6335d2fc0f6ecb8a7630b729b4f617bd4569a64763ee32f7f8f7fc02206d2ee2131a4ebda9ad1fd1ca290948c11b0071309ab26786afab2155d4e197dc012103b22f52b5cb16bcdfde08491db90b92d4d58bb7ad556989c62ba4252c43dd985bffffffff018536ca00000000001976a914fe09d5af5f6cf07569962b108eff147263bd677f88ac000000000100000001651abe5616e78e38be202dd2676fb31231cf61b676708685f48d3dde5b9e1dfd0c0000006b483045022100ac4f14eb47554798535101c60fc3471e35d4d301d92046c187545871e09f2ba002207882a9e97f9a9312cb4d2fe27ce0049de0f928a519ee0c74cc8db9667ef214b4012103d39d7ba6509ef32c0326ce4709fc3c6a028338f6acd0b8a15be1eb92c06c6a9dfeffffff02c41c4c00000000001976a914170c37d2ce1343b3cc4375d25aa411d22a7d606288ac10534c00000000001976a914e6d167fa2f26f0ede6e346544985558f9416e8e288acfad90c000100000036effd289f38ddf0b105f32d0ab170c77003281cbd728df5a15dfbcbc424b4e702010000006a4730440220260bec00c159ccee9c71b7ff0a909a573e22a026d2f22d6e16d865920d52581f02200f2f44b660a055b0d6844ff24fd4b79e7352421e4ec1a778930531ce107e12c001210212ebc8908b45149bcd3ca0bd5ab8c8ea83b9f95f0e04909d6a2832033c894580feffffff778730e5c4e7214c441fb0cecc746a33be31ccd37209b6062a3e610f4e1a9504000000006a47304402201c31c81c51675422b39e95fc8eedf643424bc232e84b133938eddcca0f5bb075022014c0eee4750a27cf2f219b6d9dfb0d57d7ae1e8bf395bc667c3cfff0622efad2012103c6f4eacab49b8bf97541faa3db0ebc46ed4cf6e41d1aaa11b448c712cf39b9a6feffffffb83b1e82ee6c754d83d3463114655d4c66dcfd2913eff8e257404957c7c5f112030000006a473044022038e64112b1099edcbe2e006c41ed0560ee7c56df3fa92f7cd04c6567c4060a1d022019ad4421a6a7ec8099322380baf8928d842bd4cba75a11653c58caabe56702df012102d82b8b817635e4dd14e811c4a02445ac4b9016d1301b8de3d1c2afc258d55093feffffffb83b1e82ee6c754d83d3463114655d4c66dcfd2913eff8e257404957c7c5f112710000006b483045022100e88690bf99f797aed15004101d09cc887150e82296b7193b879e1dd6a796539e0220135afb25d1a89aef11c76761bc31770fa13b7d550de4f13414620ebd86c4920a0121026bf31be416e79a11fa203193d63f91f0ff7446ee00f97e452afa7e38f0e46f52feffffffb83b1e82ee6c754d83d3463114655d4c66dcfd2913eff8e257404957c7c5f112f00000006b483045022100e1175e6576bde5ad393fc9b570cf204ccde72b49eada6f5f11f21503f5a4886b02206cd41692187feb7314aa37cb9903ab903fa904ac24a53f4c0a7dabe6db5f6850012103479f9699eb76674522d076b162bbaaac8dccf8c09cf9989a687fee04d1a49474feffffffb83b1e82ee6c754d83d3463114655d4c66dcfd2913eff8e257404957c7c5f1124b0100006b483045022100aaaf3db31854ba9dc5ecd451a7601dcdd2cf6b4179d8047a88bc3e20bcdd5f1c02201a81501b8b18540449c349cd4ab9cc431566577c74680d9fdeaa0a1c23054cec0121028326d5aa6d2a80dbd92ddf7d21eef7b59445add729bd1821572ca0bb79504b1dfeffffffb83b1e82ee6c754d83d3463114655d4c66dcfd2913eff8e257404957c7c5f1127f0100006a47304402206dc6db52e282b75aa5bcea89bca5197351998df1662dc8e026f54b4c17bd79870220758193840f353d4efdb60f8e17f319aa54504195f2bda837d2fcb53579f0381101210272afc39340fa8233dd2d4f2b5ae6cc41e791ac442754e1086581c34fc4967b52feffffff6490d01631211e5512c9bace8d2a446653695846b7d0fed0080e7d9042a33924040000006a473044022012c1d96ff5ca3690697de63fb6fc265ea6eb8ba952c15726a53dcaaa0a78d9f402202f9374bded184459aaf28a95b7a04768375ed15fd31bb771f5e3ba5302d9be980121036edc1431b30e008e5fb5a50104e0b881908b21e436c9e311fbbd856402685784feffffff6490d01631211e5512c9bace8d2a446653695846b7d0fed0080e7d9042a33924070100006b48304502210084c606407fd6b0db1617d8be1d8272a7b82e343cc67de7f2396451bb2edb7a2c02202a09d43c8f837f2fcb7d4d115caabeed5b36b8f1e85cb8ef1de97e6175371d66012102e1a946a2f3ac86bc2c0b5f0c7d983e58691034a8122ba2922a2f558769b14589feffffff6490d01631211e5512c9bace8d2a446653695846b7d0fed0080e7d9042a339246a0100006b483045022100f7b0e3727d004df8e64710cd849eb9935fd923a4d55ff2ac194f7e720fcb210c02200931b45eb84ced8dd11a6f7ac4b2d7ca40368e3bfae054ee6b9b09801dd1f0470121023ac069e1a9677267eb592887b6327dace02a559fd50d8401a36c7145b0a0de5efeffffff6490d01631211e5512c9bace8d2a446653695846b7d0fed0080e7d9042a339249e0100006a47304402206e2cfc21db36fccd3469a288b3469c0641807460e07bf84772f5e478c876ea9b02205cc241e4a539140e5d4a62012109281095e32b836121081835fda336fc91496b0121036b03b373c5e1e68eb72974239529c36f01be0d5a5222491b59836c38a288ff9dfeffffff6490d01631211e5512c9bace8d2a446653695846b7d0fed0080e7d9042a33924da0100006b483045022100e1fe7896f2345ecdb74b6afa89a1f6b40d57673d5d0d92d2996cbc7c0afde2be02204615b9e27b49024ee588c5bd7d2b6258fdceb503b56ed2cf7b415d07add7bcda01210279f0f7cb0e0ae610f8e2553d6e24edd545013070a6aef178241339d4237ced34feffffff0c94ac0ff178148dcaddb8d9e29d6ee0eb8d22c16af2334eb252bdb1482336421f0000006a473044022063569540a715bc19cd8377ef76fdb3fad9b7dcee1214f8bab295040aa45943ea0220121208ac4428964a8f35adbaf497ce40333024cb797e0b2a34d230b773401e67012103e2ceccae765f65e191f234420ecc9e0af3adf1749c42dabbe8a09deb6a0a8108feffffff0c94ac0ff178148dcaddb8d9e29d6ee0eb8d22c16af2334eb252bdb148233642670000006a4730440220412f94adb71570a3cc81de381060360f1d35aea0583eb278d0b8c66af84b086902205c8341639f3d37a06d2567b94304e36a5ee09259f040082e7155c2ec6a1248f1012103d0aae90f71896863929975fa31c32b7228f20bf3e07fc9b7639ca117c7a6c389feffffff0c94ac0ff178148dcaddb8d9e29d6ee0eb8d22c16af2334eb252bdb1482336426f0000006b483045022100d1545ef2b96a49f5023ac90e4708c83849b14ce50e5d66c2e50c1170a1902e3102200ccb606be32f748b3ba23cf64d15d72cf62c3b8c83c0141609cbbe79493f81eb0121026b46746514e50a3512d65dfaa2d4254ddc4ee7bea95a987c7a29a22949159822feffffffb52ad0055f3e0f9a0abe1c4e1fa02e55119748424da504cbebe313f71a86e244420100006b483045022100fcbcc436d3c43d74b01794eb7e9a2c5d3258b0449349cbcba4bb040a2f6b98ca02200b301b8b0e1c8b4da496ed6556497b78503bad75ddfff8b7ed0c0efac5fed760012103123480b5f6e05d1fbbbeb2bfb9b2960f5327ae111543a3bacbb665639a127fa5feffffff490e7b0d41586e25f52cd0cfa678740c15042c4af465b7b41fcd09c7e0d7b9453e0100006a4730440220042d2472b0779224c257e4771da2dc8b01e0b2b8b63a411eca22819038bce1e50220698719db99137c5ad9b1a553ed25130716bbe76fb8fa2f0fc539a1b670f56fa6012103114931e613841c259d081c7929566b9cdd856d3b4e18cc0835386dcbe3b00e12feffffff725d8da99a642357a8788c8530d72edd9f8650aba85b0d9ca3752d2c7a64dd45000000006a473044022067a9bd31bf4088b8c2e444fca1971e063dc1a1b4e618ed657b07e46815df247402200eb87d52fd1d6c820744391a758d290e6dbf07578fdf699f700890f60523602a0121030bf31d563a29f2208dbce2bc08f2d4577f7baebe6090bbdfcce052c3017f8d31feffffffd9a256737cdc53d158a28b90713a32085fd11004a2ee9eb74f127bd579764a4d000000006a47304402207ef81205260ab59271cf5219353650c2fd7ae3c646c4a5b0d629d22de2312f0d02204f558a6c64ba89fd44fa79d2809d2a4ca8695756204e330ffb4971ade7188ae20121038da6fc6013bfe399d3efaf618a958db38b0d7265411e70f401687800330a5e76feffffff1301d13836e890906ff99b6701dc4992de8c527bca2c873fdf4f3d69efdda25f010000006a4730440220663384e5a81ef39a79270d77457fc250258ad6510662588ba9db3078276ec56d02204d66bf82cd08eb8d26c3c105286e5b53ac1adc33857e0e8b4568f4b32a115108012102eb784ae812da58d008b17b69812d912a1ec2e3e9acbc501b3208caba1ea2a7e0feffffff9b049e2972c5674945c75b53fd240305ac369ba902d9559a1f5e952d46af8468930000006a4730440220421a0b4dbd0b7fac9512592aaaaff733479801eeed9cfc0e129824cf3634b5d5022056a3d85ac6629c647a764f675f72b231464ae7febcc54f1de6c96ebd0b157d7c0121039cd4b5f6e2a7b382de0b6e852ab5d7d16d1930bee557bd8c6d043fb4b83b4008feffffff9b049e2972c5674945c75b53fd240305ac369ba902d9559a1f5e952d46af8468c10000006a47304402201edc9fc0b49411745640ab6c5fec0db83f31b80cff3aa80eb504716b7206e703022054bbdb8921793252fae81d500dd26b350530ccbe2ed22bd5d8500c3ca871506e01210343244c1afa55b800848fed977c5fb41b14a0d574e32ea368a2fcb66d482ae94dfeffffff9b049e2972c5674945c75b53fd240305ac369ba902d9559a1f5e952d46af8468cd0000006b483045022100dae37a8a73df22beb3541784d5e8b73375c632f5fdf32816012a034eb682492802202eff338b3b7d2b627c54570a10ceefb1a3a24e8f4fd39a261cb4051469d43aa8012103c4bac90f3764d387087beff267cd76c8ae8a5a924d31422ca02877f9f24fe67ffeffffff9b049e2972c5674945c75b53fd240305ac369ba902d9559a1f5e952d46af8468d90000006b483045022100c2deb7c860683e5be988777e0712ab3a151ceadd29bfd2068cb0026f29865b3602206dd48e44dc7747742509b020e3f3ba5891dd80733513be7103ca5bf1d6c8b8b401210268bdf6f35ba1d037a7ac523c3548a0ac96aa19969c066260e34992b9557122e1feffffff9b049e2972c5674945c75b53fd240305ac369ba902d9559a1f5e952d46af8468ed0000006b48304502210089d7912fb402637027c6b8b4b851cefae7f84c23d2ce9990f03e227cdd934ef302203376ef3a9edc3b415d292ac73728470c83de7c20d58981a72b70866b4c74bc3a012103793de6f7e95ebb40be779c8d30f7d901d5a9bb4c3ada4669893e50a668c0ff1cfeffffff9b049e2972c5674945c75b53fd240305ac369ba902d9559a1f5e952d46af84684e0100006a473044022045a17a2c35503df6cc5bbdeca4e4135e81f832c619dac88fc3d2ccb6faa036430220755773423abeea93689a0074dfc19bf2b02b82407c9d2e5672cc9d68c019369201210326b36f5f77f8bb6c6a09e23fa781237500c886da2bddfc33b1fe8723ae86fb18feffffff9b049e2972c5674945c75b53fd240305ac369ba902d9559a1f5e952d46af84687c0100006b483045022100f304e64780531d28efaf58b9ff71a3cb5b787dd07b85542e615595fb2a8ca65f02200f134de4e077044e98c014d2a78edd7348621215cf69bcf72bb366405d7689d5012102310a9aca870fb3a397aff49ec9afb889db9d6ee266fdd12f3938487a9815809bfeffffff819c136016d42200f7662c04ca2171ecdefa667239180a97d29d6682482c0587640000006b483045022100f2e65d6d6d2bc25871fbcb2268181fa3838871b209e020c4f5e0a728e805da3402205a80822a9c02618dad79ce9a155dfe7863279a80231924886f447a70701e4298012103277b9ef75288bfb37a145844bdbad28042114067cf07753a13894855e38f82f5feffffff819c136016d42200f7662c04ca2171ecdefa667239180a97d29d6682482c0587b20000006a4730440220689b5fc897096cdf18dc49813cb34c79b76f7a3c070d06b9d839804fe311fd7f02207b0f8bab807316b9abe0ce1d877769e696e9ab2f4867d342406aecbc8520fa6b012102372ff91db98174880a17805eb3232db4dff202d734a6c4722ed2429259a38bb0feffffff819c136016d42200f7662c04ca2171ecdefa667239180a97d29d6682482c0587070100006b483045022100df99b759bd2a6d69b1c6dffdd03edb89c5e4ef294399be3e139c9effa7c97a9602205d4285125d2a0641bd9f4d16e11c09369350b44ce7bff735569342ceffd60ff8012103d3f27d808c69b49b0226040592d5861057471e91764841a51890de6ce929b7defeffffff819c136016d42200f7662c04ca2171ecdefa667239180a97d29d6682482c0587cd0100006a4730440220720f3f0f86d09ec4bf3d47a291e05770eede166b19941b7b415355db6d453b490220291e05f8413a29dc76f435446518b368b5b66ff53caa50dc4a440297dca25f7a012102e13be408fa10fd738cdb67f8ef9d4864b0998181b30f6592206262efece7ecdcfeffffff819c136016d42200f7662c04ca2171ecdefa667239180a97d29d6682482c0587ed0100006b483045022100b08817674fb815d532184500fc3bb5c22df7e7fe2841b08519b4c11de71828b70220694337df0a904404b2ef445ca1d24b1271300891906b8f1523afa24b376c5202012103501e709d5e214c55880e17200939b3a64e61e3bd24a7c295d6cae19a6f51e936feffffff27b8223415fe812963b765a4dee85a485b81800e31170a60f424cdbc77838596280000006a47304402206a85bf1ed5bee5e6c8aa848549448ebd8dbea885b0b7a0ded04231f3f32bac7102201e52380382102abf4f033a30d18b9d00f353664566575e24ad39ebe316f73d6a012103cf8d1c4eba8c733d5e9876ddd2807ef397a74316d4288bcae5b99bf83236a820feffffff27b8223415fe812963b765a4dee85a485b81800e31170a60f424cdbc778385960e0100006a473044022042f51259c8989c0677b82bcf12ff67ba5f34c3f05304f48d3d71d5e2b475c14802201027cbd7b6a53b3e6c970c38ad968b0373eb00ef1205c28c4970f02f04d0f1d8012102be0f86313980d9edd7194a3d7a736addda0fee59afa9a3de200afed2558fc338feffffff27b8223415fe812963b765a4dee85a485b81800e31170a60f424cdbc77838596bd0100006a47304402202882519717d68262aef9aa0e08ab9a7aeb4dc2722fc5fb4657cf22876679e040022072232530070dc7b8f846cf5ad88613becae8c6c4542903efc4d8b8093acc28fc012102d6b9742b3f0d2677a82dab0b09d9b426c002fe69fedbdd75e0cbf4b3ca78f5c2feffffff27b8223415fe812963b765a4dee85a485b81800e31170a60f424cdbc77838596320200006b483045022100ef5e0bfaa46cd43cdedd6f94863f1f5b3c8ea9d320cf56a1027d783a8ad642d302206e362245d45f2e5bed6df68ea897f8685f9d4d56155b3268ebc9b4328022190f012102f82f05bff38fb346f35f52bb568a4c519028bc7922c642913057c061d3d60602feffffff27b8223415fe812963b765a4dee85a485b81800e31170a60f424cdbc77838596ac0200006b4830450221009cf761b439509f3a7b283fb4c0e0a6e4fd6036b5f97fb561ad288eab981cd4550220602bbbe6f5faa0f6aa833297dc6373772f1ea4b3577ff1227919ee5df9d84e9a012102cc1f87d12cdb72e9bd111a2f7e129154ac6dbb22245a47d24d641853ea4dc6d4feffffff96bd68f40f5189d2285f50339c654a6c891deae64c8955267e9b21ffdaf13e98090000006a47304402206f0f9a9d67896ddbccd7fb6df95bdbb50f31eca77fee9201f46ac18a806348ab02203e4bc324fa0ef0df0cb6dc5b34f6c82234209d7463c55d6f6f1181e25a1ad99b01210380129428179adff8fa783048414e5dc2b1493eac21e7a9e6984d9544b4016adefeffffff96bd68f40f5189d2285f50339c654a6c891deae64c8955267e9b21ffdaf13e98950000006a47304402207e764e8d290cb31b403958014d8920589d000eea38cea8a3dbaa749c00b5927a02203551866483753647e7ed8e08f569782e023ea40026946d6bd23a8fa50d48b9160121030cd7e57bbd55e2c30b0e7aaf35a1601ada5517b83564bc445172cab00f01f8aefeffffff96bd68f40f5189d2285f50339c654a6c891deae64c8955267e9b21ffdaf13e98910100006b483045022100df00643433a4b53739290e46c13c9946fd9dbaf3f1ab67ddd950c200008b3af4022005eda773582f452a378eef9702d250ec332a1826674a832bb559d9816d31692f0121022fc7e1989b21a4d78f836c36cb5c7066ed98f16f6b64f9b00065c260a9de9b7ffeffffff96bd68f40f5189d2285f50339c654a6c891deae64c8955267e9b21ffdaf13e98b90100006a473044022002f4658f4b51ab7884f8ceaaf25106621869cfe24bed59c32f3361e4c7c6736002204e205af2fc741aa84f0edc455624f58eb04a81fbea90418551c32f3dd4d7dfa9012102d4e28a02e344c0a8d9a36f889aeec7818473c35382e9745ffe7d8039b5442a0afeffffff96bd68f40f5189d2285f50339c654a6c891deae64c8955267e9b21ffdaf13e98cb0100006b483045022100ced75369a7f6744563fbf17bb94bb3810ee19b0a2348d36bd18f5c617e4e4e4a022006744c6ec6d8dbe33100f78f5abfd831fa4828ab2077ad09eaa9a7d0c53212cb012103eaa14c393e40375f0c3908cdd6af2730a9dec18a08371a61c11642f1b88371a3feffffffd8f84e629e214f36c6189dc8381872b1cf83d484678569297b3a46b8da7e76b0000000006b483045022100f7959481d666a4e2767b28ecd0f9b543fe172fa10d28f534e4ca8c6704c4fabf02204a2a69419ffa77b80023007777725858491625a10d7943c42c48f92538b39e33012102eccfdc9a046ac61eef7f4aed4a28adc5e55ed01d795ad5c76be8d220a50889bdfeffffff66a2ac866e2c103901f5531d1652a079e70e0e84bbbb9a9c2a4ee12424ef04b3000000006a47304402207d1b03214fe7de5cce2b6389434b2980b59e9b69c3e27c869590fd01e239ed9c022057f20367b9ac06196b9f91bb0cc05ff586287b641eebb1ccb2d42ea1aa8f92c60121030a704068d3e4512d647b40a78df603c2f65f37246d0600015643973dcd9a3797feffffffe1d7b0d4d8e2839f3a6d2e8d208854487381595480e76ac85b3e1629f8846cccfa0000006a47304402207d4d61c252dba525641e4967dca39ffca2acf5c9b2b26cf1f865da85bf7782b202201eb97201451393be5bdccd902e74dbdaa2cc2aed20148709477df7b5afe06213012103940b5185a3f30bd130c1537d3929ba57b1b68853a0187d4eff10a9389097382cfeffffffe1d7b0d4d8e2839f3a6d2e8d208854487381595480e76ac85b3e1629f8846ccc290200006a47304402206980bc54ef305fe578230ea8d02cc83eb64fa8866944ee8b3533c82192bc458f02204a2bfb45a242f944c4687465f214241e0e503dcb1e9f601d67e3394279fe343a012103a8479fce540b852068de39f3df14cc1a9f8d31c0e251319dc042ff86e09d23d5feffffff8d277e36a61b7b894966770ecb900f964df34b960fddc118090f50be082b69d3200000006a4730440220024c592fb993fe260c6db0098297d6ff7722ac57b86b5ce5b89b8bfbb319fb5b022068543130be2edc72d879f48f9010e913118ad3837dca9c2a448577ce048917260121031bb4f99ad8a81ff252a1ff4745544dcc9646383820f4395517d500117f605f56feffffff8d277e36a61b7b894966770ecb900f964df34b960fddc118090f50be082b69d33d0000006b483045022100b0c30df69fe669e08a0f56745ae7f9d8d87e9a8188aa077eb7b027ee42bef3b5022018eb7edfe4fd6b4bd588490c8560cfc86734fff0eaa64c60807340e3e797c26301210254fbb1d7488c5bf5800b57540db4110b2c0c07f02483de64bf0cfc4427fb6f37feffffff8d277e36a61b7b894966770ecb900f964df34b960fddc118090f50be082b69d3620100006a47304402202a5b1b5430e76cb5c7825d201ea08c81f1c2d0381319ad3f522c8dccdd2ab124022036d3d9305e3f55d1ac8f73b1797a9e7a6bb916e59f07fb37067816db415ff2c5012103779aab4ff24a68b2693b010ee5753c2dcd5b57bde9288beb1b1e67401421fca0feffffff8d277e36a61b7b894966770ecb900f964df34b960fddc118090f50be082b69d3b30100006a47304402206a1585ee2ef75c474ceced3cbdea01458bdd40c10711f644116c47f3924806d1022041208a4bca01507bea8e06c39dcfb9548caf9aadf2611bc4a24c0db317fe538c012103a8c740ba15e412fd9738a79abc5a4c7d3b763c3bec3dd7ba259633cfdd24526cfeffffff01c09196e378b70db1564e4ed5e4c534ef3838daa8d93c1afed8360b7a847eda000000006a473044022048cd81065e238d311aea5ac75a8092c4042fc1336bdc5ff6f36240408174733c02202088cd0cc66ee827aa9c332aea242c29f260ba373bdbd64ad3bb27275f361bcc0121031dae436a4c74f05ba1834dc7dad01423340ad85ec83370d447c1822b994dfacbfeffffff4e03118400fecfb8226b388a767ab6bdf538fe341be04826270628ece3b198ef490000006a473044022028bd0a3b9a354852701d847623cbf66a498721bb135539fac3619b2099435c29022027387ac31ed5905f58514191c5b5723642e041f9f39ff62d80821edd62bac82e012103d62f30df3e7df458804f1b4543b2df4985060add4d9f8b066fac79839851e46efeffffff4e03118400fecfb8226b388a767ab6bdf538fe341be04826270628ece3b198efaf0000006a47304402207df08c512f2f5dd2f5f42888927db2eed1d7b6fafd6abec9b300daa94a1391b602200a66ffcedf4900b9a01e51ceeb00362334f336a64cbe73b0763e9ac2782b8f1b012102cc17d5334dea29bc222d8559887ae778c4f46365a91b413338dbee729d5cf571feffffff4e03118400fecfb8226b388a767ab6bdf538fe341be04826270628ece3b198ef960100006a473044022061195eff3cd5fc206a340bcc857b9b7b89989421cf18c2607f4c336263d7bf7b022012dde04ac834b76b60121620c7f7a777111b28229f7ececc66a0aab51b8db2ab01210247f3cf5d9bbdcb3e8cec570068fdf255d4c093fe1acf483ba0d3a8b3caa15182feffffff026e711100000000001976a914b8c3e232b7004c2e96403f1b5d8f7eb0769062a988ac586c904b000000001976a914009eb2da10438cc7fa012f6493e2c91e0e68189888ac2bda0c000100000001ab637e4018e246ec42f8c7cc9271c1eeb3abbdec1424a0083ccd8fb85f832a28010000006b483045022100e116a2451eb4c70c2e5e65560f9e3c976185b6862b95593087c214415e2e362a02201817cad7c67a7c7391b58034112eafa19c12917fb32a3f1cc9b181bd4b067e2301210258cbb0717d20048352df73e27dd82bd860f4f65daf3dc40777339825535b36b8feffffff0230fd1300000000001976a9148c4e76cbd631ae01107f7586d3eaa3382c47b60c88ac56956521000000001976a914b92e05c19ef572f7588f09e27e7ede3f36d4f1be88ac2bda0c00010000000861ebc51e19c6b38a0a450b8c76e7887132b9461b00d0d3e40461f4697ed584430f0000006b483045022100af37835a300c2fe3a2b96d6b20e69c830b64dc3c579dfed221ad22f6acc6d2ae02201a79b6b53252f2f5a8050e63b0ab9a3dba39de9c5e87e395e97394b2178a15c181210324f6e97aaf50ed5d11d0a6f8dd3f925381b07ddfc9e3216f471a961d98a6862effffffff61ebc51e19c6b38a0a450b8c76e7887132b9461b00d0d3e40461f4697ed58443120000006b483045022100b52ed98af03be39895b5dcc65217d6a3db96016b5c91015d1a7f12ff3051f82a02203ea987d6d39ec18ebba00b77b91e7cda31e54030ad30931d184a02ea5940449b8121028bbbd6bad414f2bab6f563ef3a7308456c6de346b03833bc5457b2b7583e0c4cffffffff45a0bdd5030acc640229aea6ddad4b09c1828cdc77698eb97512a57d0caa7147010000006b483045022100fdf7b4d56688a30d60ad352f05d721d8b6fd6764db76b1e477da0c6f345e02d80220103222bf673e057f1d9d163f26aac86d9c929c4617b03e8ec5d677ce2fdf4f3f8121021a258f956a207776ce7e7f581d3fc202e2d8af5b92561369afbe5b863b982bb9ffffffff8ab2458234d9d58f6b59f497de083eef6185365e0d9c5071bebbe176872d3549050000006a47304402206c5302c525b2e24acc3a9103866b8a330e885f690524c66a3d762130876f06b002204910ee8117af863a5e003c12ccc96748190b8e1586b8fc6315abd448a4c19bc9812102c522c308dd3906bd235c67206acfe62c4c5bc8e0012a0f1e02e2364641a0672effffffff2e17438811622d06beff514dc5606a9a7979fd18eb2c30f8ac693342536410770c0000006a47304402200481bd5beca51d8fb10c0af0bcf83c78c1dc2bd63ce8b2df9270d447fdc7cabd02206faec2524dc9c6da7495a4938034c16f766719ff018389b73d2684eb51394593812102049cbacae31bffbbf538491aaeaef3cf801c9e078799e44b979a43a7286b146fffffffff2e17438811622d06beff514dc5606a9a7979fd18eb2c30f8ac69334253641077120000006a47304402203d5f22ed3e8a6aae1f874359e4fe7b7463cc36f5a7edec08a6fc0139dfb9119202203e07643ae453d8c7629315bfc0717b48d10d2fbb4e4a2e0b14c506d0bdea0b95812102dc6431323071ec6d52e2e0ba6750ec8b071d740b43a6699b6edaca754c1388eefffffffffcfbf34f77fdedc72c20c584ce69a8f1e9b6488870d3bdb5d8ea049b3909b6a30e0000006a47304402205717a26b0c5d01e46eb6e8e9e488c2ce2d76a8d693c0e189638033bd610e54eb02200a9b62973968d9ffef607b1c56ce6b55bbe7af0e57bce323a4a770af46eed75a8121025a1f7b408aa78adf4c4058b17e87d2b2050d7faaf1dbf1551ce1f0ebfbf3c840fffffffffcfbf34f77fdedc72c20c584ce69a8f1e9b6488870d3bdb5d8ea049b3909b6a30f0000006b4830450221008d1ebed2a422dc38d71bab7f890217fe15d6e07c0b1b045f6ba1cc0eae18b1580220349e1e322781b510ea55b16c5338468a7610beb74a5d098ba64601251414394081210259cc432bc6c95919143282a19861b9d8bc1e332e3cc11e8f2347b7a23e2c51acffffffff08e4969800000000001976a914174aac8d3ccfc9733e8eea5d0692de8f19d5158188ace4969800000000001976a91418e59ccb0eb82308d6aa987f96dcb3ff7be130b688ace4969800000000001976a91419a80314fb7374770a62c1385cd0210ee8a4b3d688ace4969800000000001976a91444ea880d68f9f779bf6273a77c5f6b66e1581a2488ace4969800000000001976a9147bce544d17c0116fb3a2f9abf923d492b53326aa88ace4969800000000001976a914c7cb57a2362ba12d22f8a2e4cf2f20012629958488ace4969800000000001976a914d15deb03f963c276e45504ea89eab1c47fb87d9c88ace4969800000000001976a914fc07d0d08dbdff45d793d47b12450513bdb84e1188ac00000000010000000f4c78dc7b97110d9758873cbddcd4006b848f0af3330300fe05e6c0b93e112574010000006a4730440220513d4ea81e1084bb9cbcb5b750a645010f0ba45f1f730c1cba9a247d404afec90220454b1776f5d4b94c3b3df9dda238dbe61c9888d9ad00c89b63951a747fc946be812103d08a2260e3afd28e0483e62ad22ba878c2d126083d1a69bc42deb3fd5e0a016dffffffff4c78dc7b97110d9758873cbddcd4006b848f0af3330300fe05e6c0b93e112574020000006a473044022065b25c70470d5e991d9b385537f156f01f6c4a20de94843efeff7af573f07e61022005abf1f6cf5db4fc64b68d455a989fb15396efe6fe0cf6c699371230106c72c3812103f3489e4e497301f497ad8d0dc8a3b794c0a2d3ba4d1bbbe7ec6c81dcd2a2ccb0ffffffff4c78dc7b97110d9758873cbddcd4006b848f0af3330300fe05e6c0b93e112574060000006a4730440220380102e2733ab2245f5fead5f5bc16455ee152d64f78f1c97a74cb010e2125220220154353f92441d2875e149411258aa2c2d8aecb61efcc0a375821feed5ab435b181210296f1d85c90e5eb86b7bd018e385f38d9d9f42ea2cf74d9a5457da02638b093b2ffffffff4c78dc7b97110d9758873cbddcd4006b848f0af3330300fe05e6c0b93e1125740b0000006b483045022100d2449a904b891cbfae9bd2161cf6c478d23a0ac821fbf8e951e0ad53af1faf2302206c5915f663916bf2ee9208a76950c320302a59231099e8a165f8498bc39c499c8121031207461b892d6b7094fcd1d38dd528a6dff75b6ebf5949464cb19858bc0af107ffffffff4c78dc7b97110d9758873cbddcd4006b848f0af3330300fe05e6c0b93e1125740e0000006b483045022100dbb1b5429ea366b59fbe6d692d3bd23ee2835a56a6f3e647c30232963228834402206e793ea1132de93ef17a388e82382b6943af91899da71dc0e43aace4f83c80c68121032661d3dc21cc6bc2bf3f48c371df9e2aed4bbd536e2b755973990db50e9fda29ffffffff0f1d2d7c8ccf22289ce7a3a48f9919dccd264b0b953368d226862e9fcbab38d0020000006a4730440220144b9d59f2b5be2375f50b3ad401e39653cea11cca4a9e41f45b23df3a30874502205d9d146ef09abfeb68a414a411e224fd3c392390bc95e0004a1c1ef1068eae1081210333add1d3eee7a7d0c7961f680d15f864286bbffea4d4c60605ecbcd3f9c97aa4ffffffff0f1d2d7c8ccf22289ce7a3a48f9919dccd264b0b953368d226862e9fcbab38d0040000006a47304402207d78433a1e8cbd70d888c0d3fb2d4aff414a1a3113d430829edfd4526e5a73350220303ab4dbe3895ff5128d56cbc3bf81b11f6731c58aa2e813985daf011b0d1d06812102269212b1918ee6654e168901da899073f5df9279482059697c4163be93a910cfffffffff0f1d2d7c8ccf22289ce7a3a48f9919dccd264b0b953368d226862e9fcbab38d0050000006b483045022100f9cdce1475c4df5c0795fba49cff34c23f57e9b4d387a8a21b287bf251d85e270220194464f637f1f17f9cc1cd391f6144f026776e2aea9d6853b78c054b667efedf812103d72b46a6cd72a730f03157c34ebb663302188190b298bcab5c2b4caf13a3cf73ffffffff2ae79a108b1c9cf45c8f162b64fef1167f7e54dc8a1d72416c1417a4a000d3e0030000006b483045022100c48e9c3d36f2e3418b6dc6f1d132e481592a252f2107822c5d00e5c7be6a90db0220677d1be73b3e6b07d7ecb9c2ba01fa09df47e24593b848149d8ef1d0701821e58121023907676f06a3f4ce43434a77412fe5c8dc9984516f40eb0941875bf4fb6accafffffffff2ae79a108b1c9cf45c8f162b64fef1167f7e54dc8a1d72416c1417a4a000d3e00a0000006b483045022100c1294e05655237a6859ac89b7f5fd2e4c62fce21c889006b88da58d1949d3d08022057c6b2c7317f24e8e1726666bb7da7abc0c5fcb7346092cfe1a9ed1072aeea4f812103b2a613f3a31fa49c4b01f6312e6c591d59e94e0cf91760e74d3e2bde56103b37ffffffff2ae79a108b1c9cf45c8f162b64fef1167f7e54dc8a1d72416c1417a4a000d3e00b0000006a47304402201fc60955855bfbb70ee6a68eebd0c15cd9d87ebde474e6db81fb56cd6a6add2502200d2df8522c6b277e938a0819ed23181c4fd51c8d7ed65f140aaf6194f23615ea81210224fab0f1799c5cf7c64205bcc16d128e1c8aa81c576344dc8bb63f6fe9069a3dffffffff2ae79a108b1c9cf45c8f162b64fef1167f7e54dc8a1d72416c1417a4a000d3e00c0000006b483045022100c0ef1d73137d26f7b71adf00832a24edef5ddd6f85d6e8ca824201cbb4fad648022030eaa45cc64be7b9019eac5f114e2d1a1376fe627b3b11f5f0efa9ed30198d268121024cc8baac906f49deccd92576b21daeb3d8a40700d43eaf4a857d3194c318f8ebffffffff2ae79a108b1c9cf45c8f162b64fef1167f7e54dc8a1d72416c1417a4a000d3e00d0000006b483045022100a4d3614a9846af05546a975289bfb10405cbc59be2ab9397c8f9f2e0bbd177fd022018e6a013976ab4d606e2b58e5babe777d40572281bb52d4dc343b0fe71d3efe581210301d743a004b33060e2f99b4564398b1858c041df17be969266089cdf633dd019ffffffff2ae79a108b1c9cf45c8f162b64fef1167f7e54dc8a1d72416c1417a4a000d3e0100000006b483045022100daaa101214083697cd98987bad50c78d4d18bfcfb163fab96c384218258719b90220439ec86ef95023d94c57309062fc429e8713b9c07739833ec3f6c16b34c96ec6812103c22034a42cb8474fd1340010d9a244ee5a8ed89e5d52e06fff818fc71009ee31ffffffff2ae79a108b1c9cf45c8f162b64fef1167f7e54dc8a1d72416c1417a4a000d3e0110000006b483045022100f079bd18dabe3798f73c42323fed3ae2dfe6fd2d70a223b4019ce0fd3234a80e02207e9183074b1887acf24cf02d7da2395879b760260c9ef6da8db6133b78f7e52f812102d8f6d1eec4cc427084fae0257afb50b0631a005f359887a7767abb4e1d22a304ffffffff0f4a420f00000000001976a914303825be8d39e8ac8b5c3747346a2f154bba1f6988ac4a420f00000000001976a914369b068d835682c440d2d6fcac7b74d01049a3db88ac4a420f00000000001976a91442940bbd715657d6ea4df5cb532736fd9890207588ac4a420f00000000001976a9145be564157057318f6a9034ac0ec7e559c6975c6688ac4a420f00000000001976a914631beef2c0e74a121aff376071c73fa345f13c2688ac4a420f00000000001976a9147bb32504c0a6aa13c60f88b0c5558735aa4a1e3488ac4a420f00000000001976a9148a0d92897375b9bce279f90fe4b4bdad095239e088ac4a420f00000000001976a9148d6be585d3fd79031dcd2470b24a8e1a8a05554988ac4a420f00000000001976a9149324b7c8d599412d5d8b0a4f701f37866ae98ccf88ac4a420f00000000001976a914944e0e9983d5f7a4572b4b997c7d12a4582041ef88ac4a420f00000000001976a91494d271e1f91a38d4359ff512050adab8e489e32788ac4a420f00000000001976a914aef69f3a7c853e39ce55fff48e027775d575d35f88ac4a420f00000000001976a914d72c5044ee2b3f9695b3c1ae89ccad86dd039f5488ac4a420f00000000001976a914e3b2fefac687f49d1a1afacb4ce9ba559df130f888ac4a420f00000000001976a914eec21ec2b446b80ad26d6a430d939a34e6e6ef2b88ac0000000001000000025fe92dcd72a7b15dde26329d98a657e1f122d60cf65babcc562685547921f4d6000000006a47304402206eec9dc4d03d367b8ca6447b622a3e9def5ce87488ffab3ab4526577a9fe5d21022004e027bf4abd2e13327b47835642916ac8dba64d76cd155daf2e6d6fbc92a7510121036dab9431ab027f8f27a8afdccd7c325807f5dadc408041b208799d0ea1d7aac9ffffffff9d09d250617a42ed2dbd9d4248e0850b44123e512bb27ba6c14224f2714fb824000000006a47304402201eaee7aa8eaf7520edd640da02dbee31acebbcaeaa803b1a7b4f934fe8a9f25f022060c1bdc2c60d4e0693bffa46ba7d8ea180fc1bdbfbc7d2831c2bb5c414c91df80121036dab9431ab027f8f27a8afdccd7c325807f5dadc408041b208799d0ea1d7aac9ffffffff01a4140100000000001976a914032c595aab30cdd51513421e021f489395e5982c88ac000000000100000001a35f4e14434d15329eab57c8322a3aca0d561f54b92bb690a1b7f340267323a8010000006a47304402207e4f0d7fb5226ec8d952dec7ad81cb3fb7fb4018e2cd874050b5f25d10acbb0602200bf2161242db9c55cf65a5ebd4c357f5d1b5e8bc40e2cef5b2637332161cb68c012102b8b39af2344139e306d7bdd4035ef7c4ce4424e9d30b03d948489d2f83d1a9d8ffffffff01f9241500000000001976a91462c3e7e888ff7bd25a1e3c6f763015c6d70fb2d588ac000000000100000006b82c1ddcf9562bc5b43342a180ca11fd431f951a5a4e0499a378171a3a39af58010000006b483045022100acb0aabbf3a329d446f723b9e654404d1a1cb9eb04452ed5592841feae9f445d02202f6eb1f738bd871aed6261be005b74a289c3ffba66dd1eb98b04d386a7279e65012102bc261f0c8ee83fac08dba82b52cb9fdf47ebee27ec87d8108ffc2f35be720270ffffffff786f8d87fb1c039224d5be25282f00ca1c9030f763ca768ce38ccfe873369bd8540200006a47304402200145424154c88d567f77cabacbec1eb21eeac0606f80dbc2af33170e0b3e96bb0220487964b8a9ab85050ce16dfeaabca72ebfcc973b4ad05689d87c175c8625da18012103bf8e8f949ba8d09cf1158dc183e44455d891cb6b40dc1cab01ce6c8c6b5f3592ffffffff44bb9ec6d133ac519c67c46778b1bee3880cf9913e407384794e28dced16726b1e0500006b483045022100a4ff79f0742a50beff1a0de2f7cb04297fc1f159f38a651c94c340549db8af6f0220559acf3f1776a5d4ab089c7abea7b076399ac08bbe15f823ec7523a7d536526e012103bf8e8f949ba8d09cf1158dc183e44455d891cb6b40dc1cab01ce6c8c6b5f3592ffffffffcbfa9e29b24f5255710009803cd91164ff22df503cbf1f71ca4d5151d3bf2f15100200006a4730440220020a8914ee90058b871e14c5d59b91b9677e1a3a44749f080a22f79c24b56c88022047f3360cd5df33195e63508b133744dd0211e0c5461a39f7a87045ae313ac04e012103bf8e8f949ba8d09cf1158dc183e44455d891cb6b40dc1cab01ce6c8c6b5f3592ffffffff56d403dcc6be12c2f259ffc9555175cba4195b55c37628d50cc4de755a3a1efe240500006a47304402200fe67a803dc6fcd37f381c750e0f470251b169213a8fc7ca26432fea94bdd42302205f2bf5074675849c67ea4ca4b4f857fdbdf7066725d69361cae9854cfd7f60dc012103bf8e8f949ba8d09cf1158dc183e44455d891cb6b40dc1cab01ce6c8c6b5f3592ffffffffd083ba07c1b83c2356a855084477c2eb84f5f8576df8f021a83620c8b7466859240600006b483045022100d8ed277b5f9b7f36d19e8126a72b4cb1d0e6297e6e957cc95bd1089983352d2102201eefecc27e550cd4b79ed9300668511e7d60fe3dbda1cf4dd33226aa7dc2ecc9012103bf8e8f949ba8d09cf1158dc183e44455d891cb6b40dc1cab01ce6c8c6b5f3592ffffffff02ee516400000000001976a9146a2e21c457e17af47916fb885e4d610e72aa4c7c88acbab90a00000000001976a914ab4abad7e5899fff6bbe7bcf8e1386be8c7ff41b88ac00000000010000000163a77369c3dd6df032c759f34a52ee54d9b7c845074d9d788d740f1445fb4cb7010000006a47304402201b545ebeb6a2c24f76daf51559cfaa66788dd2ebc3e6af4a3b3b81a2e6b34d3a022058b70ffd9dab97a9a436af8af44e48029e1eba066eda50d93f57a046359aaebb01210306b480ab4141def407f094cbe0cbcf96e37483b815691e7cd000c600dbb950cffeffffff0298b70000000000001976a9140fac31a84cf64d36f055bfc25b3a2bdf01761b9a88ac42601600000000001976a9143fe0a0622557d177981110c7723a1eed3f5cb17888ac2bda0c000100000001732e12d00d1125d001004778f390fcb51c732f5edb3f57c19d3fecd5ff2a7f99160000006a473044022061bb959f498bca251e26dc6f85cf22eb05d46a5d7a6b0d0b47628c09b7a900d70220163fedb8b616cae3078bbe96f9143c2fcf8bb7cac3448eb59c280a337ebd4fe80121024951cabffe12d7655cea5bd8b0707bc5f088362c257e82090ca588bd90d67082feffffff124a420f00000000001976a9141d79c62c95bc98d96a14a20ad58664f9d2c7b4de88ac4a420f00000000001976a91431ad73306e40d9e234ac6eef3d87fe461cede1cc88ac4a420f00000000001976a914379516aa1d4fc0ecf74c9452686a734195a796bf88ac4a420f00000000001976a9145e9a5b81b361d961dfbaa3ce71cde472ea6bea0e88ac4a420f00000000001976a914653291051dc2364033eb30bb2a7703be5735909388ac4a420f00000000001976a9146d4769fe38bc9eacb9c06f8740afedbfa5a032fc88ac4a420f00000000001976a9146dac9636fdb0be6917798a65b4eb4c163737697788ac4a420f00000000001976a914941c34dab2a9e08f617d01ff48270a04705c0eb788ac4a420f00000000001976a914a52128d41348fe0b96733a5bf15bdf40638c4c9e88ac4a420f00000000001976a914b6ebe05b55d7ffb5dfc665b7bcdef05f6bd485df88ac4a420f00000000001976a914cffa534f701634300c8697cced894fb5080ab9cf88ac63e67600000000001976a914023b850ea817581570236061aaacd9ceeddd05b088ace4969800000000001976a9141c7af792530bcc989c31ce4450ac6979d9455bec88ace4969800000000001976a914abddfa294b9a8d2c65f7a8609f8cf4d06c5c50e688ace4969800000000001976a914b343212f303bcf937a5b8b65989bbcbf5948de9288ace4969800000000001976a914b6e477f3ed96f2a4577496eca99b64bd5f6d982988ace4969800000000001976a914d76388176e4e7733d8dcbd17ad07df7e7c58c2f888ace4969800000000001976a914eb56475b9512ae143944a17be37e7864bba035c288ac2bda0c00010000000107bf877182315e336aed7e0a14f4a90ed17ee5d4af3d4d5e72eb8038210ff14d000000006a47304402202e7807ec9f43a5418f8f8b527517d3fc7282accce0b60555af718b890f8fe4e502200ded20f0ec5440fffab883c70d065828d81ee535c0c0cf424bae74459611f5c4012102464bef1617c97a903a6d16190a29c6ada56ba9078eaa419c1eb8ae4c5e14ea5ffeffffff0555120f00000000001976a914170c37d2ce1343b3cc4375d25aa411d22a7d606288ac4a420f00000000001976a9143314621102608f494930da92954a05d52bdc3ace88ac4a420f00000000001976a91437fe286d1114fb0800de27debf0a11abd5290a6a88ac4a420f00000000001976a9143864210a3649da98661c34ff74ce098fc94db81988ac4a420f00000000001976a914d54df858b2dde443d2cf633d975df201e395d43188ac2bda0c00";

        String block842285hash = "000000000000001b7c5666cbda73912a5bcff8fc179fdd421fc9bfe7b7e7be73";
        String block842284hash = "000000000000000cdf5cc24c3beb0669b31e942d1301e07b53d6f0c7db10860d";

        Sha256Hash firstBlockHash = Sha256Hash.wrap(block842285hash);
        Block firstBlock = new Block(TESTNET, Utils.HEX.decode(block842285));
        assertEquals(firstBlockHash, firstBlock.getHash());
        assertEquals(12, firstBlock.getTransactions().size());
        assertEquals(1, firstBlock.getTransactions().get(0).getVersion());

        Sha256Hash secondBlockHash = Sha256Hash.wrap(block842284hash);
        Block secondBlock = new Block(TESTNET, Utils.HEX.decode(block842284));
        assertEquals(secondBlockHash, secondBlock.getHash());
        assertEquals(14, secondBlock.getTransactions().size());

        long version = secondBlock.getTransactions().get(0).getVersion();
        assertEquals(536870912, version);
    }
}
