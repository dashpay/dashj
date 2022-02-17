package org.bitcoinj.params;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DevNetParamsTest {
    @Test
    public void namesAndIdTest() {
        DevNetParams devNetParams = new DevNetParams("palinka", "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55",
                20001, new String [0], true);

        assertEquals("devnet.1.devnet-palinka", devNetParams.getDevnetVersionName());
        assertEquals("devnet-palinka", devNetParams.getDevnetName());
        assertEquals("org.dash.dev.1.palinka", devNetParams.getId());
    }

    @Test
    public void genesisBlockTest() {
        DevNetParams devNetParams = new DevNetParams("schnapps", "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55",
                20001, new String [0], true);

        // verify that the devnet genesis block isvalid
        Block genesisBlock = devNetParams.getDevNetGenesisBlock();
        genesisBlock.verifyHeader();

        Transaction coinbase = Objects.requireNonNull(genesisBlock.getTransactions()).get(0);

        // script = 1 PUSHDATA(14)[6465766e65742d73636861707073] 1
        byte [] coinbaseInputScript = Utils.HEX.decode("510f6465766e65742d7363686e6170707351");
        assertArrayEquals(coinbaseInputScript, coinbase.getInput(0).getScriptBytes());
    }

    @Test
    public void testNetDevNetGenesisBlockTest() {
        TestNet3Params testnetParams = TestNet3Params.get();

        // verify that the testnet does not have a devnet genesis block
        Block genesisBlock = testnetParams.getDevNetGenesisBlock();
        assertNull(genesisBlock);
    }

    @Test
    public void testNetDevNetNamesTest() {
        TestNet3Params testnetParams = TestNet3Params.get();

        // testnet should not have devnet names
        assertNull(testnetParams.getDevnetVersionName());
        assertNull(testnetParams.getDevnetName());
    }

    @Test
    public void manyDevnets() {
        DevNetParams palinka = DevNetParams.get("palinka", "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55",
                20001, new String [0]);
        DevNetParams schnapps = DevNetParams.get("schnapps", "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55",
                20001, new String [0]);

        DevNetParams palinkaTwo = DevNetParams.get("palinka", "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55",
                20001, new String [0]);

        DevNetParams palinkaThree = DevNetParams.get("palinka");

        // all palinka variables should point to the exact name object
        assertSame(palinka, palinkaTwo);
        assertSame(palinka, palinkaThree);
        assertNotSame(palinka, schnapps);
    }
}
