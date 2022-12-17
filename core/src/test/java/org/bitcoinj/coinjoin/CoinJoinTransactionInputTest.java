package org.bitcoinj.coinjoin;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CoinJoinTransactionInputTest {
    NetworkParameters PARAMS = UnitTestParams.get();
    @Test
    public void transactionInputTest() {
        byte [] scriptBytes = new ScriptBuilder().number(4).build().getProgram();
        TransactionInput txin = new TransactionInput(UnitTestParams.get(), null, new ScriptBuilder().number(5).build().getProgram() );
        CoinJoinTransactionInput customTxIn = new CoinJoinTransactionInput(txin, new ScriptBuilder().number(4).build(), -9);
        assertEquals(customTxIn.getPrevPubKey(), new Script(scriptBytes));
        assertFalse(customTxIn.hasSignature());
        assertEquals(customTxIn.getRounds(), -9);
    }

    @Test
    public void payloadTest() {
        //TxIn for [9f7d8624b7a601411c5c87d803e84ec36f381d215596ebc26eccf44e776e343d:4]: <empty>
        byte[] payload = Utils.HEX.decode("3d346e774ef4cc6ec2eb9655211d386fc34ee803d8875c1c4101a6b724867d9f0400000000ffffffff");
        CoinJoinTransactionInput input = new CoinJoinTransactionInput(PARAMS, payload, 0);
        assertEquals(0, input.getRounds());
        assertEquals(4, input.getOutpoint().getIndex());
    }
}
