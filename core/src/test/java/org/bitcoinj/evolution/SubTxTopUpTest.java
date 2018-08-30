package org.bitcoinj.evolution;

/*
- (void)testTopupBlockchainUserTransactionInputs {
    //this is for v3 transaction versions
    DSChain * devnetDRA = [DSChain devnetWithIdentifier:@"devnet-DRA"];
    NSData * hexData = [NSData dataFromHexString:@"0300090001d4ad073ec40da120d28a47164753f4f5ad80d0dc3b918b39223d36ebdfacdef6000000006b483045022100a65429d4f2ab2df58cafdaaffe874ef260f610e068e89a4455fbf92261156bb7022015733ae5aef3006fd5781b91f97ca1102edf09e9383ca761e407c619d13db7660121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34cafeffffff0200e1f50500000000016ad2d327cc050000001976a9141eccbe2508c7741d2e4c517f87565e7d477cfbbc88ac000000002201002369fced72076b33e25c5ca31efb605037e3377c8e1989eb9ec968224d5e22b4"];
    UInt256 txId = *(UInt256 *)@"715f96a80e0e4feb8a94f2e9f4f6821dd4502f0ae6c43013ec6e77985d059b55".hexToData.reverse.bytes;
    UInt256 blockchainUserRegistrationTransactionHash = *(UInt256 *)@"b4225e4d2268c99eeb89198e7c37e3375060fb1ea35c5ce2336b0772edfc6923".hexToData.reverse.bytes;
    UInt256 inputId = *(UInt256 *)@"f6deacdfeb363d22398b913bdcd080adf5f4534716478ad220a10dc43e07add4".hexToData.reverse.bytes;
    NSString * inputAddress = @"yYZqfmQhqMSF1PL7xeNHzQM3q9rktXFPLN";
    NSString * inputPrivateKey = @"cNYPkC4hGoE11ieBr2GgwyUct8zY1HLi5S5K2LLPMewtQGJsbu9H";
    DSKey * privateKey = [DSKey keyWithPrivateKey:inputPrivateKey onChain:devnetDRA];

    NSString * checkInputAddress = [privateKey addressForChain:devnetDRA];
    XCTAssertEqualObjects(checkInputAddress,inputAddress,@"Private key does not match input address");

    NSString * outputAddress0 = @"yP8JPjWoc2u8rSN6F4eE5FQn3nQiQJ9jDs";
    NSMutableData *script = [NSMutableData data];

    NSValue *hash = uint256_obj(inputId);

    [script appendScriptPubKeyForAddress:inputAddress forChain:devnetDRA];

    DSBlockchainUserTopupTransaction *blockchainUserTopupTransactionFromMessage = [[DSBlockchainUserTopupTransaction alloc] initWithMessage:hexData onChain:devnetDRA];

    XCTAssertEqualObjects(blockchainUserTopupTransactionFromMessage.toData,hexData,@"Blockchain user topup transaction does not match it's data");

    DSBlockchainUserTopupTransaction *blockchainUserTopupTransaction = [[DSBlockchainUserTopupTransaction alloc] initWithInputHashes:@[hash] inputIndexes:@[@0] inputScripts:@[script] inputSequences:@[@(TXIN_SEQUENCE - 1)] outputAddresses:@[outputAddress0] outputAmounts:@[@24899998674] blockchainUserTopupTransactionVersion:1 registrationTransactionHash:blockchainUserRegistrationTransactionHash topupAmount:@100000000 topupIndex:0 onChain:devnetDRA];

    [blockchainUserTopupTransaction signWithPrivateKeys:@[inputPrivateKey]];

    NSData * inputSignature = @"483045022100a65429d4f2ab2df58cafdaaffe874ef260f610e068e89a4455fbf92261156bb7022015733ae5aef3006fd5781b91f97ca1102edf09e9383ca761e407c619d13db7660121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34ca".hexToData;
    XCTAssertEqualObjects(blockchainUserTopupTransaction.inputSignatures[0],inputSignature,@"The transaction input signature isn't signing correctly");


    XCTAssertEqualObjects(blockchainUserTopupTransaction.data,hexData,@"The transaction data does not match it's expected values");
    XCTAssertEqualObjects([NSData dataWithUInt256:txId],[NSData dataWithUInt256:blockchainUserTopupTransaction.txHash],@"The transaction does not match it's desired private key");
}
 */

import org.bitcoinj.core.*;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;
import static org.junit.Assert.*;

public class SubTxTopUpTest {

    Context context;
    UnitTestParams PARAMS;
    byte[] txdata;

    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = Context.getOrCreate(PARAMS);
        txdata = Utils.HEX.decode("0300090001d4ad073ec40da120d28a47164753f4f5ad80d0dc3b918b39223d36ebdfacdef6000000006b483045022100a65429d4f2ab2df58cafdaaffe874ef260f610e068e89a4455fbf92261156bb7022015733ae5aef3006fd5781b91f97ca1102edf09e9383ca761e407c619d13db7660121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34cafeffffff0200e1f50500000000016ad2d327cc050000001976a9141eccbe2508c7741d2e4c517f87565e7d477cfbbc88ac000000002201002369fced72076b33e25c5ca31efb605037e3377c8e1989eb9ec968224d5e22b4");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";
    }

    @Test
    public void verifyTest() {
        Sha256Hash txId = Sha256Hash.wrap("715f96a80e0e4feb8a94f2e9f4f6821dd4502f0ae6c43013ec6e77985d059b55");
        Sha256Hash blockchainUserRegistrationTransactionHash = Sha256Hash.wrap("b4225e4d2268c99eeb89198e7c37e3375060fb1ea35c5ce2336b0772edfc6923");
        Address inputAddress = Address.fromBase58(PARAMS, "yYZqfmQhqMSF1PL7xeNHzQM3q9rktXFPLN");
        String inputPrivateKey = "cNYPkC4hGoE11ieBr2GgwyUct8zY1HLi5S5K2LLPMewtQGJsbu9H";
        ECKey privateKey = DumpedPrivateKey.fromBase58(PARAMS, inputPrivateKey).getKey();

        assertEquals(inputAddress, privateKey.toAddress(PARAMS));

        Transaction tx = new Transaction(PARAMS, txdata);
        SubTxTopup subtx = (SubTxTopup) tx.getExtraPayloadObject();
        assertEquals(txId, tx.getHash());
        assertEquals(1, subtx.version);
        assertEquals(blockchainUserRegistrationTransactionHash, subtx.getRegTxId());

        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(txdata.length);
            tx.bitcoinSerialize(stream);
            assertArrayEquals("Blockchain user transaction does not match it's data", txdata, stream.toByteArray());
        } catch (IOException x) {
            fail();
        }
    }

    @Test
    public void createSubTxTopup() {
        Sha256Hash blockchainUserRegistrationTransactionHash = Sha256Hash.wrap("b4225e4d2268c99eeb89198e7c37e3375060fb1ea35c5ce2336b0772edfc6923");
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
        assertEquals(txId, tx.getHash());

        byte [] inputScript = Utils.HEX.decode("483045022100a65429d4f2ab2df58cafdaaffe874ef260f610e068e89a4455fbf92261156bb7022015733ae5aef3006fd5781b91f97ca1102edf09e9383ca761e407c619d13db7660121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34ca");

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
