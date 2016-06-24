package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
/**
 * Created by Eric on 6/21/2016.
 */
public class MasternodeTest {

    MainNetParams params = MainNetParams.get();

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
        TransactionInput vin = new TransactionInput(params,null, new byte[0],
                new TransactionOutPoint(params, 0,
                        Sha256Hash.wrap(Utils.HEX.decode("b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb"))));

        Sha256Hash hash;// = context.masternodeManager.getBlockHash(nBlockHeight);

        hash = Sha256Hash.wrap(Utils.HEX.decode("00000000000642c0b18cafc97a23ffd6e5eeb0a63b600a0d3f9630a93b674ae0"));

        assertEquals(Masternode.calculateScore(vin, hash), Sha256Hash.wrap(Utils.HEX.decode("83f1287faaf5e5deb3058112b27a38b523f5324a37e6b00f0a5594b55109ac46"))) ;


    }
}
