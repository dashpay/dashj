package org.bitcoinj.coinjoin;

import com.google.common.collect.Lists;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.easymock.EasyMock.createMock;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CoinJoinEntryTest {
    /*
        CoinJoinClientSession.relay: Sending CoinJoinEntry(txCollateral=b8f30db7d0c201dd4f6632fe09ccffadbded3051b5c0f3ac435e9d56f40b777d, mixingInputs.size=5, mixingOutputs.size=5)
          input:  TxIn for [9d70c0a4566f438091a0659b726551c1faaa477155d90d8b6858c264f7117ba5:1]: <empty>
          input:  TxIn for [5a23dd70dbf8f595ac55710c708e514c613595aae958c1b9bad02dbac1d7b16e:0]: <empty>
          input:  TxIn for [8087b393549d86c330abc3f1cf9e07375d39ce91f23c081270d7a1f18ff82621:0]: <empty>
          input:  TxIn for [ab4dae4aa27dad995ed3372918a7a9247b13149bf1b126194d93b322e1fb92c6:2]: <empty>
          input:  TxIn for [c471ff68382b91ba49546d7147f851a1b724c2a23c03db2dad457c3c3a9765ac:0]: <empty>
          output: TxOut of 0.00100001 DASH to ycRPFRGZSuFE9NufKvJRHVRWgU7HjZHe4E script:DUP HASH160 PUSHDATA(20)[b0a180dfd44ba7066f5f42f3eaf431f0cd668fe3] EQUALVERIFY CHECKSIG
          output: TxOut of 0.00100001 DASH to yXQTFMZR93symkQQP6sBdoNWfuTBXC8LRW script:DUP HASH160 PUSHDATA(20)[799bcbf0ff47c4e83fbf1db27a337cc89ed84070] EQUALVERIFY CHECKSIG
          output: TxOut of 0.00100001 DASH to yLsPyeigcsyiuTZtXtARc3FjQhiG7jsgXg script:DUP HASH160 PUSHDATA(20)[060ae9e82d52804b66911e8aa4e94d88665ec19f] EQUALVERIFY CHECKSIG
          output: TxOut of 0.00100001 DASH to ybKZyyEqADxWAZWdgNZEYu36gHbcD2Ea1m script:DUP HASH160 PUSHDATA(20)[a48fd783b7a6aad534a91f941a9665c8ecf09643] EQUALVERIFY CHECKSIG
          output: TxOut of 0.00100001 DASH to yVhMBVUoRCrh7gCuLxkfnhyGHnDSzdKK4E script:DUP HASH160 PUSHDATA(20)[66dd8d620782a0d89e63f0ff2d7935c46e053a42] EQUALVERIFY CHECKSIG to 214614
        CoinJoinClientSession.relay: CoinJoinEntry: 05a57b11f764c258688b0dd9557147aafac15165729b65a09180436f56a4c0709d0100000000ffffffff6eb1d7c1ba2dd0bab9c158e9aa9535614c518e700c7155ac95f5f8db70dd235a0000000000ffffffff2126f88ff1a1d77012083cf291ce395d37079ecff1c3ab30c3869d5493b387800000000000ffffffffc692fbe122b3934d1926b1f19b14137b24a9a7182937d35e99ad7da24aae4dab0200000000ffffffffac65973a3c7c45ad2ddb033ca2c224b7a151f847716d5449ba912b3868ff71c40000000000ffffffff0100000001dd86755f2cb8d67c52da2fc42c9a97a2b08e6de01f94037390d46fae68e8ed99000000006b483045022100954aa1d666906e78bcc42806874f153d31d5e1e4ae18dcadcbf8dbfc1123e3900220732c82db96098bbee7a69f284464d5233de0e2adf23401552bf2eba1623509fb0121036bfa0b828ff9b020750e765158495979bea2134b13386e6655e00ce1b9cbe980ffffffff0110270000000000001976a9146f6293e33e78ae0f2d74ba1922b86794b9efb08288ac0000000005a1860100000000001976a914b0a180dfd44ba7066f5f42f3eaf431f0cd668fe388aca1860100000000001976a914799bcbf0ff47c4e83fbf1db27a337cc89ed8407088aca1860100000000001976a914060ae9e82d52804b66911e8aa4e94d88665ec19f88aca1860100000000001976a914a48fd783b7a6aad534a91f941a9665c8ecf0964388aca1860100000000001976a91466dd8d620782a0d89e63f0ff2d7935c46e053a4288ac
    */

    static byte[] dsiMessage = Utils.HEX.decode("05a57b11f764c258688b0dd9557147aafac15165729b65a09180436f56a4c0709d0100000000ffffffff6eb1d7c1ba2dd0bab9c158e9aa9535614c518e700c7155ac95f5f8db70dd235a0000000000ffffffff2126f88ff1a1d77012083cf291ce395d37079ecff1c3ab30c3869d5493b387800000000000ffffffffc692fbe122b3934d1926b1f19b14137b24a9a7182937d35e99ad7da24aae4dab0200000000ffffffffac65973a3c7c45ad2ddb033ca2c224b7a151f847716d5449ba912b3868ff71c40000000000ffffffff0100000001dd86755f2cb8d67c52da2fc42c9a97a2b08e6de01f94037390d46fae68e8ed99000000006b483045022100954aa1d666906e78bcc42806874f153d31d5e1e4ae18dcadcbf8dbfc1123e3900220732c82db96098bbee7a69f284464d5233de0e2adf23401552bf2eba1623509fb0121036bfa0b828ff9b020750e765158495979bea2134b13386e6655e00ce1b9cbe980ffffffff0110270000000000001976a9146f6293e33e78ae0f2d74ba1922b86794b9efb08288ac0000000005a1860100000000001976a914b0a180dfd44ba7066f5f42f3eaf431f0cd668fe388aca1860100000000001976a914799bcbf0ff47c4e83fbf1db27a337cc89ed8407088aca1860100000000001976a914060ae9e82d52804b66911e8aa4e94d88665ec19f88aca1860100000000001976a914a48fd783b7a6aad534a91f941a9665c8ecf0964388aca1860100000000001976a91466dd8d620782a0d89e63f0ff2d7935c46e053a4288ac");
    static byte[] txCollateralBytes = Utils.HEX.decode("0100000001dd86755f2cb8d67c52da2fc42c9a97a2b08e6de01f94037390d46fae68e8ed99000000006b483045022100954aa1d666906e78bcc42806874f153d31d5e1e4ae18dcadcbf8dbfc1123e3900220732c82db96098bbee7a69f284464d5233de0e2adf23401552bf2eba1623509fb0121036bfa0b828ff9b020750e765158495979bea2134b13386e6655e00ce1b9cbe980ffffffff0110270000000000001976a9146f6293e33e78ae0f2d74ba1922b86794b9efb08288ac00000000");

    private CoinJoinEntry coinJoinEntry;
    private final NetworkParameters params = UnitTestParams.get();
    private List<CoinJoinTransactionInput> mixingInputs;
    private List<TransactionOutput> mixingOutputs;
    private Transaction txCollateral;
    private Peer peer;

    @Before
    public void setUp() {
        // Initialize parameters
        mixingInputs = Lists.newArrayList(
                new CoinJoinTransactionInput(new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 1L, Sha256Hash.wrap("9d70c0a4566f438091a0659b726551c1faaa477155d90d8b6858c264f7117ba5"))), null, 0),
                new CoinJoinTransactionInput(new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 0L, Sha256Hash.wrap("5a23dd70dbf8f595ac55710c708e514c613595aae958c1b9bad02dbac1d7b16e"))), null, 0),
                new CoinJoinTransactionInput(new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 0L, Sha256Hash.wrap("8087b393549d86c330abc3f1cf9e07375d39ce91f23c081270d7a1f18ff82621"))), null, 0),
                new CoinJoinTransactionInput(new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 2L, Sha256Hash.wrap("ab4dae4aa27dad995ed3372918a7a9247b13149bf1b126194d93b322e1fb92c6"))), null, 0),
                new CoinJoinTransactionInput(new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 0L, Sha256Hash.wrap("c471ff68382b91ba49546d7147f851a1b724c2a23c03db2dad457c3c3a9765ac"))), null, 0)
        );
        mixingOutputs = Lists.newArrayList(
                new TransactionOutput(params, null, Denomination.THOUSANDTH.value, Address.fromBase58(params, "ycRPFRGZSuFE9NufKvJRHVRWgU7HjZHe4E")),
                new TransactionOutput(params, null, Denomination.THOUSANDTH.value, Address.fromBase58(params, "yXQTFMZR93symkQQP6sBdoNWfuTBXC8LRW")),
                new TransactionOutput(params, null, Denomination.THOUSANDTH.value, Address.fromBase58(params, "yLsPyeigcsyiuTZtXtARc3FjQhiG7jsgXg")),
                new TransactionOutput(params, null, Denomination.THOUSANDTH.value, Address.fromBase58(params, "ybKZyyEqADxWAZWdgNZEYu36gHbcD2Ea1m")),
                new TransactionOutput(params, null, Denomination.THOUSANDTH.value, Address.fromBase58(params, "yVhMBVUoRCrh7gCuLxkfnhyGHnDSzdKK4E"))
        );
        txCollateral = new Transaction(params, txCollateralBytes, 0);
        peer = createMock(Peer.class);

        // Initialize the CoinJoinEntry instance
        coinJoinEntry = new CoinJoinEntry(params, mixingInputs, mixingOutputs, txCollateral);
    }

    @Test
    public void testConstructorWithParamsAndPayload() {
        // Test constructor with NetworkParameters and payload
        CoinJoinEntry entry = new CoinJoinEntry(params, dsiMessage);

        assertEquals(mixingInputs, entry.getMixingInputs());
        assertEquals(mixingOutputs, entry.getMixingOutputs());
        assertEquals(txCollateral, entry.getTxCollateral());
    }

    @Test
    public void testConstructorWithParamsAndInputsOutputsCollateral() {
        assertNotNull(coinJoinEntry);
        assertEquals(mixingInputs, coinJoinEntry.getMixingInputs());
        assertEquals(mixingOutputs, coinJoinEntry.getMixingOutputs());
        assertEquals(txCollateral, coinJoinEntry.getTxCollateral());
    }

    @Test
    public void testBitcoinSerializeToStream() {
        assertArrayEquals(dsiMessage, coinJoinEntry.bitcoinSerialize());
    }

    @Test
    public void testToStringMethods() {
        String result = coinJoinEntry.toString();
        assertNotNull(result);
    }

    @Test
    public void testGetters() {
        assertEquals(mixingInputs, coinJoinEntry.getMixingInputs());
        assertEquals(mixingOutputs, coinJoinEntry.getMixingOutputs());
        assertEquals(txCollateral, coinJoinEntry.getTxCollateral());
    }

    @Test
    public void testSetPeer() {
        coinJoinEntry.setPeer(peer);
        assertEquals(peer, coinJoinEntry.getPeer());
    }

    @Test
    public void testAddScriptSig() {
        TransactionInput txin = new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 1L, Sha256Hash.wrap("9d70c0a4566f438091a0659b726551c1faaa477155d90d8b6858c264f7117ba5")));
        boolean result = coinJoinEntry.addScriptSig(txin);
        assertTrue(result);
    }

    @Test
    public void testAddScriptSigFail() {
        TransactionInput txin = new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 1L, Sha256Hash.wrap("1370c0a4566f438091a0659b726551c1faaa477155d90d8b6858c264f7117ba5")));
        boolean result = coinJoinEntry.addScriptSig(txin);
        assertFalse(result);
    }

    @Test
    public void testAddScriptSigFailAlreadySetSignature() {
        TransactionInput txin = new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, 1L, Sha256Hash.wrap("9d70c0a4566f438091a0659b726551c1faaa477155d90d8b6858c264f7117ba5")));
        boolean result = coinJoinEntry.addScriptSig(txin);
        assertTrue(result);
        assertFalse(coinJoinEntry.addScriptSig(txin));
    }
}
