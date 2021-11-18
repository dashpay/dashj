/*
 * Copyright 2014 Google Inc.
 * Copyright 2016 Andreas Schildbach
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

import com.google.common.primitives.UnsignedBytes;
import org.bitcoinj.core.TransactionConfidence.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.*;
import org.bitcoinj.script.*;
import org.bitcoinj.testing.*;
import org.easymock.*;
import org.junit.*;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;
import static org.bitcoinj.core.Utils.HEX;

import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Just check the Transaction.verify() method. Most methods that have complicated logic in Transaction are tested
 * elsewhere, e.g. signing and hashing are well exercised by the wallet tests, the full block chain tests and so on.
 * The verify method is also exercised by the full block chain tests, but it can also be used by API users alone,
 * so we make sure to cover it here as well.
 */
public class TransactionTest {
    private static final NetworkParameters UNITTEST = UnitTestParams.get();
    private static final NetworkParameters TESTNET = TestNet3Params.get();
    private static final Address ADDRESS = Address.fromKey(UNITTEST, new ECKey());

    private Transaction tx;

    @Before
    public void setUp() throws Exception {
        Context context = new Context(UNITTEST);
        tx = FakeTxBuilder.createFakeTx(UNITTEST);
    }

    @Test(expected = VerificationException.EmptyInputsOrOutputs.class)
    public void emptyOutputs() throws Exception {
        tx.clearOutputs();
        tx.verify();
    }

    @Test(expected = VerificationException.EmptyInputsOrOutputs.class)
    public void emptyInputs() throws Exception {
        tx.clearInputs();
        tx.verify();
    }

    @Test(expected = VerificationException.LargerThanMaxBlockSize.class)
    public void tooHuge() throws Exception {
        tx.getInput(0).setScriptBytes(new byte[Block.MAX_BLOCK_SIZE]);
        tx.verify();
    }

    @Test(expected = VerificationException.DuplicatedOutPoint.class)
    public void duplicateOutPoint() throws Exception {
        TransactionInput input = tx.getInput(0);
        input.setScriptBytes(new byte[1]);
        tx.addInput(input.duplicateDetached());
        tx.verify();
    }

    @Test(expected = VerificationException.NegativeValueOutput.class)
    public void negativeOutput() throws Exception {
        tx.getOutput(0).setValue(Coin.NEGATIVE_SATOSHI);
        tx.verify();
    }

    @Test(expected = VerificationException.ExcessiveValue.class)
    public void exceedsMaxMoney2() throws Exception {
        Coin half = UNITTEST.getMaxMoney().divide(2).add(Coin.SATOSHI);
        tx.getOutput(0).setValue(half);
        tx.addOutput(half, ADDRESS);
        tx.verify();
    }

    @Test(expected = VerificationException.UnexpectedCoinbaseInput.class)
    public void coinbaseInputInNonCoinbaseTX() throws Exception {
        tx.addInput(Sha256Hash.ZERO_HASH, 0xFFFFFFFFL, new ScriptBuilder().data(new byte[10]).build());
        tx.verify();
    }

    @Test(expected = VerificationException.CoinbaseScriptSizeOutOfRange.class)
    public void coinbaseScriptSigTooSmall() throws Exception {
        tx.clearInputs();
        tx.addInput(Sha256Hash.ZERO_HASH, 0xFFFFFFFFL, new ScriptBuilder().build());
        tx.verify();
    }

    @Test(expected = VerificationException.CoinbaseScriptSizeOutOfRange.class)
    public void coinbaseScriptSigTooLarge() throws Exception {
        tx.clearInputs();
        TransactionInput input = tx.addInput(Sha256Hash.ZERO_HASH, 0xFFFFFFFFL, new ScriptBuilder().data(new byte[99]).build());
        assertEquals(101, input.getScriptBytes().length);
        tx.verify();
    }

    @Test
    public void testEstimatedLockTime_WhenParameterSignifiesBlockHeight() {
        int TEST_LOCK_TIME = 20;
        Date now = Calendar.getInstance().getTime();

        BlockChain mockBlockChain = createMock(BlockChain.class);
        EasyMock.expect(mockBlockChain.estimateBlockTime(TEST_LOCK_TIME)).andReturn(now);

        Transaction tx = FakeTxBuilder.createFakeTx(UNITTEST);
        tx.setLockTime(TEST_LOCK_TIME); // less than five hundred million

        replay(mockBlockChain);

        assertEquals(tx.estimateLockTime(mockBlockChain), now);
    }

    @Test
    public void testOptimalEncodingMessageSize() {
        Transaction tx = new Transaction(UNITTEST);

        int length = tx.length;

        // add basic transaction input, check the length
        tx.addOutput(new TransactionOutput(UNITTEST, null, Coin.COIN, ADDRESS));
        length += getCombinedLength(tx.getOutputs());

        // add basic output, check the length
        length += getCombinedLength(tx.getInputs());

        // optimal encoding size should equal the length we just calculated
        assertEquals(tx.getOptimalEncodingMessageSize(), length);
    }

    private int getCombinedLength(List<? extends Message> list) {
        int sumOfAllMsgSizes = 0;
        for (Message m: list) { sumOfAllMsgSizes += m.getMessageSize() + 1; }
        return sumOfAllMsgSizes;
    }

    @Test
    public void testIsMatureReturnsFalseIfTransactionIsCoinbaseAndConfidenceTypeIsNotEqualToBuilding() {
        Transaction tx = FakeTxBuilder.createFakeCoinbaseTx(UNITTEST);

        tx.getConfidence().setConfidenceType(ConfidenceType.UNKNOWN);
        assertEquals(tx.isMature(), false);

        tx.getConfidence().setConfidenceType(ConfidenceType.PENDING);
        assertEquals(tx.isMature(), false);

        tx.getConfidence().setConfidenceType(ConfidenceType.DEAD);
        assertEquals(tx.isMature(), false);
    }

    @Test
    public void testCLTVPaymentChannelTransactionSpending() {
        BigInteger time = BigInteger.valueOf(20);

        ECKey from = new ECKey(), to = new ECKey(), incorrect = new ECKey();
        Script outputScript = ScriptBuilder.createCLTVPaymentChannelOutput(time, from, to);

        Transaction tx = new Transaction(UNITTEST);
        tx.addInput(new TransactionInput(UNITTEST, tx, new byte[] {}));
        tx.getInput(0).setSequenceNumber(0);
        tx.setLockTime(time.subtract(BigInteger.ONE).longValue());
        TransactionSignature fromSig =
                tx.calculateSignature(
                        0,
                        from,
                        outputScript,
                        Transaction.SigHash.SINGLE,
                        false);
        TransactionSignature toSig =
                tx.calculateSignature(
                        0,
                        to,
                        outputScript,
                        Transaction.SigHash.SINGLE,
                        false);
        TransactionSignature incorrectSig =
                tx.calculateSignature(
                        0,
                        incorrect,
                        outputScript,
                        Transaction.SigHash.SINGLE,
                        false);
        Script scriptSig =
                ScriptBuilder.createCLTVPaymentChannelInput(fromSig, toSig);
        Script refundSig =
                ScriptBuilder.createCLTVPaymentChannelRefund(fromSig);
        Script invalidScriptSig1 =
                ScriptBuilder.createCLTVPaymentChannelInput(fromSig, incorrectSig);
        Script invalidScriptSig2 =
                ScriptBuilder.createCLTVPaymentChannelInput(incorrectSig, toSig);

        try {
            scriptSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
        } catch (ScriptException e) {
            e.printStackTrace();
            fail("Settle transaction failed to correctly spend the payment channel");
        }

        try {
            refundSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Refund passed before expiry");
        } catch (ScriptException e) { }
        try {
            invalidScriptSig1.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Invalid sig 1 passed");
        } catch (ScriptException e) { }
        try {
            invalidScriptSig2.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Invalid sig 2 passed");
        } catch (ScriptException e) { }
    }

    @Test
    public void testCLTVPaymentChannelTransactionRefund() {
        BigInteger time = BigInteger.valueOf(20);

        ECKey from = new ECKey(), to = new ECKey(), incorrect = new ECKey();
        Script outputScript = ScriptBuilder.createCLTVPaymentChannelOutput(time, from, to);

        Transaction tx = new Transaction(UNITTEST);
        tx.addInput(new TransactionInput(UNITTEST, tx, new byte[] {}));
        tx.getInput(0).setSequenceNumber(0);
        tx.setLockTime(time.add(BigInteger.ONE).longValue());
        TransactionSignature fromSig =
                tx.calculateSignature(
                        0,
                        from,
                        outputScript,
                        Transaction.SigHash.SINGLE,
                        false);
        TransactionSignature incorrectSig =
                tx.calculateSignature(
                        0,
                        incorrect,
                        outputScript,
                        Transaction.SigHash.SINGLE,
                        false);
        Script scriptSig =
                ScriptBuilder.createCLTVPaymentChannelRefund(fromSig);
        Script invalidScriptSig =
                ScriptBuilder.createCLTVPaymentChannelRefund(incorrectSig);

        try {
            scriptSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
        } catch (ScriptException e) {
            e.printStackTrace();
            fail("Refund failed to correctly spend the payment channel");
        }

        try {
            invalidScriptSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Invalid sig passed");
        } catch (ScriptException e) { }
    }

    @Test
    public void testToStringWhenLockTimeIsSpecifiedInBlockHeight() {
        Transaction tx = FakeTxBuilder.createFakeTx(UNITTEST);
        TransactionInput input = tx.getInput(0);
        input.setSequenceNumber(42);

        int TEST_LOCK_TIME = 20;
        tx.setLockTime(TEST_LOCK_TIME);

        Calendar cal = Calendar.getInstance();
        cal.set(2085, 10, 4, 17, 53, 21);
        cal.set(Calendar.MILLISECOND, 0);

        BlockChain mockBlockChain = createMock(BlockChain.class);
        EasyMock.expect(mockBlockChain.estimateBlockTime(TEST_LOCK_TIME)).andReturn(cal.getTime());

        replay(mockBlockChain);

        String str = tx.toString(mockBlockChain, null);

        assertEquals(str.contains("block " + TEST_LOCK_TIME), true);
        assertEquals(str.contains("estimated to be reached at"), true);
    }

    @Test
    public void testToStringWhenIteratingOverAnInputCatchesAnException() {
        Transaction tx = FakeTxBuilder.createFakeTx(UNITTEST);
        TransactionInput ti = new TransactionInput(UNITTEST, tx, new byte[0]) {
            @Override
            public Script getScriptSig() throws ScriptException {
                throw new ScriptException(ScriptError.SCRIPT_ERR_UNKNOWN_ERROR, "");
            }
        };

        tx.addInput(ti);
        assertEquals(tx.toString().contains("[exception: "), true);
    }

    @Test
    public void testToStringWhenThereAreZeroInputs() {
        Transaction tx = new Transaction(UNITTEST);
        assertEquals(tx.toString().contains("No inputs!"), true);
    }

    @Test
    public void testTheTXByHeightComparator() {
        Transaction tx1 = FakeTxBuilder.createFakeTx(UNITTEST);
        tx1.getConfidence().setAppearedAtChainHeight(1);

        Transaction tx2 = FakeTxBuilder.createFakeTx(UNITTEST);
        tx2.getConfidence().setAppearedAtChainHeight(2);

        Transaction tx3 = FakeTxBuilder.createFakeTx(UNITTEST);
        tx3.getConfidence().setAppearedAtChainHeight(3);

        SortedSet<Transaction> set = new TreeSet<>(Transaction.SORT_TX_BY_HEIGHT);
        set.add(tx2);
        set.add(tx1);
        set.add(tx3);

        Iterator<Transaction> iterator = set.iterator();

        assertEquals(tx1.equals(tx2), false);
        assertEquals(tx1.equals(tx3), false);
        assertEquals(tx1.equals(tx1), true);

        assertEquals(iterator.next().equals(tx3), true);
        assertEquals(iterator.next().equals(tx2), true);
        assertEquals(iterator.next().equals(tx1), true);
        assertEquals(iterator.hasNext(), false);
    }

    @Test(expected = ScriptException.class)
    public void testAddSignedInputThrowsExceptionWhenScriptIsNotToRawPubKeyAndIsNotToAddress() {
        ECKey key = new ECKey();
        Address addr = Address.fromKey(UNITTEST, key);
        Transaction fakeTx = FakeTxBuilder.createFakeTx(UNITTEST, Coin.COIN, addr);

        Transaction tx = new Transaction(UNITTEST);
        tx.addOutput(fakeTx.getOutput(0));

        Script script = ScriptBuilder.createOpReturnScript(new byte[0]);

        tx.addSignedInput(fakeTx.getOutput(0).getOutPointFor(), script, key);
    }

    @Test
    public void testPrioSizeCalc() throws Exception {
        Transaction tx1 = FakeTxBuilder.createFakeTx(UNITTEST, Coin.COIN, ADDRESS);
        int size1 = tx1.getMessageSize();
        int size2 = tx1.getMessageSizeForPriorityCalc();
        assertEquals(113, size1 - size2);
        tx1.getInput(0).setScriptSig(new Script(new byte[109]));
        assertEquals(78, tx1.getMessageSizeForPriorityCalc());
        tx1.getInput(0).setScriptSig(new Script(new byte[110]));
        assertEquals(78, tx1.getMessageSizeForPriorityCalc());
        tx1.getInput(0).setScriptSig(new Script(new byte[111]));
        assertEquals(79, tx1.getMessageSizeForPriorityCalc());
    }

    @Test
    public void testCoinbaseHeightCheck() throws VerificationException {
        // Coinbase transaction from block 300,000
        final byte[] transactionBytes = HEX.decode(
                "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4803e09304062f503253482f0403c86d53087ceca141295a00002e522cfabe6d6d7561cf262313da1144026c8f7a43e3899c44f6145f39a36507d36679a8b7006104000000000000000000000001c8704095000000001976a91480ad90d403581fa3bf46086a91b2d9d4125db6c188ac00000000");
        final int height = 300000;
        final Transaction transaction = UNITTEST.getDefaultSerializer().makeTransaction(transactionBytes);
        transaction.checkCoinBaseHeight(height);
    }

    /**
     * Test a coinbase transaction whose script has nonsense after the block height.
     * See https://github.com/bitcoinj/bitcoinj/issues/1097
     */
    @Test
    public void testCoinbaseHeightCheckWithDamagedScript() throws VerificationException {
        // Coinbase transaction from block 224,430
        final byte[] transactionBytes = HEX.decode(
            "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff3b03ae6c0300044bd7031a0400000000522cfabe6d6d00000000000000b7b8bf0100000068692066726f6d20706f6f6c7365727665726aac1eeeed88ffffffff01e0587597000000001976a91421c0d001728b3feaf115515b7c135e779e9f442f88ac00000000");
        final int height = 224430;
        final Transaction transaction = UNITTEST.getDefaultSerializer().makeTransaction(transactionBytes);
        transaction.checkCoinBaseHeight(height);
    }

    /**
     * Ensure that hashForSignature() doesn't modify a transaction's data, which could wreak multithreading havoc.
     */
    @Test
    public void testHashForSignatureThreadSafety() {
        Block genesis = UNITTEST.getGenesisBlock();
        Block block1 = genesis.createNextBlock(Address.fromKey(UNITTEST, new ECKey()),
                    genesis.getTransactions().get(0).getOutput(0).getOutPointFor());

        final Transaction tx = block1.getTransactions().get(1);
        final Sha256Hash txHash = tx.getTxId();
        final String txNormalizedHash = tx.hashForSignature(
                0,
                new byte[0],
                Transaction.SigHash.ALL.byteValue())
                .toString();

        for (int i = 0; i < 100; i++) {
            // ensure the transaction object itself was not modified; if it was, the hash will change
            assertEquals(txHash, tx.getTxId());
            new Thread(){
                public void run() {
                    assertEquals(
                            txNormalizedHash,
                            tx.hashForSignature(
                                    0,
                                    new byte[0],
                                    Transaction.SigHash.ALL.byteValue())
                                    .toString());
                }
            };
        }
    }

    @Test
    public void testVersionNumbers() {
        long type8192 = 536870912;

        assertEquals(0, Transaction.versionFromLegacyVersion(type8192));
        assertEquals(8192, Transaction.typeFromLegacyVersion(type8192));

        Transaction.Type type = Transaction.Type.fromValue(8192);

        assertEquals(Transaction.Type.TRANSACTION_UNKNOWN, type);  //type 8192 is invalid
        assertEquals(Transaction.Type.TRANSACTION_COINBASE, Transaction.Type.fromValue(5));
    }

    boolean verifyBIP69(Transaction tx, boolean throwException) {
        Sha256Hash lastOutpointHash = Sha256Hash.ZERO_HASH;
        long lastOutpointIndex = 0;

        for (TransactionInput input : tx.getInputs()) {
            TransactionOutPoint outPoint = input.getOutpoint();
            int compareInput = UnsignedBytes.lexicographicalComparator().compare(outPoint.getHash().getBytes(), lastOutpointHash.getBytes());
            if (compareInput < 0) {
                if (throwException) {
                    fail("Inputs not ordered according to BIP69: outpoint hash: " + outPoint.getHash() + " < " + lastOutpointHash);
                } else {
                    return false;
                }
            }
            if (compareInput == 0) {
                if (outPoint.getIndex() < lastOutpointIndex) {
                    if (throwException) {
                        fail("Inputs not ordered according to BIP69: outpoint hash");
                    } else {
                        return false;
                    }
                }
            }

            lastOutpointHash = outPoint.getHash();
            lastOutpointIndex = outPoint.getIndex();
        }

        Coin lastOutputValue = Coin.ZERO;
        byte [] lastOutputScript = null;
        for (TransactionOutput output : tx.getOutputs()) {
            Coin value = output.getValue();
            int compareInput = value.compareTo(lastOutputValue);
            if (compareInput < 0) {
                if (throwException) {
                    fail("Outputs not ordered according to BIP69: values");
                } else {
                    return false;
                }
            }
            if (compareInput == 0) {
                if (lastOutputScript != null && UnsignedBytes.lexicographicalComparator().compare(output.getScriptBytes(), lastOutputScript) < 0) {
                    if (throwException) {
                        fail("Outputs not ordered according to BIP69: script");
                    } else {
                        return false;
                    }
                }
            }

            lastOutputValue = output.getValue();
            lastOutputScript = output.getScriptBytes();
        }
        return true;
    }

    @Test
    public void transactionBIP69Test() {
        // new coinjoin transaction
        byte[] txData = HEX.decode("020000000b6a466448f3bf4cc4e8cfe1d459185c36bfe7227529e97b6e5aa6483af1cf7f04000000006a47304402207166456427b2ae2bdd9041bd35a38e68570d87f2df378fb4dbe4924715088d8d022029c57554cbcb0c278c1d1e0206f9c7e2ad4b462582c3bad3976891485ca526d781210276a99ea131df703b7bf51b5b16bf5e8d9759207a9b6fd9951cb9d403f67efb95ffffffff6a466448f3bf4cc4e8cfe1d459185c36bfe7227529e97b6e5aa6483af1cf7f04050000006b483045022100c75f7e870472a6c69014bc9250a75b808ec07d6d1a498e5216dee6a872b313fe0220163110d9b705fa2a550dd9d4d076027561d44a3a585718c7f68f539c4e5238268121032af84cf8a1e710758969a1a61e7d86d9029201a415564773f67eb6e545bbcdb9ffffffffaf40a1f64da31f70ee48267733edd12c1e9baa87e602aac4b396511bd7656b27070000006b48304502210088c85b6ad4c6a5a6946b206dd29cc90504cb31c38a2874ef9be3aaf440b099ee022024006e6d66a3c3052afe1d74efb94be4934ae845dd00f4863094dd31476d396c812102b4101110dd8ec37cdbe01f7f7e74142a255f9178c4e47aba16b2710f822c730bffffffffd91df75b097629a8eb03cbce9b30ebab918b0149959d7c2c8fa044cd83ffbb2f010000006b483045022100da84d2868cf4c64a1aa0fc9d310ea26f51b979fd3718962c9ac583b578914c0702207dd2657fd75fdda373cd59fd69f4ee7d9127e37353d48228d7d360ad6baac819812102e47f1449a9fae3d5ed3c50ef9903cdda5d271a7dc40e2f207e9b667de317bbf0ffffffffed7e81f492748ce54538cc6a031346a5f5603ec5f97e211615f0498922b7aa3e110000006b483045022100a961c4b603f879154381a8f073f7611489e6afcc27c00c23aa30dcf361dae2b2022034227fccca7498f7c29d1994f4465b5ac964909f3d5cb24ddd0fe386fe4c63cc8121037df1dd92ecb02b991c6dea5d83f1be1ed481250e4bb8affe5e2e5c7bb20f2df9ffffffffec8ebfbf3fee9f316e5516bb81ff292704badd6f6bc676e5d05a93bbc8ee703f040000006a47304402204852ce6598fc2d135cc292d39b52f4e76917448f35526cfe41edebf778235d0402207202267634a91bf238af13c492da079c7b53f6854d6e574bff8fe6431a87c67b81210251761ab779e155db34767796422c23e24b404917498c6cacaca69bdd2d2d66a1ffffffff0c30ede42fa05dd8ca3d4acdb8ff34dd9e58720486f89ebdbc84cbc921c7ce75040000006a47304402200f51081dc263f951b1ffd58a6dfeba918658b98e472cd3725f864f2400ed58c002202c0d6982be9825ac74b1b3ae394acf5ab1e4cbb124f10e8b34c94ef37e5b634b812103ed2c50edcacf9bde5e82f74cf7a7fe20ce408634dfdccea85f745514b46d5bb0ffffffffb19e9fddcf89253e133a2505aeacf86fa89dd8e228747d7bc46f434b76391c78040000006b483045022100addf450b6c263a0de9bc57b06e0b094e7abbe388ec8cb5379ee9828d643ff170022065f27cf340437c12aea408a621e8578569e7d16dbebbaa11ad53d7d8b23fc7fc812102b3e7835fc667b6f40b5193af29aefc45b60ecd666578b0365bbe55818bd63f78ffffffffc594a3e0e3ae68ca1320ec61faef669c1c545f8b867cad2f559e2dce3cbe0d8a080000006b483045022100b70cffc96da17890b58047bf71cbd2aa0d3c511ddfca00b81ea22fc016e42be3022054738f8f5ea41e96e34a1427a5eaf8b2e3b9f34e634b81c1a54e76822dfad7d48121039ad3aa3e0039f585d14ad6ad04285abb79bb74d3b723dc9b6bc25f9d2925facdffffffffe394bc6097d08eacd51813497dda7d8712a2db40aed7ed55fe500b790d7cfa8c010000006b483045022100f90c66f26b8a35fe955eb5a120ca2e79e0934b51359f55230790905fd6584c97022044a58678012faeb251b1a921ec6ce4309b241160f527fb1c92e59310581b229b81210346a58689a3a589af004292ff16b0a023102eddd627d11e920d59f84e731f26edfffffffff4f94e4fbb3e4452f3d2b65076e35d67d9798a203e65a6fcfcb5593962d1ccd9020000006b483045022100ac60238c221aba054abb788c89305e66b710f40cc5c793dac0f1af9817aa4fc7022008444d09b7fb66f999ab2572ae26411636bb10d3accff9011bb5bd7c53ee911c8121029102ee5bb81c1d00e3165ff3349a3af9744cf04a4527ccda8181c1fdc4b9d70bffffffff0ba1860100000000001976a91411911343b528368cb54e40d8aca7685fdf4db2b988aca1860100000000001976a91419a13842cfd66a4cb28d1b2438c0017a75db882e88aca1860100000000001976a9141bf608797113c382781966aaad4677014d45b95d88aca1860100000000001976a9142bf208a9153783c7abc82f59c22c706c4fcf256588aca1860100000000001976a9144f61fbb4f92f27b039c4b9c724ef7f1526be5d9288aca1860100000000001976a9146463b2517a8350b1f9c4121277beae5fef99a2b288aca1860100000000001976a9146490ff9c2f0650bd38e217e9579618e618e7f8ae88aca1860100000000001976a91490398c9c29fd5d019e9336166312b505d40b9d6488aca1860100000000001976a9149cfbf94a76e406443b068fbc0fcb668b1095182088aca1860100000000001976a9149f7ffe4b51614143bcf6065cf7263213c849fdb088aca1860100000000001976a914b1bd3b4892f488b28d2364395fd22775af840d6c88ac00000000");

        Transaction tx = new Transaction(TESTNET, txData);

        verifyBIP69(tx, true);
        tx.sortInputs();
        tx.sortOutputs();

        byte[] sortedTxData = tx.bitcoinSerialize();
        verifyBIP69(tx, true);
        assertArrayEquals(txData, sortedTxData);
    }

    @Test
    public void transactionBIP69FailureTest() {
        // old coinjoin transaction
        byte [] coinJoinTxData = HEX.decode("0100000012d55283595376a2f0e6514d96438f337fe4b61561483a4b1e371e24f4d3da20d50b0000006a47304402203ab23195048a6c658040c30744d2c915730503a0db824f6142fb1b6cf74aa8d7022037a878cd6b5fa98239d1e8e6d0ad44c4a3fcd13cb1fdb26b89f98d7b9eb22cde81210364ca9bfe84555bff2f8de4246596da3123d066d1f236371e9f2b8ad39ae137c7ffffffff3130e23792666e65b91b4acf0def3b458200ed9eb2be930a158939340d53b98a000000006b483045022100b15f8c8019aa8bd4daab22a67327e44f58bd67757e0225490633b113f024cf2d02205ed80aef4c2669f10d88d64dd485c13fcd9f795f3b40981100441802c500a719812103637fe9ea35d1149679b3b609c74a5f21a0f11d883a1dc6891563f0b4c04d6746ffffffffd55283595376a2f0e6514d96438f337fe4b61561483a4b1e371e24f4d3da20d50f0000006a4730440220039097a2c32d61427aae703fd8921ed8248bce973ebfcf14668b5634be7f0f33022052312508267fab4807d968ae4f8d84a8688e821381b93a076dd18e88f31ad24e81210302830a2bfb02fbc8bc2f1c6afc5b97db573303aa593eade49beb792e2acb2bbaffffffff9cd5cfd99e89c5ccd39f648520673314488b284b688573b30fe6cd6003ab0e06000000006a473044022048ea7d2973bc64cfd59a917b086e439d4877701fa052f7fee553ad1caed270c502206e081854bdfe540ddcb20f09487502acf8dbcfec3a6da0c809c7a53bcc3526198121030b0e4779639bd0f8c14127530fa740b376f4b52fb533052df73a6220a7857dedffffffff7a87a536ba47caa3a440a151135b59c3e8bc2eb157b7a8981c47357a23945fb5000000006946304302203748878a788585408d1cf704cedb36017b11f8797b13c8d84bdc9cdc980cf0f3021f7bff51305a573804b6b1a1f4cfdd16166110512dc4aa29e2e8bf9b8c9b78b98121035a1285b2a1a661e27a77c87a5ee8493fa443442e72e5090daf678c9e14667968ffffffffc5fd6ed74a75c56b2098e0ac5d8686fffd60866a01e2965dcc15110cd0e81587130000006a4730440220399cca1f309ff0c024a9037284188c0e662e1a9044795ae3a029e9c88d2c2c860220071111a2aaa2d3dfdbedf4c9313c9f4415747e3364a9974764cdaf1d99ab9d11812103975b82e6fa501f66792347b4ca362b1906e3f0c6fe6ea9cf0311ef566e9ab087ffffffff7c5dbd9465c7be7e3a0e3f7605dcee5350bac52aeb3a59cdf499e413e67892e1030000006a473044022009f432bdc6230ca8fd51e47989e8c4d2eadad8a79ecff4bd21230c6eb9f72fea0220721fe88c953b25d5ce6337eb9c68e73179df12cf4a3b7ff8e1eddf415586277381210239a54c6e9defa1fa3d2cc4e727e696d09382caa56fd3b2b1a7d8e14e427714e2ffffffff7839a8e20728a3ac24ed459f5bc4fb1241f0f4bf9cdee44391dc7e4abbd7a6970d0000006a4730440220729c8c8802ae8ded5b62c97f9620b55383b7d60e0958ee0747c8534d952e07a9022062b73e97b1c7f6c6888a8d5693d7b8969cb6cba2315bf84fb9db41f532d7d267812103bea538b59ace8774ec8a0d7223dc1fe8bc4b6f36a7d8e97563c3c790e1911f34ffffffff3130e23792666e65b91b4acf0def3b458200ed9eb2be930a158939340d53b98a070000006a47304402207236d0dc209a3797ff9627fd0abcaa6df84d8a702b6ae60214bfdfe0299c79db022004cd3bde7f6eb6f77a469acea39e2c8ea61b1d3b53b684dfd15d10fb54626479812103b487678803869ef871ebc188dcbb861d635b976ffb26993562c7e0f6c625f76bffffffff78fadaad1cb42bb55d6f045444b1c581d12e47df92f1d0560a9fd4839a508cfa030000006a47304402200d61777d2e2079535c4b2477158b6666263c0e5e75398ffeb6e7ca8771d709c902202567702ad6c014b7c53eb313fd96319adbb295422b75ac6aa15dd68bf18aa3e2812102a9f6dbb247b9f030c2eb3ba22943859b428f6a0c4aca1b290e1cff6da326dd37ffffffff3dd6b1f7e77a61cd4cc4a40d0c4af9c44d911d674ab8ad4ffd0c18028b945206050000006a47304402202a262108f7216f16c7e4146c48f61b800549db331ecaf6d2d5f677d7d2753a5202201d927397c7385967d517c4e7f4fe0cf7ce1ce90e5b7ab2c3c0268ff3321fe674812103af02a5cc56e11e9cd2c6d0ec0e6e5991c51cf482e3852ba2af501e21fa55c39dffffffff78fadaad1cb42bb55d6f045444b1c581d12e47df92f1d0560a9fd4839a508cfa090000006a4730440220096340d009d59d7942afb1963cb8800beb823e48fbbba40bd078f67df92354800220520a810933f92ff3a8077ef946da131690d2b7c3659c70944dbc059e78e8ce358121037ef43096a384229b2b309e1160985f005f8df88ac2cf3ca70fd8a0983868ead5ffffffff3dd6b1f7e77a61cd4cc4a40d0c4af9c44d911d674ab8ad4ffd0c18028b945206000000006b483045022100941d4829199afaa0f366f0d677f2c3b3bf4a45e76df50b14c4018bcc99db1654022051423c38279218d1546c15765114b2ac0d29aa4b47dd74f7ef8e915b5e41fd4f8121025eb73f58035fd0dc31aa9f33d8c6ebcc89be3e734bf00b857c697629083c462fffffffffd55283595376a2f0e6514d96438f337fe4b61561483a4b1e371e24f4d3da20d50c0000006a4730440220058d0d6ef6168fe38d81af182cc9eb488ccccf7b99c46ac41a0ee9ca2b1ded6a0220718783ccf57fddff89d34203c0b07199fb57f43d98388386ae347cc3aa7cc41381210305a1abc9c7d042f46b3e21aa49e6b8acffcf759177469f530d050eeae6dc4ebfffffffffd55283595376a2f0e6514d96438f337fe4b61561483a4b1e371e24f4d3da20d5000000006a4730440220514dc76b97069c49332cbe752fbbab3b51dbdbb145a4fac64a29541b65d96594022009c9a453269d85045d73231c943c877a5b072f8680b2e911fb334768eed9b74c812103ac7c4bcb5903f00cebd89f34536f33b742e4bf221134333f174b792402ae12b0ffffffffd55283595376a2f0e6514d96438f337fe4b61561483a4b1e371e24f4d3da20d5090000006b483045022100bdb3bd58a1b4a4b7cdb445223c2d913c66cd16ff974e876b0fa3fddc02bab88f022000e3037c401f0a338159dc5430cf8ee795e9eea62b233116020f4694cb24ad648121020eb3edb09904df290b950b8cddf30f2eee4224829c291a148a999a22d7b3247affffffff78fadaad1cb42bb55d6f045444b1c581d12e47df92f1d0560a9fd4839a508cfa020000006a473044022059e1fdd1602b61e6c3d670b33d560fde46bfe7fd5c729116e170138bbe80cd1402205583bd1aaa34e52563a277510edc7bc7f5f988771794c3fa6fe9db7f93f83c9b812102fbd94d3d50b7c6c5c20d3d1a67b1cf3ab8800b320f73daf948d5913f07f79f63ffffffffd55283595376a2f0e6514d96438f337fe4b61561483a4b1e371e24f4d3da20d5020000006a473044022017c034e88642e77d6a00e11dae0a4962a093709a98a463874e8aeeade572bb8702203131ef115cb873453caaccaf2dfa400d5103e0c8f6f4fa1ba289df659694b4f98121030261c40bd013a4684445d2ec176b0b93240e4dfee26a1bf0e969fefa621a3f5effffffff12e4969800000000001976a914d6959cccbbecdb4c39c06ba0355ee130ce7c3fbd88ace4969800000000001976a9144538b9f11e54ee011348fb4d1121f00c4176926f88ace4969800000000001976a9149b66d63c5f3c60e4758851c4232ba3eb311c559e88ace4969800000000001976a914e79df321d9665668d8e2a30b18c7270ba15ec54a88ace4969800000000001976a914d73cac081eb2444fe8746e20911764668239d58388ace4969800000000001976a9142142002ee06ee9763359ae21ca6b0737c5de974e88ace4969800000000001976a91477fca6291dfa3cf07060144b8f2fad0f3da893b488ace4969800000000001976a91425bce90e50cc4f7db684b3fc7baa50ca8948410588ace4969800000000001976a9146853fa9fa2dcf22aa9a6090c3a6e6aaf66ccdc5d88ace4969800000000001976a91492771948eb9344f8ce09dac245c3ee962eb8257888ace4969800000000001976a91496ca76024c28534f4ebdd3dff7beb553730d0fdd88ace4969800000000001976a91446eb2f285e78d2d389f83316ecea26f66ec9fed488ace4969800000000001976a914de5f09ff08ca03ad554119f14b5733a07cb9ab5188ace4969800000000001976a914587e145b49f61fdeb0baa78b21f28a47a034702588ace4969800000000001976a9143433260272816af6e855cc8dacfb2fe7266f647988ace4969800000000001976a914318c806b35f528ba48e277f57a1a527ef25af43f88ace4969800000000001976a9140dec91bcac0fdc81373ae1dc349964b069632a4b88ace4969800000000001976a91488466b1be52ada7d847f511360cf964a64edf95488ac00000000");

        Transaction coinJoinTx = new Transaction(TESTNET, coinJoinTxData);

        assertFalse(verifyBIP69(coinJoinTx, false));

        coinJoinTx.sortInputs();
        coinJoinTx.sortOutputs();

        byte [] sortedCoinJoinTxData = coinJoinTx.bitcoinSerialize();

        boolean mismatch = false;
        for (int i = 0; i < coinJoinTxData.length; ++i) {
            if (coinJoinTxData[i] != sortedCoinJoinTxData[i]) {
                mismatch = true;
                break;
            }
        }
        assertTrue("The coinJoinTx should not match after sorting", mismatch);
    }
}
