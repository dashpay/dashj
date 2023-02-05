package org.bitcoinj.crypto;

import org.bitcoinj.core.Sha256Hash;
import org.dashj.bls.BLS;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BLSBatchVerifierTest {
    @BeforeClass
    public static void beforeClass() {
        BLS.Init();
    }

    static class Message {
        public int sourceId;
        public int msgId;
        Sha256Hash msgHash;
        BLSSecretKey sk;
        BLSPublicKey pk;
        BLSSignature sig;
        boolean valid;
    }

    static void addMessage(List<Message> vec, int sourceId, int msgId, int msgHash, boolean valid)
    {
        Message m = new Message();
        m.sourceId = sourceId;
        m.msgId = msgId;
        byte [] msgHashBytes = new byte [32];
        msgHashBytes[31] = (byte)msgHash;
        m.msgHash = Sha256Hash.wrap(msgHashBytes);
        m.sk = new BLSSecretKey();
        m.sk.makeNewKey();
        m.pk = m.sk.GetPublicKey();
        m.sig = m.sk.Sign(m.msgHash);
        m.valid = valid;

        if (!valid) {
            BLSSecretKey tmp = new BLSSecretKey();
            tmp.makeNewKey();
            m.sig = tmp.Sign(m.msgHash);
        }

        vec.add(m);
    }

    static void verify(List<Message> vec, boolean secureVerification, boolean perMessageFallback)
    {
        BLSBatchVerifier<Integer, Integer> batchVerifier = new BLSBatchVerifier<>(secureVerification, perMessageFallback);

        HashSet<Integer> expectedBadMessages = new HashSet<>();
        HashSet<Integer> expectedBadSources = new HashSet<>();
        for (Message m : vec) {
            if (!m.valid) {
                expectedBadMessages.add(m.msgId);
                expectedBadSources.add(m.sourceId);
            }

            batchVerifier.pushMessage(m.sourceId, m.msgId, m.msgHash, m.sig, m.pk);
        }

        batchVerifier.verify();

        assertEquals(expectedBadSources, batchVerifier.badSources);

        if (perMessageFallback) {
            assertEquals(expectedBadMessages, batchVerifier.badMessages);
        } else {
            assertTrue(batchVerifier.badMessages.isEmpty());
        }
    }

    static void verify(List<Message> vec) {
        verify(vec, false, false);
        verify(vec, true, false);
        verify(vec, false, true);
        verify(vec, true, true);
    }

    @Test
    public void batch_verifier_tests() {
        ArrayList<Message> msgs = new ArrayList<>();

        // distinct messages from distinct sources
        addMessage(msgs, 1, 1, 1, true);
        addMessage(msgs, 2, 2, 2, true);
        addMessage(msgs, 3, 3, 3, true);
        verify(msgs);

        // distinct messages from same source
        addMessage(msgs, 4, 4, 4, true);
        addMessage(msgs, 4, 5, 5, true);
        addMessage(msgs, 4, 6, 6, true);
        verify(msgs);

        // invalid sig
        addMessage(msgs, 7, 7, 7, false);
        verify(msgs);

        // same message as before, but from another source and with valid sig
        addMessage(msgs, 8, 8, 7, true);
        verify(msgs);

        // same message as before, but from another source and signed with another key
        addMessage(msgs, 9, 9, 7, true);
        verify(msgs);

        msgs.clear();
        // same message, signed by multiple keys
        addMessage(msgs, 1, 1, 1, true);
        addMessage(msgs, 1, 2, 1, true);
        addMessage(msgs, 1, 3, 1, true);
        addMessage(msgs, 2, 4, 1, true);
        addMessage(msgs, 2, 5, 1, true);
        addMessage(msgs, 2, 6, 1, true);
        verify(msgs);

        // last message invalid from one source
        addMessage(msgs, 1, 7, 1, false);
        verify(msgs);
    }
}
