package org.bitcoinj.coinjoin;

import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CoinJoinTransactionInputTest {
    @Test
    public void transactionInputTest() {
        byte [] scriptBytes = new ScriptBuilder().number(4).build().getProgram();
        TransactionInput txin = new TransactionInput(UnitTestParams.get(), null, new ScriptBuilder().number(5).build().getProgram() );
        CoinJoinTransactionInput customTxIn = new CoinJoinTransactionInput(txin, new ScriptBuilder().number(4).build(), -9);
        assertEquals(customTxIn.getPrevPubKey(), new Script(scriptBytes));
        assertFalse(customTxIn.hasSignature());
        assertEquals(customTxIn.getRounds(), -9);
    }
}
