package org.bitcoinj.evolution;

/*
```- (void)testCreateBlockchainUserTransactionInputs {
    //this is for v3 transaction versions
    DSChain * devnetDRA = [DSChain devnetWithIdentifier:@"devnet-DRA"];
    NSData * hexData = [NSData dataFromHexString:@"03000800013f39fe95e37ce75bf7de2a89496e8c485f75f808b597c7c11fe9f023ec8726d3010000006a473044022033bafeac5704355c7855a6ad099bd6834cbcf3b052e42ed83945c58aae904aa4022073e747d376a8dcd2b5eb89fef274b01c0194ee9a13963ebbc657963417f0acf3012102393c140e7b53f3117fd038581ae66187c4be33f49e33a4c16ffbf2db1255e985feffffff0240420f0000000000016a9421be1d000000001976a9145f461d2cdae3e8244c6dbc6de58ad06ccd22890388ac000000006101000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84"];
    UInt256 txId = *(UInt256 *)@"8f3368ceb332bdb8587fbeb540ad3bbf6f1c6c5a3f85c4e49f5f93351e4979e0".hexToData.reverse.bytes;
    UInt256 inputId = *(UInt256 *)@"d32687ec23f0e91fc1c797b508f8755f488c6e49892adef75be77ce395fe393f".hexToData.reverse.bytes;
    NSString * inputAddress = @"yeXaNd6esFX83gNsqVW7y43SVMqtvygcRT";
    NSString * inputPrivateKey = @"cQv3B1Ww5GkTDEAmA4KaZ7buGXsoUKTBmLLc79PVM5J6qLQc4wqj";
    DSKey * privateKey = [DSKey keyWithPrivateKey:inputPrivateKey onChain:devnetDRA];

    NSString * checkInputAddress = [privateKey addressForChain:devnetDRA];
    XCTAssertEqualObjects(checkInputAddress,inputAddress,@"Private key does not match input address");

    DSKey * payloadKey = [DSKey keyWithPrivateKey:@"cVBJqSygvC7hHQVuarUZQv868NgHUavceAfeqgo32LYiBYYswTv6" onChain:devnetDRA];
    NSString * payloadAddress = @"yeAUXizK9bD6iuxaArDsh7XGX3Q75ZgE3Y";
    UInt160 pubkeyHash = *(UInt160 *)@"467d271aff54f66134ad7513bb7992a48cecbfc3".hexToData.reverse.bytes;
    NSString * checkPayloadAddress = [payloadKey addressForChain:devnetDRA];
    XCTAssertEqualObjects(checkPayloadAddress,payloadAddress,@"Payload key does not match input address");

    NSString * outputAddress0 = @"yV1D32jV3duqeBGqWtjjevQk7ikHuitzK4";
    NSMutableData *script = [NSMutableData data];

    NSValue *hash = uint256_obj(inputId);

    [script appendScriptPubKeyForAddress:inputAddress forChain:devnetDRA];

    DSBlockchainUserRegistrationTransaction *blockchainUserRegistrationTransactionFromMessage = [[DSBlockchainUserRegistrationTransaction alloc] initWithMessage:hexData onChain:devnetDRA];

    XCTAssertEqualObjects(blockchainUserRegistrationTransactionFromMessage.toData,hexData,@"Blockchain user transaction does not match it's data");

    DSBlockchainUserRegistrationTransaction *blockchainUserRegistrationTransaction = [[DSBlockchainUserRegistrationTransaction alloc]
        initWithInputHashes:@[hash]
            inputIndexes:@[@1]
            inputScripts:@[script]
            inputSequences:@[@(TXIN_SEQUENCE - 1)]
            outputAddresses:@[outputAddress0]
            outputAmounts:@[@498999700]
            blockchainUserRegistrationTransactionVersion:1
            username:@"samisfun"
            pubkeyHash:pubkeyHash
            topupAmount:@1000000
            topupIndex:0 onChain:devnetDRA];
    [blockchainUserRegistrationTransaction signPayloadWithKey:payloadKey];
    NSData * payloadDataToConfirm = @"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84".hexToData;
    NSData * payloadData = blockchainUserRegistrationTransaction.payloadData;
    XCTAssertEqualObjects(payloadData,payloadDataToConfirm,@"Payload Data does not match, signing payload does not work");

    [blockchainUserRegistrationTransaction signWithPrivateKeys:@[inputPrivateKey]];
    NSData * inputSignature = @"473044022033bafeac5704355c7855a6ad099bd6834cbcf3b052e42ed83945c58aae904aa4022073e747d376a8dcd2b5eb89fef274b01c0194ee9a13963ebbc657963417f0acf3012102393c140e7b53f3117fd038581ae66187c4be33f49e33a4c16ffbf2db1255e985".hexToData;
    XCTAssertEqualObjects(blockchainUserRegistrationTransaction.inputSignatures[0],inputSignature,@"The transaction input signature isn't signing correctly");


    XCTAssertEqualObjects(blockchainUserRegistrationTransaction.data,hexData,@"The transaction data does not match it's expected values");
    XCTAssertEqualObjects([NSData dataWithUInt256:txId],[NSData dataWithUInt256:blockchainUserRegistrationTransaction.txHash],@"The transaction does not match it's desired private key");
}```
 */

import org.bitcoinj.core.*;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SubTxRegisterTest {

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
    public void verifyTest() {

        Sha256Hash txId = Sha256Hash.wrap("8f3368ceb332bdb8587fbeb540ad3bbf6f1c6c5a3f85c4e49f5f93351e4979e0");
        Address inputAddress = Address.fromBase58(PARAMS, "yeXaNd6esFX83gNsqVW7y43SVMqtvygcRT");
        String inputPrivateKey = "cQv3B1Ww5GkTDEAmA4KaZ7buGXsoUKTBmLLc79PVM5J6qLQc4wqj";
        ECKey privateKey = DumpedPrivateKey.fromBase58(PARAMS, inputPrivateKey).getKey();

        assertEquals(inputAddress, privateKey.toAddress(PARAMS));

        Address payloadAddress = Address.fromBase58(PARAMS, "yeAUXizK9bD6iuxaArDsh7XGX3Q75ZgE3Y");
        byte[] pubkeyHash = Utils.reverseBytes(Utils.HEX.decode("467d271aff54f66134ad7513bb7992a48cecbfc3"));

        assertArrayEquals("Payload key does not match input address", payloadAddress.getHash160(), pubkeyHash);

        Transaction tx = new Transaction(PARAMS, txdata);
        SubTxRegister subtx = new SubTxRegister(PARAMS, tx);

        byte [] payloadDataToConfirm = Utils.HEX.decode("01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84");
        assertArrayEquals("Payload Data does not match, signing payload does not work", payloadDataToConfirm, subtx.getPayload());

        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(txdata.length);
            tx.bitcoinSerialize(stream);
            assertArrayEquals("Blockchain user transaction does not match it's data", txdata, stream.toByteArray());
        } catch (IOException x) {
            fail();
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
        ECKey privateKey = DumpedPrivateKey.fromBase58(PARAMS, inputPrivateKey).getKey();

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

        byte [] payloadDataToConfirm = Utils.HEX.decode("01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84");

        assertArrayEquals("Payload Data does not match, signing payload does not work", payloadDataToConfirm, subtx.getPayload());
        assertArrayEquals("Payload Data does not match, signing payload does not work", payloadDataToConfirm, subTxUnsigned.getPayload());

        tx.setExtraPayload(subtx.getPayload());

        tx.addSignedInput(txin, ScriptBuilder.createOutputScript(inputAddress), privateKey, Transaction.SigHash.ALL, false);

        byte [] inputScript = Utils.HEX.decode("473044022033bafeac5704355c7855a6ad099bd6834cbcf3b052e42ed83945c58aae904aa4022073e747d376a8dcd2b5eb89fef274b01c0194ee9a13963ebbc657963417f0acf3012102393c140e7b53f3117fd038581ae66187c4be33f49e33a4c16ffbf2db1255e985");

        assertArrayEquals("The transaction input signature isn't signing correctly", inputScript, tx.getInput(0).getScriptBytes());

        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(txdata.length);
            tx.bitcoinSerialize(stream);
            assertArrayEquals("Blockchain user transaction does not match it's data", txdata, stream.toByteArray());
        } catch (IOException x) {
            fail();
        }
    }
}
