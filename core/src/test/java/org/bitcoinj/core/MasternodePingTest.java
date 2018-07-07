package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Hash Engineering on 6/21/2016.
 */
public class MasternodePingTest {

    Context context;

    @Before
    public void setUp()
    {
        context = new Context(MainNetParams.get());
    }


    @Test
    public void testParse() {
        byte [] mnpdata = Utils.HEX.decode(
                "0004c0a6aba914fed6ff974027d931283bdc017c2b8c4c4a3eeb7a39b4bd0f0d0100000000ffffffff5883ddbb4f59d5211f9e8153a1b16ec8102727c2dee8a9044000000000000000acbd035b00000000411b054ca61d7f16cc3af9e90b995973969fda2e4619e82eecb6d15c63543047ea9b77388242e7fe9de82588d16d0e5c8d92e60eacb92824ef366816d146f52c68b10001000100");

        Sha256Hash blockHash = Sha256Hash.wrap("000000000000004004a9e8dec2272710c86eb1a153819e1f21d5594fbbdd8358");

        MasternodePing mnp = new MasternodePing(MainNetParams.get(), mnpdata, 0);
        assertEquals(mnp.blockHash, blockHash);
        assertEquals(mnp.getHash(), Sha256Hash.wrap(Utils.HEX.decode("1b060341f424c156d878837262a6c8e782dead55506f07af43fe69914b1d47fb")));
    }
}
