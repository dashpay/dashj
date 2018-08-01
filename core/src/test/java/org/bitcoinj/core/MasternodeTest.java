package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Before;
import org.junit.Ignore;
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

    @Ignore
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
                      "09efcb66ff207c412e0c9817edc6ae73f4ee6ab69b78872f19c1e7f6da81ab530000000000000000000000000000ffff36bb35f04a3e21024844d13d64dd612147474caeb3ae9eff50daffec4cea17312ce3a8191573af5141042045b0853a5d097b39747b975a2e8cf952a7ac082367ba9b987158ec00a12e92848950444ddd416c7258268c91bffafdc0b14dd4877d488ce7abbe45bdc8469a411f1d574ccc8b7fe9ceba57d9d52e41665e0ac92e6db7fb01d733ff8bb7e59392973c27b199fa7532f1f3ad6a95ebc80685f8c48aa3cf7079650acc62f20f6647ebdddc5a5b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000421201000000000000000000000000");

        PublicKey collateralAddress = new PublicKey(Utils.HEX.decode("024844d13d64dd612147474caeb3ae9eff50daffec4cea17312ce3a8191573af51"));

        Masternode mn = new Masternode(MainNetParams.get(), mndata, 0);
        MasternodeInfo info = mn.getInfo();
        assertEquals(0, info.nLastDsq);
        assertEquals(1532681437, info.sigTime);
        assertEquals(70210, info.nProtocolVersion);
        assertEquals(collateralAddress, info.pubKeyCollateralAddress);
        assertEquals(false, mn.fAllowMixingTx);
        assertEquals(0, mn.mapGovernanceObjectsVotedOn.size());


    }
}
