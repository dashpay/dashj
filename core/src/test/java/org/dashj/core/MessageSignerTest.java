package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by HashEngineering on 5/9/2018.
 */
public class MessageSignerTest {
    Context context;

    @Before
    public void setUp()
    {
        context = new Context(MainNetParams.get());
    }

    @Test
    public void verifySignatureTest()
    {
        PublicKey publicKey = new PublicKey(Utils.HEX.decode("02cf6cecbac4b6b541ba06c7b58477297a06b2295cf9e2a95566241c287096b895"));
        MasternodeSignature vchSig = new MasternodeSignature(Utils.reverseBytes(Utils.HEX.decode("1adc32aadc47a32019ef87e615b3e89a7f6821a9569d169c471dacdcf468057d2714611f8a5e83dd36cbf083468c4b69b819a922aeebe0fb201b458c0794f54a1f")));
        String message = "139.59.254.15:999915239445811bd94fd9f0b98eb669fa2c1dc1bda3da99f4c69ed40ce1ccc666e3be757d966d927e90173f970a0c70208";
        StringBuilder errorMessage = new StringBuilder();
        assert(MessageSigner.verifyMessage(publicKey, vchSig, message, errorMessage));

        assert(!MessageSigner.verifyMessage(publicKey, vchSig, "140.59.254.15:999915239445811bd94fd9f0b98eb669fa2c1dc1bda3da99f4c69ed40ce1ccc666e3be757d966d927e90173f970a0c70208", errorMessage));


    }


}
