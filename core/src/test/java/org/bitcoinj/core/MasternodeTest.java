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
                "03ea9f31d92ee60d3f9cb2a86dfafdda51ba7f05a3a4aeb5fa12c2ffae0eab830000000000000000000000000000ffff34c917814e1f2102d3b5834899c4479fe6eee3a1775d7b932f4e8d341f19a26453bc5b2a3fbb322c4104acb47355c2968cdf79d1ef17950bc0a896d674c4db62a3b0778181fc9cc8d864d8ca5c5f4325754d8c9f83038779b1d2ff755459b89596bb738615eed5c07610412024baf946c849c9a2dbddf8bb21059d9f6f3a9442b382c20012f15990e8a4308200b892e1a23e6245cbfa6596d416dd1741f01752119f909e2ceee78e7b6653488e5e3b5b00000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000000401201000000000000000000000000");

        PublicKey collateralAddress = new PublicKey(Utils.HEX.decode("02d3b5834899c4479fe6eee3a1775d7b932f4e8d341f19a26453bc5b2a3fbb322c"));

        Masternode mn = new Masternode(MainNetParams.get(), mndata, 0);
        MasternodeInfo info = mn.getInfo();
        assertEquals(info.nLastDsq, 0);
        assertEquals(info.sigTime, 1530617486L);
        assertEquals(info.nProtocolVersion, 70208);
        assertEquals(info.pubKeyCollateralAddress, collateralAddress);
        assertEquals(mn.mapGovernanceObjectsVotedOn.size(), 0);


    }
}
