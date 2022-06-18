package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;


import static org.junit.Assert.*;

/**
 * Created by hashengineering on 04/11/19.
 */
public class FinalCommitmentTest {

    Context context;
    UnitTestParams PARAMS;


    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = new Context(PARAMS);
    }

    /*
        getrawtransaction ee8bc537907d65e71599372deab7038ee48f8ed3c7ba79ac4492785f2e451735 true
        {
         "hex": "03000600000000000000fd490101004c370100010001ae1022a3871ce8a74b958b45b88885ecea8a172f428bf535b774e7060000000032ffefffffffff0332ffefffffffff03160b120058893acc8b6622dfd210f7d00aed97a43b364da4073b791c23b1e8b4b4c7943bf5b3b4c62c0108b391351a43f421c008fe77374e79b9bbcb3e7916f063083f936585c1793d0e887c537be5d70a4014216fe9d1c8c8e92208dfe167773c0da8b6d012390b8608ec8783a6ba3a2deeb03606a7133d6b8b0f11a7d458b210f03421f3b97cfdb73fdbd24ecca58568ca637aa6dbce271fd5d49a3d6ae63410365c38bda1dc73e683c0ad03b9e28e1999c3b590e188420eb0104087a8fe58de9ebb1f1daacfdc1d1492b00287b3dd2972eb76623d6f362b220d871d46eaae00ff594ab37a5a313c436838026b0052c5ce30bd4c1668b0165f935a0fed4f9b1921de6ceb8a347ba9fff4a9cb20762a",
         "txid": "ee8bc537907d65e71599372deab7038ee48f8ed3c7ba79ac4492785f2e451735",
         "size": 342,
         "version": 3,
         "type": 6,
         "locktime": 0,
         "vin": [
         ],
         "vout": [
         ],
         "extraPayloadSize": 329,
         "extraPayload": "01004c370100010001ae1022a3871ce8a74b958b45b88885ecea8a172f428bf535b774e7060000000032ffefffffffff0332ffefffffffff03160b120058893acc8b6622dfd210f7d00aed97a43b364da4073b791c23b1e8b4b4c7943bf5b3b4c62c0108b391351a43f421c008fe77374e79b9bbcb3e7916f063083f936585c1793d0e887c537be5d70a4014216fe9d1c8c8e92208dfe167773c0da8b6d012390b8608ec8783a6ba3a2deeb03606a7133d6b8b0f11a7d458b210f03421f3b97cfdb73fdbd24ecca58568ca637aa6dbce271fd5d49a3d6ae63410365c38bda1dc73e683c0ad03b9e28e1999c3b590e188420eb0104087a8fe58de9ebb1f1daacfdc1d1492b00287b3dd2972eb76623d6f362b220d871d46eaae00ff594ab37a5a313c436838026b0052c5ce30bd4c1668b0165f935a0fed4f9b1921de6ceb8a347ba9fff4a9cb20762a",
         "qcTx": {
           "version": 1,
           "height": 79692,
           "commitment": {
             "version": 1,
             "llmqType": 1,
             "quorumHash": "0000000006e774b735f58b422f178aeaec8588b8458b954ba7e81c87a32210ae",
             "signersCount": 49,
             "validMembersCount": 49,
             "quorumPublicKey": "160b120058893acc8b6622dfd210f7d00aed97a43b364da4073b791c23b1e8b4b4c7943bf5b3b4c62c0108b391351a43"
           }
         },
         "blockhash": "00000000005ee008c3a73f992e8e589de8185b1f8b27b5f0c1b1cdb0d9c089a1",
         "height": 79692,
         "confirmations": 12,
         "time": 1555218553,
         "blocktime": 1555218553,
         "instantlock": false,
         "chainlock": false
        }
     */
    @Test
    public void finalCommitmentTest() throws IOException {

        byte [] txdata = Utils.HEX.decode("03000600000000000000fd490101004c370100010001ae1022a3871ce8a74b958b45b88885ecea8a172f428bf535b774e7060000000032ffefffffffff0332ffefffffffff03160b120058893acc8b6622dfd210f7d00aed97a43b364da4073b791c23b1e8b4b4c7943bf5b3b4c62c0108b391351a43f421c008fe77374e79b9bbcb3e7916f063083f936585c1793d0e887c537be5d70a4014216fe9d1c8c8e92208dfe167773c0da8b6d012390b8608ec8783a6ba3a2deeb03606a7133d6b8b0f11a7d458b210f03421f3b97cfdb73fdbd24ecca58568ca637aa6dbce271fd5d49a3d6ae63410365c38bda1dc73e683c0ad03b9e28e1999c3b590e188420eb0104087a8fe58de9ebb1f1daacfdc1d1492b00287b3dd2972eb76623d6f362b220d871d46eaae00ff594ab37a5a313c436838026b0052c5ce30bd4c1668b0165f935a0fed4f9b1921de6ceb8a347ba9fff4a9cb20762a");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";

        Transaction commitmentTx = new Transaction(PARAMS, txdata);

        FinalCommitmentTxPayload payload = (FinalCommitmentTxPayload)commitmentTx.getExtraPayloadObject();

        assertEquals(329, payload.getMessageSize());
        assertEquals(1, payload.getVersion());
        assertEquals(79692, payload.height);

        FinalCommitment commitment = payload.commitment;
        assertEquals(1, commitment.getVersion());
        assertEquals(1, commitment.llmqType);
        assertEquals(Sha256Hash.wrap("0000000006e774b735f58b422f178aeaec8588b8458b954ba7e81c87a32210ae"), commitment.quorumHash);
        assertEquals(49, commitment.countSigners());
        assertEquals(49, commitment.countValidMembers());

        BLSPublicKey quorumPublickKey = new BLSPublicKey(PARAMS, Utils.HEX.decode("160b120058893acc8b6622dfd210f7d00aed97a43b364da4073b791c23b1e8b4b4c7943bf5b3b4c62c0108b391351a43"), 0);
        assertEquals(quorumPublickKey, commitment.quorumPublicKey);

        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(txdata.length);
        assertArrayEquals(commitmentTx.getExtraPayload(), payload.getPayload());
        commitmentTx.setExtraPayload(payload.getPayload());
        commitmentTx.bitcoinSerialize(bos);
        assertArrayEquals(txdata, bos.toByteArray());
    }

    /*
      getrawtransaction f9776bfdea91c60149110e536ae51e8bfdfff3fdec3c4fecb7081132f335abbd true
      {
       "hex": "03000600000000000000fda301010056190100010002f2ef5ab062b348bfcf9f5be4c07b817e176a5726fa9b799ad67f690700000000fd9001bf7fffaffedffef77fef7ffffffcbdffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f0000000000fd9001bf7fffaffedffef77fef7ffffffcbfffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f000000000003a3fbbe99d80a9be8fc59fd4fe43dfbeba9119b688e97493664716cdf15ae47fad70fea7cb93f20fba10d689f9e3c02a2263a396ef747508bf75a2dd7f891abb0fc4fb58b6773d131eb0403126bdebe9944c544e03a478b401b65cabbb24338872613f7d58ff13ab038ab86418ec70ef1734ff43e965ccb83e02da83b10d44c0f23c630752cfb29b402149a1fc3fad0760e6341a4a1031efad2983c8637d2a461e9bcaf935b7a4dfa225ed2f7771c7592eda5c13583577719bea9337b4b9b6286ac11a072de0955b0dc5a012280bb557a53f9643cee7730dabe2d3a4a19042813ef5d39ae92d0015554954011c1e12bc688d4d7672ac33c4001e0dedbfe5d0316f2ad23206d478964ca62d75f50e4d0",
       "txid": "f9776bfdea91c60149110e536ae51e8bfdfff3fdec3c4fecb7081132f335abbd",
       "size": 432,
       "version": 3,
       "type": 6,
       "locktime": 0,
       "vin": [
       ],
       "vout": [
       ],
       "extraPayloadSize": 419,
       "extraPayload": "010056190100010002f2ef5ab062b348bfcf9f5be4c07b817e176a5726fa9b799ad67f690700000000fd9001bf7fffaffedffef77fef7ffffffcbdffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f0000000000fd9001bf7fffaffedffef77fef7ffffffcbfffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f000000000003a3fbbe99d80a9be8fc59fd4fe43dfbeba9119b688e97493664716cdf15ae47fad70fea7cb93f20fba10d689f9e3c02a2263a396ef747508bf75a2dd7f891abb0fc4fb58b6773d131eb0403126bdebe9944c544e03a478b401b65cabbb24338872613f7d58ff13ab038ab86418ec70ef1734ff43e965ccb83e02da83b10d44c0f23c630752cfb29b402149a1fc3fad0760e6341a4a1031efad2983c8637d2a461e9bcaf935b7a4dfa225ed2f7771c7592eda5c13583577719bea9337b4b9b6286ac11a072de0955b0dc5a012280bb557a53f9643cee7730dabe2d3a4a19042813ef5d39ae92d0015554954011c1e12bc688d4d7672ac33c4001e0dedbfe5d0316f2ad23206d478964ca62d75f50e4d0",
       "qcTx": {
         "version": 1,
         "height": 72022,
         "commitment": {
           "version": 1,
           "llmqType": 2,
           "quorumHash": "0000000007697fd69a799bfa26576a177e817bc0e45b9fcfbf48b362b05aeff2",
           "signersCount": 321,
           "validMembersCount": 322,
           "quorumPublicKey": "03a3fbbe99d80a9be8fc59fd4fe43dfbeba9119b688e97493664716cdf15ae47fad70fea7cb93f20fba10d689f9e3c02"
         }
       },
       "blockhash": "00000000014d910dca80944b52aa3f522d5604254043b8354d641912aace4343",
       "height": 72022,
       "confirmations": 7718,
       "time": 1554173458,
       "blocktime": 1554173458,
       "instantlock": false,
       "chainlock": true
      }
     */

    @Test
    public void finalCommitmentTestTwo() throws IOException {

        byte [] txdata = Utils.HEX.decode("03000600000000000000fda301010056190100010002f2ef5ab062b348bfcf9f5be4c07b817e176a5726fa9b799ad67f690700000000fd9001bf7fffaffedffef77fef7ffffffcbdffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f0000000000fd9001bf7fffaffedffef77fef7ffffffcbfffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f000000000003a3fbbe99d80a9be8fc59fd4fe43dfbeba9119b688e97493664716cdf15ae47fad70fea7cb93f20fba10d689f9e3c02a2263a396ef747508bf75a2dd7f891abb0fc4fb58b6773d131eb0403126bdebe9944c544e03a478b401b65cabbb24338872613f7d58ff13ab038ab86418ec70ef1734ff43e965ccb83e02da83b10d44c0f23c630752cfb29b402149a1fc3fad0760e6341a4a1031efad2983c8637d2a461e9bcaf935b7a4dfa225ed2f7771c7592eda5c13583577719bea9337b4b9b6286ac11a072de0955b0dc5a012280bb557a53f9643cee7730dabe2d3a4a19042813ef5d39ae92d0015554954011c1e12bc688d4d7672ac33c4001e0dedbfe5d0316f2ad23206d478964ca62d75f50e4d0");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";

        Transaction commitmentTx = new Transaction(PARAMS, txdata);

        FinalCommitmentTxPayload payload = (FinalCommitmentTxPayload)commitmentTx.getExtraPayloadObject();

        assertEquals(419, payload.getMessageSize());
        assertEquals(1, payload.getVersion());
        assertEquals(72022, payload.height);

        FinalCommitment commitment = payload.commitment;
        assertEquals(1, commitment.getVersion());
        assertEquals(2, commitment.llmqType);
        assertEquals(Sha256Hash.wrap("0000000007697fd69a799bfa26576a177e817bc0e45b9fcfbf48b362b05aeff2"), commitment.quorumHash);
        assertEquals(321, commitment.countSigners());
        assertEquals(322, commitment.countValidMembers());

        BLSPublicKey quorumPublickKey = new BLSPublicKey(PARAMS, Utils.HEX.decode("03a3fbbe99d80a9be8fc59fd4fe43dfbeba9119b688e97493664716cdf15ae47fad70fea7cb93f20fba10d689f9e3c02"), 0);
        assertEquals(quorumPublickKey, commitment.quorumPublicKey);

        assertArrayEquals(commitmentTx.getExtraPayload(), payload.getPayload());
        // round trip
        assertArrayEquals(txdata, commitmentTx.bitcoinSerialize());
    }

    @Test
    public void finalCommitmentHashTest() {
        byte [] data = Utils.HEX.decode("010002f2ef5ab062b348bfcf9f5be4c07b817e176a5726fa9b799ad67f690700000000fd9001bf7fffaffedffef77fef7ffffffcbdffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f0000000000fd9001bf7fffaffedffef77fef7ffffffcbfffaffffffffffffdfffff7f7f7fff7ffefbfffffdff1fdbf7feffcffbb1f000000000003a3fbbe99d80a9be8fc59fd4fe43dfbeba9119b688e97493664716cdf15ae47fad70fea7cb93f20fba10d689f9e3c02a2263a396ef747508bf75a2dd7f891abb0fc4fb58b6773d131eb0403126bdebe9944c544e03a478b401b65cabbb24338872613f7d58ff13ab038ab86418ec70ef1734ff43e965ccb83e02da83b10d44c0f23c630752cfb29b402149a1fc3fad0760e6341a4a1031efad2983c8637d2a461e9bcaf935b7a4dfa225ed2f7771c7592eda5c13583577719bea9337b4b9b6286ac11a072de0955b0dc5a012280bb557a53f9643cee7730dabe2d3a4a19042813ef5d39ae92d0015554954011c1e12bc688d4d7672ac33c4001e0dedbfe5d0316f2ad23206d478964ca62d75f50e4d0");
        FinalCommitment commitment = new FinalCommitment(PARAMS, data, 0);

        Sha256Hash commitmentHash = commitment.getHash();
        assertEquals(Sha256Hash.wrap("082f5e29385f81704ef63c886aa20c2f8d69efd87d3937d6769285e2ead9ea0f"), commitmentHash);
    }

    @Test
    public void finalCommitmentV2Test() throws IOException {

        byte [] qfcommit = Utils.HEX.decode("02006530fed4f6262e0fd7314cccbe94d7aba54f3cdcbe2bd3237ad2f276eb4d01000003000cfd0b0cfd0b0e22ac5b7c87076a03b1e4bee58c8404aaed183dd2c3114cea3ac1cbf85218a6196a19073789e9a12fc439b773842368b70d64f275bfc681e393286844bc565ceb51555d061c53e846bdd623e3809b5619d03a92ab89a5758598d4bef96cc398e233f04cfdfba3842098813d7532960527d76656b3cc1dbae0bd07898b2d31660824be7c8baef965d9eeaef4b8cc819e1e60b4739a5302d7bcce658d31c75c74a9245d6a44ff36d7df5ced7fca3117578e5b2bcd12e993666bdfce0c390d7b849b901b3699902a3c2c07cc269910b2f9483ab70e52d6ffbe68e1c012df67840e129b19052f6ddf1880a3c8a05b6cb9a38ca640b5fcf4fcb422b3bcc7c665c703fc258443ce0400580578af74f42ca656");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";

        FinalCommitment commitment = new FinalCommitment(PARAMS, qfcommit, 0);

        assertEquals(2, commitment.getVersion());
        assertEquals(101, commitment.llmqType);
        assertEquals(3, commitment.quorumIndex);
        assertEquals(Sha256Hash.wrap("0000014deb76f2d27a23d32bbedc3c4fa5abd794becc4c31d70f2e26f6d4fe30"), commitment.quorumHash);
        assertEquals(10, commitment.countSigners());
        assertEquals(10, commitment.countValidMembers());

        BLSPublicKey quorumPublickKey = new BLSPublicKey(PARAMS, Utils.HEX.decode("0e22ac5b7c87076a03b1e4bee58c8404aaed183dd2c3114cea3ac1cbf85218a6196a19073789e9a12fc439b773842368"), 0);
        assertEquals(quorumPublickKey, commitment.quorumPublicKey);

        // round trip
        assertArrayEquals(qfcommit, commitment.bitcoinSerialize());
    }
}
