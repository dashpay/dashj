package org.bitcoinj.evolution;

/*
- (void)testResetBlockchainUserTransactionInputs {
    //this is for v3 transaction versions
    DSChain * devnetDRA = [DSChain devnetWithIdentifier:@"devnet-DRA"];
    NSData * hexData = [NSData dataFromHexString:@"03000a00000000000000a00100659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370d659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370de803000000000000f6f5abf4ba75c554b9ef001a78c35ce5edb3ccb1411fd442ee3bb6dac571f432e56def3d06f64a15cc74f382184ca4d5d4cad781ced01ae4e8109411f548da5c5fa6bfce5a23a8d620104e6953600539728b95077e19"];
    UInt256 txId = *(UInt256 *)@"251961000a115bafbb7bdb6e1baf23d88e37ecf2fe6af5d9572884cabaecdcc0".hexToData.reverse.bytes;
    UInt256 blockchainUserRegistrationTransactionHash = *(UInt256 *)@"0d3701a0ef99acaf10158b9891c24d84600930824566063a81b7caef43329c65".hexToData.reverse.bytes;
    UInt256 blockchainUserPreviousTransactionHash = *(UInt256 *)@"0d3701a0ef99acaf10158b9891c24d84600930824566063a81b7caef43329c65".hexToData.reverse.bytes;

    DSKey * payloadKey = [DSKey keyWithPrivateKey:@"cVxAzue29NemggDqJyUwMsZ7KJsm4y9ntoW5UeCaTfQdruH2BKQR" onChain:devnetDRA];
    NSString * payloadAddress = @"yfguWspuwx7ceKthnqqDc8CiZGZGRN7eFp";
    NSString * checkPayloadAddress = [payloadKey addressForChain:devnetDRA];
    XCTAssertEqualObjects(checkPayloadAddress,payloadAddress,@"Payload key does not match input address");

    DSKey * replacementPayloadKey = [DSKey keyWithPrivateKey:@"cPG7GuByFnYkGvkrZqw8chGNfJYmKYnXt6TBjHruaApC42CPwwTE" onChain:devnetDRA];
    NSString * replacementPayloadAddress = @"yiqFNxn9kbWEKj7B87aEnoyChBL8rMFymt";
    UInt160 replacementPubkeyHash = *(UInt160 *)@"b1ccb3ede55cc3781a00efb954c575baf4abf5f6".hexToData.reverse.bytes;
    NSString * replacementCheckPayloadAddress = [replacementPayloadKey addressForChain:devnetDRA];
    XCTAssertEqualObjects(replacementCheckPayloadAddress,replacementPayloadAddress,@"Replacement payload key does not match input address");

    DSBlockchainUserResetTransaction *blockchainUserResetTransactionFromMessage = [[DSBlockchainUserResetTransaction alloc] initWithMessage:hexData onChain:devnetDRA];

    XCTAssertEqualObjects(blockchainUserResetTransactionFromMessage.toData,hexData,@"Blockchain user reset transaction does not match it's data");

    DSBlockchainUserResetTransaction *blockchainUserResetTransaction = [[DSBlockchainUserResetTransaction alloc] initWithInputHashes:@[] inputIndexes:@[] inputScripts:@[] inputSequences:@[] outputAddresses:@[] outputAmounts:@[] blockchainUserResetTransactionVersion:1 registrationTransactionHash:blockchainUserRegistrationTransactionHash previousBlockchainUserTransactionHash:blockchainUserPreviousTransactionHash replacementPublicKeyHash:replacementPubkeyHash creditFee:1000 onChain:devnetDRA];

    [blockchainUserResetTransaction signPayloadWithKey:payloadKey];
    NSData * payloadDataToConfirm = @"0100659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370d659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370de803000000000000f6f5abf4ba75c554b9ef001a78c35ce5edb3ccb1411fd442ee3bb6dac571f432e56def3d06f64a15cc74f382184ca4d5d4cad781ced01ae4e8109411f548da5c5fa6bfce5a23a8d620104e6953600539728b95077e19".hexToData;
    NSData * payloadData = blockchainUserResetTransaction.payloadData;
    XCTAssertEqualObjects(payloadData,payloadDataToConfirm,@"Payload Data does not match, signing payload does not work");


    XCTAssertEqualObjects(blockchainUserResetTransaction.data,hexData,@"The transaction data does not match it's expected values");
    XCTAssertEqualObjects([NSData dataWithUInt256:txId],[NSData dataWithUInt256:blockchainUserResetTransaction.txHash],@"The transaction does not match it's desired private key");
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

public class SubTxResetKeyTest {

    Context context;
    UnitTestParams PARAMS;
    byte[] txdata;

    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = Context.getOrCreate(PARAMS);
        txdata = Utils.HEX.decode("03000a00000000000000a00100659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370d659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370de803000000000000f6f5abf4ba75c554b9ef001a78c35ce5edb3ccb1411fd442ee3bb6dac571f432e56def3d06f64a15cc74f382184ca4d5d4cad781ced01ae4e8109411f548da5c5fa6bfce5a23a8d620104e6953600539728b95077e19");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";
    }

    @Test
    public void verifyTest() {
        Sha256Hash txId = Sha256Hash.wrap("251961000a115bafbb7bdb6e1baf23d88e37ecf2fe6af5d9572884cabaecdcc0");
        Sha256Hash blockchainUserRegistrationTransactionHash = Sha256Hash.wrap("0d3701a0ef99acaf10158b9891c24d84600930824566063a81b7caef43329c65");
        Sha256Hash blockchainUserPreviousTransactionHash = Sha256Hash.wrap("0d3701a0ef99acaf10158b9891c24d84600930824566063a81b7caef43329c65");

        ECKey payloadKey = DumpedPrivateKey.fromBase58(PARAMS,"cVxAzue29NemggDqJyUwMsZ7KJsm4y9ntoW5UeCaTfQdruH2BKQR").getKey();
        Address payloadAddress = Address.fromBase58(PARAMS, "yfguWspuwx7ceKthnqqDc8CiZGZGRN7eFp");
        assertEquals("Payload key does not match input address", payloadAddress, payloadKey.toAddress(PARAMS));

        ECKey replacementPayloadKey = DumpedPrivateKey.fromBase58(PARAMS,"cPG7GuByFnYkGvkrZqw8chGNfJYmKYnXt6TBjHruaApC42CPwwTE").getKey();
        Address replacementPayloadAddress = Address.fromBase58(PARAMS, "yiqFNxn9kbWEKj7B87aEnoyChBL8rMFymt");
        KeyId replacementPubkeyHash = new KeyId(Utils.reverseBytes(Utils.HEX.decode("b1ccb3ede55cc3781a00efb954c575baf4abf5f6")));


        Transaction tx = new Transaction(PARAMS, txdata);
        SubTxResetKey resetKey = (SubTxResetKey)tx.getExtraPayloadObject();
        assertEquals(txId, tx.getHash());
        assertEquals(replacementPubkeyHash, resetKey.newPubKeyId);
        assertEquals(blockchainUserRegistrationTransactionHash, resetKey.regTxId);
        assertEquals(blockchainUserPreviousTransactionHash, resetKey.hashPrevSubTx);
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(tx.getMessageSize());
            tx.bitcoinSerialize(bos);
            assertArrayEquals("Blockchain user reset transaction does not match it's data", txdata, bos.toByteArray());
        } catch (IOException x) {
            fail(x.getMessage());
        }
    }

    @Test
    public void createSubTxResetKey() {
        Sha256Hash txId = Sha256Hash.wrap("251961000a115bafbb7bdb6e1baf23d88e37ecf2fe6af5d9572884cabaecdcc0");
        Sha256Hash blockchainUserRegistrationTransactionHash = Sha256Hash.wrap("0d3701a0ef99acaf10158b9891c24d84600930824566063a81b7caef43329c65");
        Sha256Hash blockchainUserPreviousTransactionHash = Sha256Hash.wrap("0d3701a0ef99acaf10158b9891c24d84600930824566063a81b7caef43329c65");

        ECKey payloadKey = DumpedPrivateKey.fromBase58(PARAMS,"cVxAzue29NemggDqJyUwMsZ7KJsm4y9ntoW5UeCaTfQdruH2BKQR").getKey();
        Address payloadAddress = Address.fromBase58(PARAMS, "yfguWspuwx7ceKthnqqDc8CiZGZGRN7eFp");
        assertEquals("Payload key does not match input address", payloadAddress, payloadKey.toAddress(PARAMS));

        ECKey replacementPayloadKey = DumpedPrivateKey.fromBase58(PARAMS,"cPG7GuByFnYkGvkrZqw8chGNfJYmKYnXt6TBjHruaApC42CPwwTE").getKey();
        Address replacementPayloadAddress = Address.fromBase58(PARAMS, "yiqFNxn9kbWEKj7B87aEnoyChBL8rMFymt");
        KeyId replacementPubkeyId = new KeyId(Utils.reverseBytes(Utils.HEX.decode("b1ccb3ede55cc3781a00efb954c575baf4abf5f6")));
        assertArrayEquals(replacementPubkeyId.getBytes(), replacementPayloadKey.getPubKeyHash());

        Transaction tx = new Transaction(PARAMS);
        tx.setVersion(3);
        tx.setType(Transaction.Type.TRANSACTION_SUBTX_RESETKEY);

        SubTxResetKey resetKey = new SubTxResetKey(1, blockchainUserRegistrationTransactionHash,
                blockchainUserPreviousTransactionHash, Coin.valueOf(1000), replacementPubkeyId, payloadKey);

        tx.setExtraPayload(resetKey);
        byte [] payloadDataToConfirm = Utils.HEX.decode("0100659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370d659c3243efcab7813a06664582300960844dc291988b1510afac99efa001370de803000000000000f6f5abf4ba75c554b9ef001a78c35ce5edb3ccb1411fd442ee3bb6dac571f432e56def3d06f64a15cc74f382184ca4d5d4cad781ced01ae4e8109411f548da5c5fa6bfce5a23a8d620104e6953600539728b95077e19");
        assertArrayEquals("Payload Data does not match, signing payload does not work", payloadDataToConfirm, resetKey.getPayload());

         assertEquals(txId, tx.getHash());
        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(txdata.length);
            tx.bitcoinSerialize(stream);
            assertArrayEquals("resetkey transaction does not match it's data", txdata, stream.toByteArray());
        } catch (IOException x) {
            fail();
        }
    }
}
