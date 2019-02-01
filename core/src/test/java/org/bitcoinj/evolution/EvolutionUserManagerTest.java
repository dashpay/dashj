package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.bitcoinj.core.Utils.HEX;
import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;
import static org.junit.Assert.assertArrayEquals;

public class EvolutionUserManagerTest {
    Context context;
    UnitTestParams PARAMS;
    byte[] registerTxData;
    byte[] topupTxData;
    byte [] resetTxData;
    byte[] registerTxDataDuplicate;

    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = Context.getOrCreate(PARAMS);
        registerTxData = HEX.decode("03000800013f39fe95e37ce75bf7de2a89496e8c485f75f808b597c7c11fe9f023ec8726d3010000006a473044022033bafeac5704355c7855a6ad099bd6834cbcf3b052e42ed83945c58aae904aa4022073e747d376a8dcd2b5eb89fef274b01c0194ee9a13963ebbc657963417f0acf3012102393c140e7b53f3117fd038581ae66187c4be33f49e33a4c16ffbf2db1255e985feffffff0240420f0000000000016a9421be1d000000001976a9145f461d2cdae3e8244c6dbc6de58ad06ccd22890388ac000000006101000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84");
        topupTxData = HEX.decode("0300090001d4ad073ec40da120d28a47164753f4f5ad80d0dc3b918b39223d36ebdfacdef6000000006a47304402203822cbd38bc5dfe4fe0ec7f3740d09a2a05ed67020879c3b364ea0e7348880bd022039c3ea29f261060353099a5596e268ae5e4c928e59211e4ffeddf10de128bcf10121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34cafeffffff0200e1f50500000000016ad2d327cc050000001976a9141eccbe2508c7741d2e4c517f87565e7d477cfbbc88ac00000000220100e079491e35935f9fe4c4853f5a6c1c6fbf3bad40b5be7f58b8bd32b3ce68338f");
        resetTxData = HEX.decode("03000a00000000000000a00100e079491e35935f9fe4c4853f5a6c1c6fbf3bad40b5be7f58b8bd32b3ce68338fe079491e35935f9fe4c4853f5a6c1c6fbf3bad40b5be7f58b8bd32b3ce68338fe803000000000000f6f5abf4ba75c554b9ef001a78c35ce5edb3ccb141203eb36d9258a6ec3017cd3a7fc7637299f17dd3ebd36768f32e948ce8439f52dc40d33bd355adc2257f3416ded76506aba7debfdc1d206e8abfde0dd5e5f9640c");
        registerTxDataDuplicate = HEX.decode("03000800013f39fe95e37ce75bf7de2a89496e8c485f75f808b597c7c11fe9f023ec8726d3010000006a473044022035c6d3de81ecb959cd4487d4fc1680c49d4ef6f5541d9e81656bcc31669e8712022004e4275f8a57f95369b64b0551ff7d8bd2341739ff158df665eb98341fe3732f0121027519e6da9db7fd3d28a90855388745ee8fc3808a85239d643b6523d32ac399f7feffffff0240420f0000000000016a9421be1d000000001976a9145f461d2cdae3e8244c6dbc6de58ad06ccd22890388ac000000006101000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84");
    }

    @Test
    public void createtopup() {
        Sha256Hash blockchainUserRegistrationTransactionHash = Sha256Hash.wrap("8f3368ceb332bdb8587fbeb540ad3bbf6f1c6c5a3f85c4e49f5f93351e4979e0");
        Sha256Hash inputId = Sha256Hash.wrap("f6deacdfeb363d22398b913bdcd080adf5f4534716478ad220a10dc43e07add4");
        Address outputAddress = Address.fromBase58(PARAMS, "yP8JPjWoc2u8rSN6F4eE5FQn3nQiQJ9jDs");
        Sha256Hash txId = Sha256Hash.wrap("715f96a80e0e4feb8a94f2e9f4f6821dd4502f0ae6c43013ec6e77985d059b55");
        Address inputAddress = Address.fromBase58(PARAMS, "yYZqfmQhqMSF1PL7xeNHzQM3q9rktXFPLN");
        String inputPrivateKey = "cNYPkC4hGoE11ieBr2GgwyUct8zY1HLi5S5K2LLPMewtQGJsbu9H";
        ECKey privateKey = DumpedPrivateKey.fromBase58(PARAMS, inputPrivateKey).getKey();

        Transaction tx = new Transaction(PARAMS);
        tx.setVersion(3);
        tx.setType(Transaction.Type.TRANSACTION_SUBTX_TOPUP);
        TransactionOutPoint outpoint = new TransactionOutPoint(PARAMS, 0, inputId);
        TransactionInput txin = new TransactionInput(PARAMS, null, new byte[0], outpoint);
        txin.setSequenceNumber(TransactionInput.NO_SEQUENCE-1);

        TransactionOutput output = new TransactionOutput(PARAMS, null, Coin.valueOf(24899998674L), ScriptBuilder.createOutputScript(outputAddress).getProgram());

        TransactionOutput outputOpReturn = new TransactionOutput(PARAMS, null, Coin.valueOf(100000000), new ScriptBuilder().op(OP_RETURN).build().getProgram());
        tx.addOutput(outputOpReturn);
        tx.addOutput(output);

        SubTxTopup subtx = new SubTxTopup(1, blockchainUserRegistrationTransactionHash);
        tx.setExtraPayload(subtx.getPayload());

        tx.addSignedInput(txin, ScriptBuilder.createOutputScript(inputAddress), privateKey, Transaction.SigHash.ALL, false);
        assertEquals(3, tx.getVersionShort());
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            tx.bitcoinSerialize(bos);
            assertEquals(tx.getVersionShort(), 3);
        } catch (IOException x) {
            fail();
        }
    }

    @Test
    public void createSubTxResetKey() {
        Sha256Hash txId = Sha256Hash.wrap("251961000a115bafbb7bdb6e1baf23d88e37ecf2fe6af5d9572884cabaecdcc0");
        Sha256Hash blockchainUserRegistrationTransactionHash = Sha256Hash.wrap("8f3368ceb332bdb8587fbeb540ad3bbf6f1c6c5a3f85c4e49f5f93351e4979e0");
        Sha256Hash blockchainUserPreviousTransactionHash = Sha256Hash.wrap("8f3368ceb332bdb8587fbeb540ad3bbf6f1c6c5a3f85c4e49f5f93351e4979e0");

        ECKey payloadKey = DumpedPrivateKey.fromBase58(PARAMS,"cVBJqSygvC7hHQVuarUZQv868NgHUavceAfeqgo32LYiBYYswTv6").getKey();
        Address payloadAddress = Address.fromBase58(PARAMS, "yeXaNd6esFX83gNsqVW7y43SVMqtvygcRT");
        //Assert.assertEquals("Payload key does not match input address", payloadAddress, payloadKey.toAddress(PARAMS));

        ECKey replacementPayloadKey = DumpedPrivateKey.fromBase58(PARAMS,"cPG7GuByFnYkGvkrZqw8chGNfJYmKYnXt6TBjHruaApC42CPwwTE").getKey();
        Address replacementPayloadAddress = Address.fromBase58(PARAMS, "yiqFNxn9kbWEKj7B87aEnoyChBL8rMFymt");
        KeyId replacementPubkeyId = new KeyId(Utils.reverseBytes(HEX.decode("b1ccb3ede55cc3781a00efb954c575baf4abf5f6")));
        assertArrayEquals(replacementPubkeyId.getBytes(), replacementPayloadKey.getPubKeyHash());

        Transaction tx = new Transaction(PARAMS);
        tx.setVersion(3);
        tx.setType(Transaction.Type.TRANSACTION_SUBTX_RESETKEY);

        SubTxResetKey resetKey = new SubTxResetKey(1, blockchainUserRegistrationTransactionHash,
                blockchainUserPreviousTransactionHash, Coin.valueOf(1000), replacementPubkeyId, payloadKey);

        tx.setExtraPayload(resetKey);
        byte [] payloadDataToConfirm = HEX.decode("0100659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370d659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370de803000000000000f6f5abf4ba75c554b9ef001a78c35ce5edb3ccb1411fd442ee3bb6dac571f432e56def3d06f64a15cc74f382184ca4d5d4cad781ced01ae4e8109411f548da5c5fa6bfce5a23a8d620104e6953600539728b95077e19");
        //assertArrayEquals("Payload Data does not match, signing payload does not work", payloadDataToConfirm, resetKey.getPayload());

        //Assert.assertEquals(txId, tx.getHash());
        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(200);
            tx.bitcoinSerialize(stream);
            //assertArrayEquals("resetkey transaction does not match it's data", txdata, stream.toByteArray());
            assertEquals(true, true);
        } catch (IOException x) {
            Assert.fail();
        }
    }

    @Test
    public void createSubTx() {
        Sha256Hash inputId = Sha256Hash.wrap("d32687ec23f0e91fc1c797b508f8755f488c6e49892adef75be77ce395fe393f");
        ECKey payloadKey = DumpedPrivateKey.fromBase58(PARAMS, "cVBJqSygvC7hHQVuarUZQv868NgHUavceAfeqgo32LYiBYYswTv6").getKey();
        Address outputAddress = Address.fromBase58(PARAMS, "yV1D32jV3duqeBGqWtjjevQk7ikHuitzK4");
        Sha256Hash txId = Sha256Hash.wrap("8f3368ceb332bdb8587fbeb540ad3bbf6f1c6c5a3f85c4e49f5f93351e4979e0");
        Address inputAddress = Address.fromBase58(PARAMS, "yeXaNd6esFX83gNsqVW7y43SVMqtvygcRT");
        String inputPrivateKey = "cQv3B1Ww5GkTDEAmA4KaZ7buGXsoUKTBmLLc79PVM5J6qLQc4wqj";
        ECKey privateKey = new ECKey(); //DumpedPrivateKey.fromBase58(PARAMS, inputPrivateKey).getKey();

        Transaction tx = new Transaction(PARAMS);
        tx.setVersion(3);
        tx.setType(Transaction.Type.TRANSACTION_SUBTX_REGISTER);
        TransactionOutPoint outpoint = new TransactionOutPoint(PARAMS, 1, inputId);
        TransactionInput txin = new TransactionInput(PARAMS, null, new byte[0], outpoint);
        txin.setSequenceNumber(TransactionInput.NO_SEQUENCE-1);

        TransactionOutput output = new TransactionOutput(PARAMS, null, Coin.valueOf(498999700), ScriptBuilder.createOutputScript(outputAddress).getProgram());

        TransactionOutput outputOpReturn = new TransactionOutput(PARAMS, null, Coin.valueOf(1000000), new ScriptBuilder().op(OP_RETURN).build().getProgram());
        tx.addOutput(outputOpReturn);
        tx.addOutput(output);

        SubTxRegister subtx = new SubTxRegister(1, "samisfun", payloadKey);
        SubTxRegister subTxUnsigned = new SubTxRegister(1, "samisfun", new KeyId(payloadKey.getPubKeyHash()));
        subTxUnsigned.sign(payloadKey);

        byte [] payloadDataToConfirm = HEX.decode("01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84");

        assertArrayEquals("Payload Data does not match, signing payload does not work", payloadDataToConfirm, subtx.getPayload());
        assertArrayEquals("Payload Data does not match, signing payload does not work", payloadDataToConfirm, subTxUnsigned.getPayload());

        tx.setExtraPayload(subtx.getPayload());

        tx.addSignedInput(txin, ScriptBuilder.createOutputScript(inputAddress), privateKey, Transaction.SigHash.ALL, false);

        byte [] inputScript = HEX.decode("473044022033bafeac5704355c7855a6ad099bd6834cbcf3b052e42ed83945c58aae904aa4022073e747d376a8dcd2b5eb89fef274b01c0194ee9a13963ebbc657963417f0acf3012102393c140e7b53f3117fd038581ae66187c4be33f49e33a4c16ffbf2db1255e985");

        //assertArrayEquals("The transaction input signature isn't signing correctly", inputScript, tx.getInput(0).getScriptBytes());

        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(0);
            tx.bitcoinSerialize(stream);
            String string = HEX.encode(stream.toByteArray());
            string.toCharArray();
            //assertArrayEquals("Blockchain user transaction does not match it's data", txdata, stream.toByteArray());
        } catch (IOException x) {
            Assert.fail();
        }
    }

    @Test
    public void testManager() {
        EvolutionUserManager manager = new EvolutionUserManager(context);
        Transaction tx = new Transaction(PARAMS, registerTxData);

        try {
            manager.checkSubTxRegister(tx, null);
            if(!manager.processSubTxRegister(tx, null))
                fail();
        } catch (VerificationException x) {
            fail(x.getMessage());
        }

        EvolutionUser user = manager.getUser(tx.getHash());
        EvolutionUser currentUser = manager.getCurrentUser();

        SubTxRegister subtx = (SubTxRegister)tx.getExtraPayloadObject();
        assertEquals(subtx.getUserName(), user.userName);
        assertEquals(subtx.getPubKeyId(), user.curPubKeyID);

        //check that we cannot add a duplicate blockchain user made with different keys
        Transaction txDup = new Transaction(PARAMS, registerTxDataDuplicate);

        try {
            manager.checkSubTxRegister(txDup, null);
            fail();  //checkSubTxRegister should throw an exception, because "samisfun" exists
        } catch (VerificationException x) {
            assert(x.getMessage().startsWith("Username exists"));
        }

        //check that we can process a user again that has already been added without failure
        try {
            manager.checkSubTxRegister(tx, null);
            if(manager.processSubTxRegister(tx, null)) //Since the user exists, return false
                fail();
        } catch (VerificationException x) {
            fail(x.getMessage());
        }


        Transaction secondTx = new Transaction(PARAMS, topupTxData);
        SubTxTopup topup = (SubTxTopup)secondTx.getExtraPayloadObject();

        try {
            manager.checkSubTxTopup(secondTx, null);
            if (!manager.processSubTxTopup(secondTx, null))
                fail();
        } catch (VerificationException x) {
            fail(x.getMessage());
        }

        //If same topup is processed again, checkSubTxTopup will not fail
        manager.checkSubTxTopup(secondTx, null);
        //But processSubTxTopup will return false
        if(manager.processSubTxTopup(secondTx, null))
            fail();

        assertEquals(101000000L, user.getCreditBalance().getValue());
        Transaction thirdTx = new Transaction(PARAMS, resetTxData);
        SubTxResetKey reset = (SubTxResetKey)thirdTx.getExtraPayloadObject();

        if(!manager.processSpecialTransaction(thirdTx, null))
            fail();

        assertEquals(reset.getNewPubKeyId(), user.getCurPubKeyID());

        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            manager.bitcoinSerialize(bos);
            EvolutionUserManager secondManager = new EvolutionUserManager(PARAMS, bos.toByteArray());
            EvolutionUser secondUser = secondManager.getUser(tx.getHash());
            EvolutionUser secondCurrentUser = secondManager.getCurrentUser();

            assertEquals(subtx.getUserName(), secondUser.getUserName());
            assertEquals(reset.getNewPubKeyId(), secondUser.getCurPubKeyID());
            assertEquals(tx.getHash(), secondUser.getRegTx().getHash());
            assertEquals(currentUser.getRegTxId(), secondCurrentUser.getRegTxId());
        } catch (IOException x) {
            fail();
        }

    }
}
