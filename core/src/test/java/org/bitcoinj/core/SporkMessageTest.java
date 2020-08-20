package org.bitcoinj.core;

import com.google.common.base.Preconditions;
import org.bitcoinj.params.MainNetParams;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SporkMessageTest {
    static NetworkParameters PARAMS = MainNetParams.get();
    static Context context = new Context(PARAMS);

    static {
        context.initDash(true, true);
    }

    @Test
    public void verifySpork() {
        byte [] sporkData = Utils.HEX.decode("1f27000000000000000000007192a45c00000000411c3acf25f6c7b4af6e7919bd5a988335228955312301622f1a576712a1bb146c347283595ab601f7263379ec7e8b03c279c785313043b4deb38a107fc5ccddff90");
        Sha256Hash sporkHash = Sha256Hash.wrap("c89a674297530b1b9f4d1ed2aaabb89112de687999c881dae0bb389af148a8b0");
        SporkMessage sporkMessage = new SporkMessage(PARAMS, sporkData, 0);

        assertEquals(10015, sporkMessage.getSporkId());
        assertEquals(0, sporkMessage.getValue());
        assertEquals(1554289265L, sporkMessage.getTimeSigned());
        assertEquals(sporkHash, sporkMessage.getSignatureHash());

        assertTrue(sporkMessage.checkSignature(Address.fromString(PARAMS, PARAMS.getSporkAddress()).getHash()));
    }
}
