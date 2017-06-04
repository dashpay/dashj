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
import org.bitcoinj.params.TestNet2Params;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.*;

public class BlockTest {
    private static final NetworkParameters PARAMS = TestNet2Params.get();

    public static final byte[] blockBytes;

    static {
        // Block 000000000fba0622132b6acd887021db720c541590d0408bc3ae525277fb2636; height of 187943
        // One with lots of transactions in, so a good test of the merkle tree hashing.
        blockBytes = HEX.decode("00000020157dc13d40818f91eddb8d14dd2e8dcb43f2c4dd2935d4065238788d00000000ec269fd49d1d0c5613f918a686f71ddb3a8f92c7b85b385f3dfed4f0c5cd0a392b92f9584ee1001d059cf2910301000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4c0327de02042b92f95808fabe6d6dec5faa57bc58a0f6aeb6555f0900000092db0a8600000000ac88bd54c554c6e701000000000000006ffffff3710100000d2f6e6f64655374726174756d2f00000000022c460e43000000001976a914cf5783cdb4347cf4d6b4026fbdd895e460acbc5088ac2c460e43000000001976a91440312e1aeebc7e2e99047125d78f19f51df6180188ac0000000001000000032ec44449143edfba5bc741a899b22c9ea1797750195000cfc9a8317aca5b858a000000006b483045022100f04f5686febb8d9df0af2f8fee92e74048dd79ec6e44acb7c05a16b635c9320502207aed80d5b137463a6c0e5b2af859ce3e5475945ad167072f1216dccbd16ffee00121038ecdf3413d4e7e2797cc1b038f9ba9e42b83f261e64d9115569e46d741b2da24feffffffd9bb73a65adeeab77186fe0d33917a0661a6d6da7e94a64486fae91135adf71e000000006a473044022055ba9a7106dc28e90c72c2d41f937528ea6aacb371f79ca3a18f9305236b2c2d0220558d833028626f16ad93acfcb990ca68575d1874befc85a185ef78a3c11ccd5e0121038ecdf3413d4e7e2797cc1b038f9ba9e42b83f261e64d9115569e46d741b2da24feffffffdca428fda53beb894c01c398b7a92f744b60ae5c0e01ea29f985edf6305d322e000000006a47304402205edcea624377076f2db313346cc5a7f364594912cfd6a695e7beb77834c0ffc2022004d67d23b7c8b825d17c3f5a37cb57cee4fd5ff121c080b674d4d41decb52717012103353cc80209a47dbf0f434385bb293b1f08b92e2dd3917c51483e5c846ad77f71feffffff02c70a8d05000000001976a914c88b91c4e0e738d082f29fd784ff11de29eb244b88acf5741c86000000001976a914dc3e0793134b081145ec0c67a9c72a7b297df27c88ac25de0200010000000229035c9d173a3581cd3e751e30803d8007178debee5c43ab0667f22d3d1d85b5000000006a47304402205208c78c617e2847bde13b7d09c36b14aa048a55972f9c8b49f657ea82d1f5d602202fac89274602079cecd7bbe7cfd577e64084d68734531a56c2db0374f93ce0bb01210342d0a0181a33451fe901dec2a01ef22fc5ad570c5de507cfbf70417777fa3374feffffffdd2b4139ad014967454591a796045b2f02259def4d3c5a26c2c32e4f3d786f68000000006a47304402205f49906b990f0a35c5349de8453ebc7c70f3a59259cc524e1508c97a2eba09f00220745e5c0eb38421b9b7ac4e9c181bc3bb0d96b9c6eab26af9ea7ba5852664391e0121038ecdf3413d4e7e2797cc1b038f9ba9e42b83f261e64d9115569e46d741b2da24feffffff024b4a0f00000000001976a914f32788579f94cab7974f8119e7383c3d14357c5388ac40230e43000000001976a914dc3e0793134b081145ec0c67a9c72a7b297df27c88ac26de0200");
    }

    @Before
    public void setUp() throws Exception {
        Context context = new Context(PARAMS);
    }

    @Test
    public void testWork() throws Exception {
        BigInteger work = PARAMS.getGenesisBlock().getWork();
        // This number is printed by Dash Core at startup as the calculated value of chainWork on testnet:
        //
        // 2017-04-22 19:20:20 UpdateTip: new best=00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c  height=0  log2_work=20.000022  tx=1  date=2014-01-25 16:10:06 progress=0.000002
        // log base 2 of 1048592 is 20.000022
        assertEquals(BigInteger.valueOf(1048592), work);
    }

    @Test
    public void testBlockVerification() throws Exception {
        Block block = PARAMS.getDefaultSerializer().makeBlock(blockBytes);
        block.verify(Block.BLOCK_HEIGHT_GENESIS, EnumSet.noneOf(Block.VerifyFlag.class));
        assertEquals("000000000fba0622132b6acd887021db720c541590d0408bc3ae525277fb2636", block.getHashAsString());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testDate() throws Exception {
        Block block = PARAMS.getDefaultSerializer().makeBlock(blockBytes);
        assertEquals("21 Apr 2017 05:01:31 GMT", block.getTime().toGMTString());
    }

    @Test
    public void testProofOfWork() throws Exception {
        // This context accepts any difficulty target.
        NetworkParameters params = UnitTestParams.get();
        Block block = params.getDefaultSerializer().makeBlock(blockBytes);
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
        Block block = PARAMS.getDefaultSerializer().makeBlock(blockBytes);
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
        Block block = PARAMS.getDefaultSerializer().makeBlock(blockBytes);
        Block header = block.cloneAsHeader();
        Block reparsed = PARAMS.getDefaultSerializer().makeBlock(header.bitcoinSerialize());
        assertEquals(reparsed, header);
    }

    @Test
    public void testBitcoinSerialization() throws Exception {
        // We have to be able to reserialize everything exactly as we found it for hashing to work. This test also
        // proves that transaction serialization works, along with all its subobjects like scripts and in/outpoints.
        //
        // NB: This tests the bitcoin serialization protocol.
        Block block = PARAMS.getDefaultSerializer().makeBlock(blockBytes);
        assertTrue(Arrays.equals(blockBytes, block.bitcoinSerialize()));
    }
    
    @Test
    public void testUpdateLength() {
        NetworkParameters params = UnitTestParams.get();
        Block block = params.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, new ECKey().getPubKey(), Block.BLOCK_HEIGHT_GENESIS);
        assertEquals(block.bitcoinSerialize().length, block.length);
        final int origBlockLen = block.length;
        Transaction tx = new Transaction(params);
        // this is broken until the transaction has > 1 input + output (which is required anyway...)
        //assertTrue(tx.length == tx.bitcoinSerialize().length && tx.length == 8);
        byte[] outputScript = new byte[10];
        Arrays.fill(outputScript, (byte) ScriptOpCodes.OP_FALSE);
        tx.addOutput(new TransactionOutput(params, null, Coin.SATOSHI, outputScript));
        tx.addInput(new TransactionInput(params, null, new byte[] {(byte) ScriptOpCodes.OP_FALSE},
                new TransactionOutPoint(params, 0, Sha256Hash.of(new byte[] { 1 }))));
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
        block.getTransactions().get(1).addInput(new TransactionInput(params, null, new byte[] {(byte) ScriptOpCodes.OP_FALSE},
                new TransactionOutPoint(params, 0, Sha256Hash.of(new byte[] { 1 }))));
        assertEquals(block.length, origBlockLen + tx.length);
        assertEquals(tx.length, origTxLength + 41); // - 1 + 40 + 1 + 1
    }

    @Test
    public void testCoinbaseHeightTestnet() throws Exception {
        // Testnet block 21066 (hash 0000000004053156021d8e42459d284220a7f6e087bf78f30179c3703ca4eefa)
        // contains a coinbase transaction whose height is two bytes, which is
        // shorter than we see in most other cases.

        //Block block = TestNet3Params.get().getDefaultSerializer().makeBlock(
        //    ByteStreams.toByteArray(getClass().getResourceAsStream("block_testnet21066.dat")));

        Block block = TestNet3Params.get().getDefaultSerializer().makeBlock(blockBytes);
        // Check block.
        assertEquals("000000000fba0622132b6acd887021db720c541590d0408bc3ae525277fb2636", block.getHashAsString());
        block.verify(187943, EnumSet.of(Block.VerifyFlag.HEIGHT_IN_COINBASE));

        // Testnet block 32768 (hash 000000007590ba495b58338a5806c2b6f10af921a70dbd814e0da3c6957c0c03)
        // contains a coinbase transaction whose height is three bytes, but could
        // fit in two bytes. This test primarily ensures script encoding checks
        // are applied correctly.

        //block = TestNet3Params.get().getDefaultSerializer().makeBlock(
        //    ByteStreams.toByteArray(getClass().getResourceAsStream("block_testnet32768.dat")));
        block = TestNet3Params.get().getDefaultSerializer().makeBlock(HEX.decode("04000000ad7226a87e8c16a4de493320a6f55535903b13dae0689bc6a76255ad0400000068208963dae191aa15c11dd82adfd7ba7184716b9acc649805904ec9a9d564079a3036578798311d3909b8000101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff050288130101ffffffff0100743ba40b0000002321033de1afba2a2aa9f88f06912a4b51d386af3142e89f3b1a69741cb62f3dd47cdaac00000000"));
        // Check block.
        assertEquals("0000001d3f1f66dd3b276f7fddaae344973e9ddf1179d904b0798bcfc503cd94", block.getHashAsString());
        block.verify(5000, EnumSet.of(Block.VerifyFlag.HEIGHT_IN_COINBASE));
    }

    /*@Test
    public void testReceiveCoinbaseTransaction() throws Exception {
        // Block 169482 (hash 0000000000000756935f1ee9d5987857b604046f846d3df56d024cdb5f368665)
        // contains coinbase transactions that are mining pool shares.
        // The private key MINERS_KEY is used to check transactions are received by a wallet correctly.

        // The address for this private key is 1GqtGtn4fctXuKxsVzRPSLmYWN1YioLi9y.
        final String MINING_PRIVATE_KEY = "5JDxPrBRghF1EvSBjDigywqfmAjpHPmTJxYtQTYJxJRHLLQA4mG";

        final long BLOCK_NONCE = 3973947400L;
        final Coin BALANCE_AFTER_BLOCK = Coin.valueOf(22223642);
        final NetworkParameters PARAMS = MainNetParams.get();

        Block block169482 = PARAMS.getDefaultSerializer().makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block169482.dat")));

        // Check block.
        assertNotNull(block169482);
        block169482.verify(169482, EnumSet.noneOf(Block.VerifyFlag.class));
        assertEquals(BLOCK_NONCE, block169482.getNonce());

        StoredBlock storedBlock = new StoredBlock(block169482, BigInteger.ONE, 169482); // Nonsense work - not used in test.

        // Create a wallet contain the miner's key that receives a spend from a coinbase.
        ECKey miningKey = DumpedPrivateKey.fromBase58(PARAMS, MINING_PRIVATE_KEY).getKey();
        assertNotNull(miningKey);
        Context context = new Context(PARAMS);
        Wallet wallet = new Wallet(context);
        wallet.importKey(miningKey);

        // Initial balance should be zero by construction.
        assertEquals(Coin.ZERO, wallet.getBalance());

        // Give the wallet the first transaction in the block - this is the coinbase tx.
        List<Transaction> transactions = block169482.getTransactions();
        assertNotNull(transactions);
        wallet.receiveFromBlock(transactions.get(0), storedBlock, NewBlockType.BEST_CHAIN, 0);

        // Coinbase transaction should have been received successfully but be unavailable to spend (too young).
        assertEquals(BALANCE_AFTER_BLOCK, wallet.getBalance(BalanceType.ESTIMATED));
        assertEquals(Coin.ZERO, wallet.getBalance(BalanceType.AVAILABLE));
    }*/

    @Test
    public void isBIPs() throws Exception {
        final MainNetParams mainnet = MainNetParams.get();
        final Block genesis = mainnet.getGenesisBlock();
        assertFalse(genesis.isBIP34());
        assertFalse(genesis.isBIP66());
        assertFalse(genesis.isBIP65());

        // 227835/00000000000001aa077d7aa84c532a4d69bdbff519609d1da0835261b7a74eb6: last version 1 block
        final Block block227835 = mainnet.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block227835.dat")));
        assertFalse(block227835.isBIP34());
        assertFalse(block227835.isBIP66());
        assertFalse(block227835.isBIP65());

        // 227836/00000000000000d0dfd4c9d588d325dce4f32c1b31b7c0064cba7025a9b9adcc: version 2 block
        final Block block227836 = mainnet.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block227836.dat")));
        assertTrue(block227836.isBIP34());
        assertFalse(block227836.isBIP66());
        assertFalse(block227836.isBIP65());

        // 363703/0000000000000000011b2a4cb91b63886ffe0d2263fd17ac5a9b902a219e0a14: version 3 block
        final Block block363703 = mainnet.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block363703.dat")));
        assertTrue(block363703.isBIP34());
        assertTrue(block363703.isBIP66());
        assertFalse(block363703.isBIP65());

        // 383616/00000000000000000aab6a2b34e979b09ca185584bd1aecf204f24d150ff55e9: version 4 block
        final Block block383616 = mainnet.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block383616.dat")));
        assertTrue(block383616.isBIP34());
        assertTrue(block383616.isBIP66());
        assertTrue(block383616.isBIP65());

        // 370661/00000000000000001416a613602d73bbe5c79170fd8f39d509896b829cf9021e: voted for BIP101
        final Block block370661 = mainnet.getDefaultSerializer()
                .makeBlock(ByteStreams.toByteArray(getClass().getResourceAsStream("block370661.dat")));
        assertTrue(block370661.isBIP34());
        assertTrue(block370661.isBIP66());
        assertTrue(block370661.isBIP65());
    }
}
