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
import org.bitcoinj.core.Utils;
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

    public void signFunction(boolean legacy) {
        BLSScheme.setLegacyDefault(legacy);
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

    public void keyAggregateFunction(boolean legacy) {
        BLSScheme.setLegacyDefault(legacy);
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

    public void keyAggregateVectorFunction(boolean legacy) {
        BLSScheme.setLegacyDefault(legacy);
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
            BLSPublicKey ret = BLSPublicKey.aggregateInsecure(vec_pk, legacy);
            assertEquals(ret, new BLSPublicKey());
        }
        
        for (int i = 0; i < keyCount; i++) {
            BLSSecretKey sk = BLSSecretKey.makeNewKey();
            vec_sk.add(sk);
            vec_pk.add(sk.getPublicKey());
        }

        BLSSecretKey ag_sk = BLSSecretKey.aggregateInsecure (vec_sk);
        BLSPublicKey ag_pk = BLSPublicKey.aggregateInsecure (vec_pk, legacy);

        assertTrue(ag_sk.isValid());
        assertTrue(ag_pk.isValid());

        Sha256Hash msgHash1 = Sha256Hash.of(Sha256Hash.ZERO_HASH.getBytes());
        Sha256Hash msgHash2 = Sha256Hash.twiceOf(Sha256Hash.ZERO_HASH.getBytes());

        BLSSignature sig = ag_sk.sign(msgHash1);
        assertTrue(sig.verifyInsecure(ag_pk, msgHash1));
        assertFalse(sig.verifyInsecure(ag_pk, msgHash2));
    }

    public void signatureAggregateSubFunction(boolean legacy) {
        BLSScheme.setLegacyDefault(legacy);
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
        BLSSignature vecSig = BLSSignature.aggregateInsecure (vec_sigs, legacy);
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

    public void signatureAggregateSecureFunction(boolean legacy) {
        BLSScheme.setLegacyDefault(legacy);
        int count = 10;

        Sha256Hash hash = getRandomHash();

        ArrayList< BLSSignature > vec_sigs = new ArrayList<>();
        ArrayList< BLSPublicKey > vec_pks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            BLSSecretKey sk = BLSSecretKey.makeNewKey();
            vec_pks.add(sk.getPublicKey());
            vec_sigs.add(sk.sign(hash));
        }

        BLSSignature sec_agg_sig = BLSSignature.aggregateSecure(vec_sigs, vec_pks, hash, legacy);
        assertTrue(sec_agg_sig.isValid());
        assertTrue(sec_agg_sig.verifySecureAggregated(vec_pks, hash));
    }

    @Test
    public void setHexStringTest() {

    }

    @Test
    public void signatureTest() {
        signFunction(true);
        signFunction(false);
    }

    @Test
    public void keyAggregationTest() {
        keyAggregateFunction(true);
        keyAggregateFunction(false);
    }

    @Test
    public void keyAggregationVectorTest() {
        keyAggregateVectorFunction(true);
        keyAggregateVectorFunction(false);
    }

    @Test
    public void signatureAggregationSubTest() {
        signatureAggregateSubFunction(true);
        signatureAggregateSecureFunction(false);
    }

    @Test
    public void signatureAggregateSecureTest() {
        signatureAggregateSecureFunction(true);
        signatureAggregateSecureFunction(false);
    }

    @Test
    public void quorumSignatureTest() {
        BLSScheme.setLegacyDefault(true);
        // 000000000000002c82df7f716994d9dc7dd0694249bdd380d1b09cda746da7c8:8
        // valid quorum commitment: 000000000000002c82df7f716994d9dc7dd0694249bdd380d1b09cda746da7c8:9: quorumPublicKey = 1612b2daa422daa274af5884e2ef7cdcae1c33d36573a8702a3d6c6a8e389f1b14981465df891df5a1b7210432bdfb55, membersSignature = 052f62455ad81786528a2c7b7ab4c22f812982ed99c0799e6cbf9a719a76e9cff2eaca9aefd41f29922c2f85e3c3d70a1100b35bc0d7d25bd54291d99234bf556a5649e8cccf4fddb040ebaca5fa401b0ec409cbd285f6c58a8dc17b521b2093
        BLSPublicKey quorumPublicKey = new BLSPublicKey(Utils.HEX.decode("1612b2daa422daa274af5884e2ef7cdcae1c33d36573a8702a3d6c6a8e389f1b14981465df891df5a1b7210432bdfb55"), true);
        BLSSignature membersSignature = new BLSSignature(Utils.HEX.decode("052f62455ad81786528a2c7b7ab4c22f812982ed99c0799e6cbf9a719a76e9cff2eaca9aefd41f29922c2f85e3c3d70a1100b35bc0d7d25bd54291d99234bf556a5649e8cccf4fddb040ebaca5fa401b0ec409cbd285f6c58a8dc17b521b2093"), true);

        String[] strOperatorKeys = new String[] {
                "8f3a813aa68a07fca73c616ea60d0dfbc81667c24a8ac6e6d4c9a64c6d162d5738808c5eab7138742a3d17c814a8bf94",
                "92058ad273ac46e18e4f43a20b5bcfbabdcded712d80387eeabaf190d4351f45749db9a9d1bf4e13e4ae946a03ed4015",
                "08ff9920aa7391cf47e0a1a816ab4c67e037a5d448d2cf28b4d8c7c4008c459eadbe5134f7176804046521ec0b49341e",
                "8b817dcf0c4233d3c71ceae42db90a1b630f1f97285b4ffd265387a088a7d38400cd705ca090bd9c0f4619a225e16c73",
                "91008785993639ba13e4e20981c89ed9a64a0e561da60e7e286f25c397d6e0db06acdded783b247fe26f2f2ff6665184",
                "0936107afd59a0433113ee3d77ef0ed7bc48790f70959460fdcac663f7050b4e48179c68228fe15f91dd6c19c702d0c8",
                "05ee12ead9b2fcbcd20e028acb74226fd75ec271ad8daa431fb9e6fdeea0070aeffb080c21edca34385060a1c8c05bdd",
                "064cd7b508ad623a51d79c8557a667499383e8d723ec79792d08bfce5fb96a6a898f502b74ab79761fed79c652c081d9",
                "919ad9aa930fc2cdafed3db371eb52dede4d14c9d170d1a75714556da791bc6973761ac975163488f258b988eb19d487",
                "02ab52425100d319bc1b5e1382c4eba074f73f2ae94f6e1713ffd9a0f513b541f44d9a0879f48fdcaf3521ebd3b734e3",
                "8f7242bdba0921c2418d4e3be676e320c0ba9ea86b5d185dd1dc1e665587925086e8bd0de4473f9eba0f1487ddf81f86",
                "0a354bd6de479dce0864fced9cc8e3f7ee39f1f77a69b28d75745907a7e76e6565d6b5b12bc7cd64284d910650f8ccb1",
                "921958278157241233fe7e816d06c4bba25583a108507c691d3ee45e3a7231a5606c31161c1c32614f74deff608690d9",
                "944be5269df80a87677dd9c7f6202c58d7b8eeeff712b2581b96956e823f02b7095a0fa27d12f8e10a426fa666abe7d2",
                "81b1f0151edf35e001385496b0b18481d4293eb1218f8105be8068d7864c535825d134f70177922a1c64674c87e10829",
                "11d3f729e18d03589e5795565318007ec11675fbcd970ff72c6d8534f0a9e582f00d6254d897e5563e90286a5ab2197f",
                "81a5c8199317dc83bebfa4b00c3c50c3c3ce9e1af8271016c44821dcb3f4a8181a35c21e3914d765f5a4e2059ebd5bbb",
                "8451624e5fbcdcf1703e9c1e80cef6e07e648ce343952f3e30c82c17c64a934870b54f30249398532e4a3e74a1e07df4",
                "8e1fbec112bc165a30db65e2bfbde2459fad2a590bcdca6c2350355ab9920c7db88655ac6ac25ed190f8e58900cfa507",
                "114923b2ea69b786b8dd5ae2b95f726903735714890b923d42288f8a46d894009749f5acd47340de1a3a4d33d80a3258",
                "0e7e5a1f72524c3bab4d7990a27af8d4451a327109549d876ec522e22305cadf80c9ff0f74d1a200dbcdb1376afd34e5",
                "892bda25e986cfdce112814bff6bb7f01b5bba267f503902d006ed0c30c4c27b782bf3cdfdb761514fba52129e45f76f",
                "99dc46b3b77144740a64f5dc1ca597424ac998d2a00e1eea6b248a02a5a53f9db0e122e598ad93d83b046ba3105d2f1a",
                "8e433404d5169db60433f21db99edcce1afcb548d2b0414c9dbab698148aaaf8d91e1ee94a021404e5d8d3d644835659",
                "0c3cd2a62cf315fb5c34615d8fda0d032d88de74d8100e85c4c07bb636ab609b699e1d593506eb160d4adfcd9f86dad8",
                "0f396fa4c452d8c6eb1eb993bdea8af98f96c65bf0cc37ef5048c895af4e89aa8babaa95111157a0d0aaefe1809282a1",
                "8162cb75478d2328c6af409b3ba0f4f720cd30c340d0b608e62bfb7ed72015a35f1ff5225acbd97af2a33320fe3ede48",
                "8d630e590710227707903890ebb933e2c12cacd477f689a258cc2bcefc481ada7513b9e8a11878481f8aae36fc278fa8",
                "01da056d3b253e6660c98771aea644191640f179dee3674f0c720ae896ebc9a4614f707c6809ad8f33a7226abf65d549",
                "0bba5dc0e216fa128d8701d0ce4de39e2dc39f16a0a3ceeced7fcc89d17b65cee32362bc68b712bfc9cc5490c334c6e6",
                "86f9f8c4738f1e83450f785017b983c6036ddcb23d16ba08735c51c531347dc3aa5ee8471ad883d2b1dd0873f6e18a70",
                "860413b84c02b5bfd97f44a2737dc4bd20404614d74e63da02d3dd91fd211d7c5b4ffc9caa23b277b53b96ec50bd7ff7",
                "05e2e0ff4488026ff18e1700c8378f50e4b84b9222ee46d0898ba7debe7da7121f98edca635bd167345e7904ee08330c",
                "13e6578f575c681f159b019fbc7d425f6e9cee2a4bfc98bad504026418d351aec1f0e91a66de53ef8899ab6fb66a1bed",
                "12e53b9b0f93bdac4d25e78fb5610aa4a10d12906586b1e162598a31718af93d015162ed7bb1d21daab9aa85e164afd1",
                "183ad2fa4d5622e12ef083304461bcc046c41c2a24b2f1ef7b36e2fe8bc50f48dbef75cf51128bfa4e280ba724babf23",
                "90908933bd97769966d74a7a85fad9ce894ec6dd943b71678a2ec87a155a9a0a390707e64d384a6452fe478771262504",
                "1232235225905ae0f2f765dcc3908e2e40d241bf9783ee7e39831bf76b620e3c019fdb522900563dc06a0494b036c27e",
                "0933c9280553bdf898189d3dec95419262433defd4bef9e90611ffc05376e582fe41f57d4b0547852928da79171e29fa",
                "8f2d54ffb351acc9fb8ca90726b02320832dda589a83fae040611d96a0a6917a5fbac2841232e18312f675c6a5aee670",
                "9555f97d16e75a135d98ec2f52a8881a60c790673cad6d9f0ef0e52bca3b0607aaa19cfec7ef4512e85b7fc687d0f3e6",
                "84830a7f9af1b788df3060c089e3e7d6e242e94802dfb8d2eb46d69aa27276a860963c52b20f41cdb4791a71e58b4344",
                "892fc0e02bff6e41f119ddb6f7f0d475a2721f101a26830db026681383fbbfab0cfa488473b51f511edab979ed915b28",
                "8c8440a82f2fa19bcf1a1324de03db6beba690da39c79c7e09835728026c46a59475e2fae6d0fbe20c01a128e796aac8",
                "8614b12d8761ef8fffe132725a9a6b511abe7823df3c0022ab3e4dec221cf8ce2ab6589cd617779023a54056d87f997b",
                "89718b0bcc8233af8df3eab1f3d2003282506e6babe096eae072cb8a435431fb3ca0359ef7ee8bfb3fbe981debdf9c0f",
                "0abc9b9ee35465c024cf4c72ed60dcb600c8657e6deff6f4ad69400b5f3a9d5140bb7c09c5262cd1265c093a7cb6c184",
                "10f912e265e3865b0ca0e7a8514616f541d2526e493212d0e82218f2ec7abce09eeb0316d165cdf006dfb596b37380d9",
                "8624a671b7eb6111e53adf55806ac01d6c0af6da23fe3e964650ed39b017585291cd6e2b3ff20a1f658aa26b4836abf6",
                "069fdc47b17e21a2c12eb27e81ff4c011f8088b3525cb1e6140a6f7db38123232ede01ee4fb2b7be143b756533a77ef6",
                "1053fe7f087d571a864a5a7408002f0ad786b33f06db9b719f39ed37af60270eb0c9e494833b6d2d1f029ec2700d3e6d",
                "954b5017998fe8a16d3946ed13ffa255c546a1dbd478e5bb3a2657de4d331a6abda1587ee83a8c6954bcad8ee43bb16c",
                "0c08500f384056485306bc8ff98a26ede2d20248ea1f7ccbd3ddc8b29a0e46a8fcda6a02d7a12b6ae94207a441411477",
                "8e2c3b1b98e45c78c9ccd7934064da30b18f6891417a7915d8dd7ee3dc5be76baa6e164e3dc0ae7185e9a3b449bfe813",
                "17ac04dcbe4572333decb848d4dcea1c2e5edf24a1e774aa1c1c6f31dbc3261883ad27cacd2efdd2ab91b24a77390b3f",
                "0eda3c087f9a593efe4c8fa7fd4ce02c587952b1bc20a49b2d21d573213c4f47a6db3494b1a33a0749518ba3bc0002d0",
                "022a15f6c1f3af9376cadbf2e99684de157ddcdd0966fac9fddb9772867213b867994bdcb55c8ea30e41b19c385f9fe4",
        };

        ArrayList<BLSPublicKey> mnOperatorKeys = new ArrayList<>();
        for (String opKey : strOperatorKeys) {
            mnOperatorKeys.add(new BLSPublicKey(Utils.HEX.decode(opKey), true));
        }

        Sha256Hash commitmentHash = Sha256Hash.wrap("656e3b2e895b155da40860ad4c09d48204d0847f1eb20bd1ebbe9416bfbd7961");

        BLSPublicKey agg_keys = BLSPublicKey.aggregateInsecure(mnOperatorKeys, true);
        assertTrue(agg_keys.isValid());
        assertTrue(membersSignature.verifySecureAggregated(mnOperatorKeys, commitmentHash));
    }
}