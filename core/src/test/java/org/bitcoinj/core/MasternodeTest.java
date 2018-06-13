package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
/**
 * Created by Eric on 6/21/2016.
 */
public class MasternodeTest {

    Context context;

    @Before
    public void setUp()
    {
        context = new Context(MainNetParams.get());
    }

    @Test
    public void testCalculateScore()
    {
        //if(blockChain.getChainHead() == null)
        //    return Sha256Hash.ZERO_HASH;

        /*
CalculateScore:-------
, vin=CTxIn(COutPoint(b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb, 0), scriptSig=)
vin.prevout.hash=b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb
vin.prevout.n=0
hash=00000000000642c0b18cafc97a23ffd6e5eeb0a63b600a0d3f9630a93b674ae0
aux=b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb
2016-03-01 07:37:39 hash2=8802d328293c18864b4c2e5d4de40f21e650ceb3ce55414ce54e2e321d33b75c
2016-03-01 07:37:39 hash3=0411aaa87e4632a79846ad4a9b69d66cc25b9c69966e913ddaf8997ccc2a0b16
2016-03-01 07:37:39 r=83f1287faaf5e5deb3058112b27a38b523f5324a37e6b00f0a5594b55109ac46 (hash2-hash3)
                      83f1287faaf5e5deb3058112b27a38b523f5324a37e6b00f0a5594b55109ac46
*/
        TransactionInput vin = new TransactionInput(context.getParams(),null, new byte[0],
                new TransactionOutPoint(context.getParams(), 0,
                        Sha256Hash.wrap(Utils.HEX.decode("b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb"))));

        Sha256Hash hash;// = context.masternodeManager.getBlockHash(nBlockHeight);

        hash = Sha256Hash.wrap(Utils.HEX.decode("00000000000642c0b18cafc97a23ffd6e5eeb0a63b600a0d3f9630a93b674ae0"));

        assertEquals(Masternode.calculateScore(vin, hash), Sha256Hash.wrap(Utils.HEX.decode("83f1287faaf5e5deb3058112b27a38b523f5324a37e6b00f0a5594b55109ac46"))) ;


    }

    @Test
    public void testParse() {
        byte [] mndata = Utils.HEX.decode(
                "663945fdd0b1872325af5c98f8c8be4ce02e221cea655a97e7ec34f1d69d5cea0100000000ffffffff00000000000000000000" +
                "ffff2d20813e270f2103e70a389b7e4e32fbc73ecfdcdf5713c1f2729d4b348be8425618645cba755daf4104c89d541daefe31b69f0307" +
                "b370e7aa66926d14062164327ffe1fd66cf5d35b9add273f30037e923003316e75a5e8d6a03f16dd9a71e227b89f7392d25a54e96f4120" +
                "ae7538c989c65a29a3f004c869bf281ffc820eb401e111c4aef88f592638accb37b5cff3d334486583bd9b166d6a1f858d7eb58c77bbb4" +
                "634224b2fa5d2a19adefa6065a000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000000000000000000000000000000000000000000000000000000000401201000000000000000000000000");

        PublicKey collateralAddress = new PublicKey(Utils.HEX.decode("03e70a389b7e4e32fbc73ecfdcdf5713c1f2729d4b348be8425618645cba755daf"));

        Masternode mn = new Masternode(MainNetParams.get(), mndata, 0);
        MasternodeInfo info = mn.getInfo();
        assertEquals(info.nLastDsq, 0);
        assertEquals(info.sigTime, 1510385391L);
        assertEquals(mn.protocolVersion, 70208);
        assertEquals(info.pubKeyCollateralAddress, collateralAddress);
        assertEquals(mn.mapGovernanceObjectsVotedOn.size(), 0);


    }
}
