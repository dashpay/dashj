/*
 * Copyright 2022 Dash Core Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.crypto;

import org.bitcoinj.core.Sha256Hash;
import org.dashj.bls.BLSJniLibrary;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BLSSignatureTest {
    @BeforeClass
    public static void beforeClass() {
        BLSJniLibrary.init();
    }

    static Random random = new Random();
    static Sha256Hash getRandomHash() {
        byte [] randBytes = new byte[32];
        random.nextBytes(randBytes);
        return Sha256Hash.wrap(randBytes);
    }

    @Test
    public void signatureTest() {
        BLSSecretKey sk1 = BLSSecretKey.makeNewKey();
        BLSSecretKey sk2 = BLSSecretKey.makeNewKey();

        Sha256Hash msgHash1 = Sha256Hash.of(Sha256Hash.ZERO_HASH.getBytes());
        Sha256Hash msgHash2 = Sha256Hash.twiceOf(Sha256Hash.ZERO_HASH.getBytes());

        BLSSignature sig1 = sk1.sign(msgHash1);
        BLSSignature sig2 = sk2.sign(msgHash1);
        assertTrue(sig1.verifyInsecure(sk1.getPublicKey(), msgHash1));
        assertFalse(sig1.verifyInsecure(sk1.getPublicKey(), msgHash2));
        assertFalse(sig2.verifyInsecure(sk1.getPublicKey(), msgHash1));
        assertFalse(sig2.verifyInsecure(sk2.getPublicKey(), msgHash2));
        assertTrue(sig2.verifyInsecure(sk2.getPublicKey(), msgHash1));
    }

    @Test
    public void keyAggregateTest() {
        BLSSecretKey sk1 = BLSSecretKey.makeNewKey();
        BLSSecretKey sk2 = BLSSecretKey.makeNewKey();

        BLSPublicKey ag_pk = sk1.getPublicKey();
        ag_pk.aggregateInsecure(sk2.getPublicKey());

        BLSSecretKey ag_sk = new BLSSecretKey(sk1);
        ag_sk.aggregateInsecure(sk2);

        assertEquals(ag_pk, ag_sk.getPublicKey());

        Sha256Hash msgHash1 = Sha256Hash.of(Sha256Hash.ZERO_HASH.getBytes());
        Sha256Hash msgHash2 = Sha256Hash.twiceOf(Sha256Hash.ZERO_HASH.getBytes());

        BLSSignature sig = ag_sk.sign(msgHash1);
        assertTrue(sig.verifyInsecure(ag_pk, msgHash1));
        assertFalse(sig.verifyInsecure(ag_pk, msgHash2));
    }

    @Test
    public void keyAggregateVectorTest() {
        // In practice, we only aggregate 400 key shares at any given time, something substantially larger than that should
        // be good. Plus this is very, very fast, so who cares!
        int keyCount = 10000;
        ArrayList<BLSSecretKey> vec_sk = new ArrayList<>(keyCount);
        ArrayList<BLSPublicKey> vec_pk = new ArrayList<>(keyCount);

        {
            BLSSecretKey ret = BLSSecretKey.aggregateInsecure(vec_sk);
            assertEquals(ret, new BLSSecretKey());
        }
        {
            BLSPublicKey ret = BLSPublicKey.aggregateInsecure(vec_pk, BLSScheme.legacyDefault);
            assertEquals(ret, new BLSPublicKey());
        }
        
        for (int i = 0; i < keyCount; i++) {
            BLSSecretKey sk = BLSSecretKey.makeNewKey();
            vec_sk.add(sk);
            vec_pk.add(sk.getPublicKey());
        }

        BLSSecretKey ag_sk = BLSSecretKey.aggregateInsecure (vec_sk);
        BLSPublicKey ag_pk = BLSPublicKey.aggregateInsecure (vec_pk, BLSScheme.legacyDefault);

        assertTrue(ag_sk.isValid());
        assertTrue(ag_pk.isValid());

        Sha256Hash msgHash1 = Sha256Hash.of(Sha256Hash.ZERO_HASH.getBytes());
        Sha256Hash msgHash2 = Sha256Hash.twiceOf(Sha256Hash.ZERO_HASH.getBytes());

        BLSSignature sig = ag_sk.sign(msgHash1);
        assertTrue(sig.verifyInsecure(ag_pk, msgHash1));
        assertFalse(sig.verifyInsecure(ag_pk, msgHash2));
    }

    @Test
    public void signatureAggregateSubTest() {
        int count = 20;
        ArrayList<BLSPublicKey> vec_pks = new ArrayList<>(count);
        ArrayList<Sha256Hash> vec_hashes = new ArrayList<>(count);
        ArrayList<BLSSignature> vec_sigs = new ArrayList<>(count);

        BLSSignature sig = new BLSSignature();
        Sha256Hash hash;
        for (int i = 0; i < count; i++) {
            BLSSecretKey sk = BLSSecretKey.makeNewKey();
            vec_pks.add(sk.getPublicKey());
            hash = getRandomHash();
            vec_hashes.add(hash);
            BLSSignature sig_i = sk.sign(hash);
            vec_sigs.add(sig_i);

            if (i == 0) {
                // first sig is assigned directly
                sig = new BLSSignature(sig_i);
            } else {
                // all other sigs are aggregated into the previously computed/stored sig
                sig.aggregateInsecure(sig_i);
            }
            assertTrue(sig.verifyInsecureAggregated(vec_pks, vec_hashes));
        }
        // Create an aggregated signature from the vector of individual signatures
        BLSSignature vecSig = BLSSignature.aggregateInsecure (vec_sigs, BLSScheme.legacyDefault);
        assertTrue(vecSig.verifyInsecureAggregated(vec_pks, vec_hashes));
        // Check that these two signatures are equal
        assertEquals(sig, vecSig);

        // Test that the sig continues to be valid when subtracting sigs via `SubInsecure`
        for (int i = 0; i < count - 1; i++) {
            BLSSignature top_sig = vec_sigs.get(vec_sigs.size() - 1);
            vec_pks.remove(vec_pks.size() - 1);
            vec_hashes.remove(vec_hashes.size() -1);
            assertFalse(sig.verifyInsecureAggregated(vec_pks, vec_hashes));
            sig.subInsecure(top_sig);
            assertTrue(sig.verifyInsecureAggregated(vec_pks, vec_hashes));
            vec_sigs.remove(vec_sigs.size() - 1);
        }

        // Check that the final left-over sig validates
        assertEquals(vec_sigs.size(), 1);
        assertEquals(vec_pks.size(), 1);
        assertEquals(vec_hashes.size(), 1);
        assertTrue(vec_sigs.get(0).verifyInsecure(vec_pks.get(0), vec_hashes.get(0)));
    }

    @Test
    public void signatureAggregateSecureTest() {
        int count = 10;

        Sha256Hash hash = getRandomHash();

        ArrayList< BLSSignature > vec_sigs = new ArrayList<>();
        ArrayList< BLSPublicKey > vec_pks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            BLSSecretKey sk = BLSSecretKey.makeNewKey();
            vec_pks.add(sk.getPublicKey());
            vec_sigs.add(sk.sign(hash));
        }

        BLSSignature sec_agg_sig = BLSSignature.aggregateSecure(vec_sigs, vec_pks, hash, BLSScheme.legacyDefault);
        assertTrue(sec_agg_sig.isValid());
        assertTrue(sec_agg_sig.verifySecureAggregated(vec_pks, hash));
    }
}