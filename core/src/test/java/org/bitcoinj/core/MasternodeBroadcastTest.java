package org.bitcoinj.core;

import org.bitcoinj.net.Dos;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Hash Engineering on 6/21/2016.
 */
public class MasternodeBroadcastTest {

    Context context;

    @Before
    public void setUp() {
        context = new Context(MainNetParams.get());
        context.initDash(true, true);
        context.sporkManager.processSporkForUnitTesting(SporkManager.SPORK_6_NEW_SIGS);
    }
    //version 70208
    @Ignore
    public void testParse() {
        byte [] mnbdata = Utils.HEX.decode(
                "0004c0a6aba914fed6ff974027d931283bdc017c2b8c4c4a3eeb7a39b4bd0f0d0100000000ffffffff00000000000000000000ffff6deb4643270f21021b29b3f20cabb7209410b32fe105fd00c92c94ec76f46c96712313da1d201a174104f784ec878fbae081d37dde6583ccbded6535539d3a20237fa5f657cdc3e62de12ad4179a030343a08b4d703ef6fc96b14f40b039808c47e93be38a109d2b0b19411f044b41b6fc8cdc62a7a25d3d7fdc985cc44a92e15eac2ccfa2c83700fca47a566c7a60db0176206ca7ff8ac42dfcba1689e01bc1efaebb1baf79584be77ba17320fe125a00000000401201000004c0a6aba914fed6ff974027d931283bdc017c2b8c4c4a3eeb7a39b4bd0f0d0100000000ffffffff5883ddbb4f59d5211f9e8153a1b16ec8102727c2dee8a9044000000000000000acbd035b00000000411b054ca61d7f16cc3af9e90b995973969fda2e4619e82eecb6d15c63543047ea9b77388242e7fe9de82588d16d0e5c8d92e60eacb92824ef366816d146f52c68b10001000100");

        PublicKey collateralAddress = new PublicKey(Utils.HEX.decode("021b29b3f20cabb7209410b32fe105fd00c92c94ec76f46c96712313da1d201a17"));

        MasternodeBroadcast mn = new MasternodeBroadcast(MainNetParams.get(), mnbdata, 0);
        MasternodeInfo info = mn.getInfo();
        assertEquals(info.nLastDsq, 0);
        assertEquals(info.sigTime, 1511194144);
        assertEquals(info.nProtocolVersion, 70208);
        assertEquals(info.pubKeyCollateralAddress, collateralAddress);
        assertEquals(mn.lastPing.getHash(), Sha256Hash.wrap(Utils.HEX.decode("1b060341f424c156d878837262a6c8e782dead55506f07af43fe69914b1d47fb")));
    }
    @Test
    public void testParseWithSpork6() {
        byte [] mnbdata = Utils.HEX.decode(
                "0313a1b4dd8780ee7ac2acf4695bb2c9d2b8bd2870671d6b36a0cd96fb7b15bd0000000000000000000000000000ffff22db97654a3921024844d13d64dd612147474caeb3ae9eff50daffec4cea17312ce3a8191573af5141048c17c4728e67613d97508973693a20dd8387a6076fa42ca74e680b785636eaacf5ae3a18fcb8989b3ad4b9a3f5543388456013589729a657a402cedc23c25800411f57b98f37fb18c48c94835d3ca5c0f87f321b5226748e0f2f79b419850df051a95cc2466072e7d0014d4d12e4748d41574f6a5e96751dc8fc5a79b990e3e8e52642d63d5b00000000421201000313a1b4dd8780ee7ac2acf4695bb2c9d2b8bd2870671d6b36a0cd96fb7b15bd00000000811e02f95a860ccd4a777a369ded43610d02c2dcbab19a1d3b2764410000000042d63d5b00000000411b4ae1e49978d591ab05f85ba2bffa700dc160636d55bb746260df53ecdcbfb8dc35f389b9ed7ffd3074d95a1f196392548bb3caaeaa5aa39bb5457550a9152e91000100010088d50100");
        PublicKey collateralAddress = new PublicKey(Utils.HEX.decode("024844d13d64dd612147474caeb3ae9eff50daffec4cea17312ce3a8191573af51"));

        MasternodeBroadcast mn = new MasternodeBroadcast(MainNetParams.get(), mnbdata, 0);
        MasternodeInfo info = mn.getInfo();
        assertEquals(info.nLastDsq, 0);
        assertEquals(info.sigTime, 1530779202);
        assertEquals(info.nProtocolVersion, 70210);
        assertEquals(info.pubKeyCollateralAddress, collateralAddress);
        assertEquals(mn.lastPing.getHash(), Sha256Hash.wrap(Utils.HEX.decode("43adcd15e54345cbfa6ca8ab96c35989acfb0c3f82a23724841decf0d53c2997")));

        assert(mn.checkSignature(new Dos()));
    }
}
