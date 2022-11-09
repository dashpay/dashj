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
            BLSPublicKey ret = BLSPublicKey.aggregateInsecure(vec_pk, BLSScheme.isLegacyDefault());
            assertEquals(ret, new BLSPublicKey());
        }
        
        for (int i = 0; i < keyCount; i++) {
            BLSSecretKey sk = BLSSecretKey.makeNewKey();
            vec_sk.add(sk);
            vec_pk.add(sk.getPublicKey());
        }

        BLSSecretKey ag_sk = BLSSecretKey.aggregateInsecure (vec_sk);
        BLSPublicKey ag_pk = BLSPublicKey.aggregateInsecure (vec_pk, BLSScheme.isLegacyDefault());

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
        BLSSignature vecSig = BLSSignature.aggregateInsecure (vec_sigs, BLSScheme.isLegacyDefault());
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

        BLSSignature sec_agg_sig = BLSSignature.aggregateSecure(vec_sigs, vec_pks, hash, BLSScheme.isLegacyDefault());
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
        BLSPublicKey quorumPublicKey = new BLSPublicKey(Utils.HEX.decode("1612b2daa422daa274af5884e2ef7cdcae1c33d36573a8702a3d6c6a8e389f1b14981465df891df5a1b7210432bdfb55"));
        BLSSignature membersSignature = new BLSSignature(Utils.HEX.decode("052f62455ad81786528a2c7b7ab4c22f812982ed99c0799e6cbf9a719a76e9cff2eaca9aefd41f29922c2f85e3c3d70a1100b35bc0d7d25bd54291d99234bf556a5649e8cccf4fddb040ebaca5fa401b0ec409cbd285f6c58a8dc17b521b2093"));

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
            mnOperatorKeys.add(new BLSPublicKey(Utils.HEX.decode(opKey)));
        }

        Sha256Hash commitmentHash = Sha256Hash.wrap("656e3b2e895b155da40860ad4c09d48204d0847f1eb20bd1ebbe9416bfbd7961");

        BLSPublicKey agg_keys = BLSPublicKey.aggregateInsecure(mnOperatorKeys, true);
        assertTrue(agg_keys.isValid());
        assertTrue(membersSignature.verifySecureAggregated(mnOperatorKeys, commitmentHash));
    }
    
    @Test
    public void quorumTest2() {
        BLSScheme.setLegacyDefault(true);
        Sha256Hash commitmentHash = Sha256Hash.wrap("6fb2fbb3429b6baeb32b90ff169b244016af2d9e9f25b371e323386188dae11f");
        
        String[] strOperatorKeys = new String[]{
                "8fc31972124bb559aecd56dfb361048ab3f5203624c6436b1676b8d440bee777d83b937febacb2d3a651df9dbd20503e",


                "19a3fe93e29cb8e1043b3ba3abe13f10639e574c9ccce8ea16a6f1b1c204b8dfde8e1a6a29e88af9b945293924009a1a",


                "13cc0376467317a70dac3d49ca1ce0c666b3f2630396fa208cc6e7d6401c691178b4cdf20deeccfe7d597e40cfba0f18",


                "99d1a6a4f7f817d2e004134d96fa0c831433e9c649726ba8567f447d1b2394209bc1ef184a93c707054fb6816790de30",


                "93ea90ebcf0d8e332e37e5ac3c676653bb1203e8db7604bb0ac64a9b655b553de514e9bff5eeb86bb3ef9178375392f9",


                "0bde30ce81e7c6396e334eb1bd788b683535eefe9911286bb42662c46e3712ddb5c7c24d7998118ae089e804e14efee5",


                "0620124f5dbe95b93bbcbab48452ba0cc47beaaf554e63db5deef90c10ca79c1e83c08a43d4316105419bccf65958023",


                "8f70ff352844250e267b31c0ddb83dffd4cac43532194bd47cbabf410ca29fa7f1ecec08c8fde8c0d13910e903016d5a",


                "018a6d23ae53d6231f7dd73a058f125340e92f6e97897f017d9d9d4e6671bbd92241170dfcdd5a4ab8ef47ef12ddcad5",


                "118081d1c248d74a0737f36e5bd40aa71b512c6be6f68e3664723849ac47a62fc743c4dc7234694bda1b7701f33d2e81",


                "92cafe1870e043973b2f1fded8de3d5a66dac5ade46aa0995157077efee92d852857bc7f03ed69c92723a58f8bd2926e",


                "07f3bb14d4e16bb20ebdfc97b3b067ebbcea5b0b9725b796e6c62d8a0818eff300261a62b6fbc3bdbaa97b895b66137c",


                "09f8a06bd95c1be3cfdcd2516fabc0858c611d63c76da3a5beaa007b9d7c895aa63c0b2887bd584a76892db417a6683f",


                "8fc1d0cea417ed963e50d876a38bf0846b536b7e8809826e163bc9ea0f749ea8ebe00c6642e71bb84000549bda5bb1d0",


                "90ea47f22be1644834d8756793f2308f2c5b40afd16ebb98d29a3bd37e437990d4d5930ccfa56c1ea0b4e51d05a49f23",


                "07df25a28955c903cc19f836a4daa0842d203cfc0dc5ae9b57b8246a4787ee4c98ea3f2586203315d61f4e77b6c80dc5",


                "8deed4d18add0ef5a7dff743a206786ab2dcc1b4aff679a61577dea99b62fb24dd56e3fe7ff65fa0be964dc5d7967c3e",


                "052057284a7a9dbccb97fbaea3425104901dc661b69294a55c7ca800ed18d37df7ccc02367b5d6836ee4f6b052249a1d",


                "075b907b6d6c12aa111da0e102186b9d06f4e065969b60732207f18c2c5d0deb8ecba47cb4c0929647db0e2fae6f08ca",


                "174de56654f2bb6417e15ff06361ea0becc00bd72a3eba0f83b60feac860570769fbf28482a706f10906a1e96dae4a8f",


                "17f78abcee6d2ed68bf2c82afbf56ef9af67313e2eb655ea5178850907cb3057cae0bb5a1d09f161057bf62f9d4890c6",


                "066d57a6451b7800c1c2a6c6e04fe73ec2e1c95e492bacae760ad2f79ca3c30727ec9bf0daea43c08ff1ad6c2cf07612",


                "0aeb5c2757211202b3afd2033ec1b4ef2dfe376ba5c6c07b45e6a7460afa4086423c4a704eb9a781514fbc513e190a62",


                "8b7c76ec03f7ae0dc9be41fb9168906ef0d0d4de74f0ad8c5cf0a30483879b5203ae5d7c6aeee5b92998444bc10f68ec",


                "94a637afe3810d73e3402b5d6a398e45222ba846a339f1c3570aa8e3f7f5b9d7acef08ac234cce4f706671498330a599",


                "082cbd9118474316f40b800e43f94a121928f256fd340098ff0ad81a902c4326dda4b42737d52739482f2baa80c487cc",


                "92823797ad456d53ce1e6bde84e8a19164ff88a73ccd242ec48d9c6a479f2a049e214c7e8ec2243b7ea74ca6144ab2c5",


                "0ad4f577d067630f6fd15f4d2aefdb9456d648b71cb7253d47511acc81dd5ddb69a03c848322aa11e5242f66afde5a2a",


                "1099dcddc6560d1039b0edb91bd700e5deae0cba43163fa289a80c2bd22335b5b0e7a1fb8f5494c0e6360e73a12fe0a8",


                "905caab51ff07a2f8d69972fd6ec09f6f9893cf6dfc49775f5a2db2ea7a8a525bbaf4e7e369d06590f6f2e8e4658d4dc",


                "090f1ca955443740346b5b4b0bfb8251f040074b5a2feb77e54add831bf34aaf1d84207691f6f5aa5e702152a496fadc",


                "937befcc17d16d4154ea8bbe82e9bd52e2ecd825dd9a43f58730d594d87cceebcb41e11461319fd71bfc08d0a0545200",


                "897fc76b69f1ff4b06535e7a4bc7396fb66b33194effbb72214dbefc2c7cd3220ab6cc39fe4630a513879f9f8dca27e3",


                "16f8048e511e7c0c2b495a9b20030b315d75bca283b70af25d16c8809c7f2a786225c2fe47ff1c92aa8ebf586be91abc",


                "0f267ed0554a2bfb5cd39f4b56b5c2daa0adb2c3c97dd8d0c5e6295e25de9378ac6f25623e2acf0721ce9044c47f9278",


                "0ea46d70601eb45319ab495e2462f981debc8316df2bb1a679ae3525c7f517e535b69a02052844374c887a9312a47984",


                "882aed1df01917097a5502ff541a800d268967ab39c8f841ed62c5387eb46459d6f6959166cafec148dcae03830e83a5",


                "17a49cea05ca2e18f74af110c5ab52c89a43ced4e056a8af7ca8973401494bdaba26d1c56b46b018091d0dd64f244750",


                "0fa5377eb256323aace31b45c3e48ea110404b053cb80e8043bd1e44de1705130548e4ab28738816251ea57a7fc10324",


                "880f24b5e040dcbf86c3f468dd28bf45d9e41fbcd127fad56669d9afe358dcdc26e42f0f8b19997b1741dbb99c553aa6",


                "826fc7f30c49215b98d5cb47a350f888a306c52fa42c77e765b55288e622f03859273cae7e1cac99e67f7a9a96a6aa2c",


                "00ea87eef15f38c1a844d77348e687794c601277011c933026cdfdb649524632b055feea3539abc48472cb447d281d65",


                "00eb80b32b60db5d7b03559f6e9205beac8d047689904bdda0bc50987d5f208d39b78ed90a34af7e1e9d44495ca1eb42",


                "17596d7a72b65531fffd5f610752422d6e286c975f30d026092f7900f8015073bd6f6d1b85dd3981814c093910e7dac6",


                "16ca29d03ef4897a22fe467bb58c52448c63bb29534502305e8ff142ac03907fae0851ff2528e4878ef51bfa3d5a1f22",


                "002a2e19ac4b986e20f55ddb19cdfa69cbd5f76c5e2d66ac6d9c8418aa1f0836e61b643bf48eeb004ccf3f3f0f03b82a",


                "0acab8f54530816bce0366a4f1a2319a445c535d074b53d5824a3e90b542e8b5a77181aad2e77560fb9f6fd7eb76532b",


                "0c9bf080a96d13b356b01e734618a77225f03b3e92684f252ccbd313764a9fd9247bde6b00d92f6b5669043e77860453",


                "038ea498b8c554bdc1f0ce0c107d8d23d27e40b45e6df56793cab951722dd69a958dc14798ae542cf025802f4b84a3f6",


                "842d732a03847819b1e2675ac48b9af4a1c92b310ecacc42c428ff902099cc47d08ecd4616da55d185463855aee99f79",


                "088905cc3f99e76b3a1abf714a55978d9930c2abdc77a21bd809e452e8c47c35d38e318ec3118e1944cf1a4a8df907c1",


                "0084679675b1b750baeb4a51c6006348eddf65fa10e7c15c61091630c0881240a8b4273a232d1bc4a6f73c0a79a2087d",


                "0935576848f6ab7e27fff34b671953672012352e36f5147181926b8bbc9e8b43b98458704666df25d36f37d41eb7c694",


                "14e199beb2a2a59166a12a851ef158928bc5efc25b39eb78b3a428b25384609d8c03548a94e77c0c941c90c68a4187d8",


                "14eabcb82f2b0b9cda8eaa3cecd39f0058b418cb7a25795f597a811895bfcc23643bb25ae8432a52804dfb53575b649e",


                "15a577f51dc6fd7fa4621f0a4601e48fd65418a89c2af2afef725fb4f053a8ee5841cd3fdae39ebdf5a202e0c4deca23",
                "12730062f122f937b29f69536db3ad36980b88004eadc2ca341425d432723d67e53a4f55786c54017d77c1bd1df6b310",
                "90051db915bd86bd938746c14440b11ee3b2801cbc6d6c1c912e8b41ea5eb1d8f852abf220ae91ecdb6da094846c1ba8"
        };


        //00000127dd6cf1b17c1f2d925df3a39414276b02f1468a948b837a79af872c82:0:quorumPublicKey = 150d4fb3473b6ab94c3ad9fcc3b45a4f6883b29138d470497aec30cf39247b86df17961113d87c64f540c84cc9b272d2,
        BLSSignature membersSignature = new BLSSignature(Utils.HEX.decode("894712416309f1809dd86eeefd91a9c8e8962ca05bbad331297cfa9997f4c0cf9902d1e44bf2f3f679fef8447a82d0360e763a33f52f6f238c5fdca6bd877c6d2da93a7cb5070d6af245666f4b3315133069bc3f4bff41013687df05c9580665"));

        ArrayList<BLSPublicKey> mnOperatorKeys = new ArrayList<>();
        for (String opKey : strOperatorKeys) {
            mnOperatorKeys.add(new BLSPublicKey(Utils.HEX.decode(opKey)));
        }


        BLSPublicKey agg_keys = BLSPublicKey.aggregateInsecure(mnOperatorKeys, true);
        assertTrue(agg_keys.isValid());
        assertTrue(membersSignature.verifySecureAggregated(mnOperatorKeys, commitmentHash));

    }
}