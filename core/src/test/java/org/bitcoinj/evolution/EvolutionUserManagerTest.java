package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

public class EvolutionUserManagerTest {
    Context context;
    UnitTestParams PARAMS;
    byte[] txdata;

    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = Context.getOrCreate(PARAMS);
        txdata = Utils.HEX.decode("03000800013f39fe95e37ce75bf7de2a89496e8c485f75f808b597c7c11fe9f023ec8726d3010000006a473044022033bafeac5704355c7855a6ad099bd6834cbcf3b052e42ed83945c58aae904aa4022073e747d376a8dcd2b5eb89fef274b01c0194ee9a13963ebbc657963417f0acf3012102393c140e7b53f3117fd038581ae66187c4be33f49e33a4c16ffbf2db1255e985feffffff0240420f0000000000016a9421be1d000000001976a9145f461d2cdae3e8244c6dbc6de58ad06ccd22890388ac000000006101000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";
    }

    @Test
    public void testManager() {
        EvolutionUserManager manager = new EvolutionUserManager(context);
        Transaction tx = new Transaction(PARAMS, txdata);

        if(!manager.checkSubTxRegister(tx, null))
            fail();

        if(!manager.processSubTxRegister(tx, null, Coin.valueOf(10000)))
            fail();

        EvolutionUser user = manager.getUser(tx.getHash());
        EvolutionUser currentUser = manager.getCurrentUser();

        SubTxRegister subtx = (SubTxRegister)tx.getExtraPayloadObject();
        assertEquals(subtx.getUserName(), user.userName);
        assertEquals(subtx.getPubKeyId(), user.curPubKeyID);

        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            manager.bitcoinSerialize(bos);
            EvolutionUserManager secondManager = new EvolutionUserManager(PARAMS, bos.toByteArray());
            EvolutionUser secondUser = secondManager.getUser(tx.getHash());
            EvolutionUser secondCurrentUser = secondManager.getCurrentUser();

            assertEquals(subtx.getUserName(), secondUser.getUserName());
            assertEquals(subtx.getPubKeyId(), secondUser.getCurPubKeyID());
            assertEquals(tx.getHash(), secondUser.getRegTx().getHash());
            assertEquals(currentUser.getRegTxId(), secondCurrentUser.getRegTxId());
        } catch (IOException x) {
            fail();
        }

    }
}
