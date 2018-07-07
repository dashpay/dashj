package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Hash Engineering on 6/21/2016.
 */
public class MasternodeBroadcastTest {

    Context context;

    @Before
    public void setUp()
    {
        context = new Context(MainNetParams.get());
    }
    @Test
    public void testParse() {
        byte [] mnbdata = Utils.HEX.decode(
                "0004c0a6aba914fed6ff974027d931283bdc017c2b8c4c4a3eeb7a39b4bd0f0d0100000000ffffffff00000000000000000000ffff6deb4643270f21021b29b3f20cabb7209410b32fe105fd00c92c94ec76f46c96712313da1d201a174104f784ec878fbae081d37dde6583ccbded6535539d3a20237fa5f657cdc3e62de12ad4179a030343a08b4d703ef6fc96b14f40b039808c47e93be38a109d2b0b19411f044b41b6fc8cdc62a7a25d3d7fdc985cc44a92e15eac2ccfa2c83700fca47a566c7a60db0176206ca7ff8ac42dfcba1689e01bc1efaebb1baf79584be77ba17320fe125a00000000401201000004c0a6aba914fed6ff974027d931283bdc017c2b8c4c4a3eeb7a39b4bd0f0d0100000000ffffffff5883ddbb4f59d5211f9e8153a1b16ec8102727c2dee8a9044000000000000000acbd035b00000000411b054ca61d7f16cc3af9e90b995973969fda2e4619e82eecb6d15c63543047ea9b77388242e7fe9de82588d16d0e5c8d92e60eacb92824ef366816d146f52c68b10001000100");

        PublicKey collateralAddress = new PublicKey(Utils.HEX.decode("021b29b3f20cabb7209410b32fe105fd00c92c94ec76f46c96712313da1d201a17"));

        MasternodeBroadcast mn = new MasternodeBroadcast(MainNetParams.get(), mnbdata, 0);
        MasternodeInfo info = mn.getInfo();
        assertEquals(info.nLastDsq, 0);
        assertEquals(info.sigTime, 1511194144);
        assertEquals(mn.protocolVersion, 70208);
        assertEquals(info.pubKeyCollateralAddress, collateralAddress);
        assertEquals(mn.lastPing.getHash(), Sha256Hash.wrap(Utils.HEX.decode("1b060341f424c156d878837262a6c8e782dead55506f07af43fe69914b1d47fb")));
    }
}
