package org.dashj.bls;

import com.google.common.io.BaseEncoding;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import static org.dashj.bls.BLS.STS_OK;
import static org.junit.Assert.*;

public class BlsSignatureTest extends BaseTest {

    public static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    @Test
    public void mainTests() {
        // This test is taken from the readme at the following link:
        // https://github.com/Chia-Network/bls-signatures/blob/master/README.md
        System.out.println("Creating keys and signatures");

        // Example seed, used to generate private key. Always use
        // a secure RNG with sufficient entropy to generate a seed.
        byte[] seed = {0, 50, 6, 244 - 256, 24, 199 - 255, 1, 25, 52, 88, 192 - 256,
                19, 18, 12, 89, 6, 220 - 255, 18, 102, 58, 209 - 255,
                82, 12, 62, 89, 110, 182 - 255, 9, 44, 20, 254 - 255, 22};

        PrivateKey sk = PrivateKey.FromSeed(seed, seed.length);
        PublicKey pk = sk.GetPublicKey();

        byte[] msg = {100, 2, 254 - 256, 88, 90, 45, 23};

        Signature sig = sk.Sign(msg, msg.length);

        System.out.println("Serializing keys and signatures to bytes");
        byte[] skBytes = new byte[(int) PrivateKey.PRIVATE_KEY_SIZE];  // 32 byte array
        byte[] pkBytes = new byte[(int) PublicKey.PUBLIC_KEY_SIZE];    // 48 byte array
        byte[] sigBytes = new byte[(int) Signature.SIGNATURE_SIZE];    // 96 byte array

        sk.Serialize(skBytes);   // 32 bytes
        pk.Serialize(pkBytes);   // 48 bytes
        sig.Serialize(sigBytes); // 96 bytes

        System.out.println("Loading keys and signatures from bytes");
        // Takes array of 32 bytes
        sk = PrivateKey.FromBytes(skBytes);

        // Takes array of 48 bytes
        pk = PublicKey.FromBytes(pkBytes);

        // Takes array of 96 bytes
        sig = Signature.FromBytes(sigBytes);
        System.out.println("Verifying signatures");
        // Add information required for verification, to sig object
        sig.SetAggregationInfo(AggregationInfo.FromMsg(pk, msg, msg.length));

        boolean ok = sig.Verify();
        System.out.println("Signature Verify = " + ok);

        System.out.println("Aggregate signatures for a single message");
        // Generate some more private keys
        seed[0] = 1;
        PrivateKey sk1 = PrivateKey.FromSeed(seed, seed.length);
        seed[0] = 2;
        PrivateKey sk2 = PrivateKey.FromSeed(seed, seed.length);

        // Generate first sig
        PublicKey pk1 = sk1.GetPublicKey();
        Signature sig1 = sk1.Sign(msg, msg.length);

        // Generate second sig
        PublicKey pk2 = sk2.GetPublicKey();
        Signature sig2 = sk2.Sign(msg, msg.length);

        // Aggregate signatures together
        SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        Signature aggSig = Signature.AggregateSigs(sigs);

        byte[] buffer = new byte[(int) Signature.SIGNATURE_SIZE];
        aggSig.Serialize(buffer);

        BigInteger bi = new BigInteger(buffer);
        System.out.println(bi.toString(16));

        // For same message, public keys can be aggregated into one.
        // The signature can be verified the same as a single signature,
        // using this public key.
        PublicKeyVector pubKeys = new PublicKeyVector();
        pubKeys.push_back(pk1);
        pubKeys.push_back(pk2);
        PublicKey aggPubKey = PublicKey.Aggregate(pubKeys);

        System.out.println("Aggregate signatures for different messages");

        // Generate one more key and message
        seed[0] = 3;
        PrivateKey sk3 = PrivateKey.FromSeed(seed, seed.length);
        PublicKey pk3 = sk3.GetPublicKey();
        byte [] msg2 = {100, 2, 254-256, 88, 90, 45, 23};

        // Generate the signatures, assuming we have 3 private keys
        sig1 = sk1.Sign(msg, msg.length);
        sig2 = sk2.Sign(msg, msg.length);
        Signature sig3 = sk3.Sign(msg2, msg2.length);

        // They can be noninteractively combined by anyone
        // Aggregation below can also be done by the verifier, to
        // make batch verification more efficient
        SignatureVector sigsL = new SignatureVector();
        sigsL.push_back(sig1);
        sigsL.push_back(sig2);
        Signature aggSigL = Signature.AggregateSigs(sigsL);

        // Arbitrary trees of aggregates
        SignatureVector sigsFinal = new SignatureVector();
        sigsFinal.push_back(aggSigL);
        sigsFinal.push_back(sig3);
        Signature aggSigFinal = Signature.AggregateSigs(sigsFinal);

        // Serialize the final signature
        aggSigFinal.Serialize(sigBytes);

        System.out.println("Verify aggregate signature for different messages");

        // Deserialize aggregate signature
        aggSigFinal = Signature.FromBytes(sigBytes);

        // Create aggregation information (or deserialize it)
        AggregationInfo a1 = AggregationInfo.FromMsg(pk1, msg, msg.length);
        AggregationInfo a2 = AggregationInfo.FromMsg(pk2, msg, msg.length);
        AggregationInfo a3 = AggregationInfo.FromMsg(pk3, msg2, msg2.length);
        AggregationInfoVector infos = new AggregationInfoVector();
        infos.push_back(a1);
        infos.push_back(a2);
        AggregationInfo a1a2 = AggregationInfo.MergeInfos(infos);
        AggregationInfoVector infos2 = new AggregationInfoVector();
        infos2.push_back(a1a2);
        infos2.push_back(a3);
        AggregationInfo aFinal = AggregationInfo.MergeInfos(infos2);

        // Verify final signature using the aggregation info
        aggSigFinal.SetAggregationInfo(aFinal);
        ok = aggSigFinal.Verify();

        // If you previously verified a signature, you can also divide
        // the aggregate signature by the signature you already verified.
        ok = aggSigL.Verify();
        SignatureVector cache = new SignatureVector();
        cache.push_back(aggSigL);
        aggSigFinal = aggSigFinal.DivideBy(cache);

        // Final verification is now more efficient
        ok = aggSigFinal.Verify();

        System.out.println("Aggregate private keys");

        PrivateKeyVector privateKeysList = new PrivateKeyVector();
        privateKeysList.push_back(sk1);
        privateKeysList.push_back(sk2);
        PublicKeyVector pubKeysList = new PublicKeyVector();
        pubKeysList.push_back(pk1);
        pubKeysList.push_back(pk2);

        // Create an aggregate private key, that can generate
        // aggregate signatures
        PrivateKey aggSk = PrivateKey.Aggregate(privateKeysList, pubKeysList);

        Signature aggSig3 = aggSk.Sign(msg, msg.length);

        System.out.println("HD keys");

        // Random seed, used to generate master extended private key
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, seed.length);

        ExtendedPublicKey epk = esk.GetExtendedPublicKey();

        // Use i >= 2^31 for hardened keys
        ExtendedPrivateKey skChild = esk.PrivateChild(0)
                .PrivateChild(5);

        ExtendedPublicKey pkChild = epk.PublicChild(0)
                .PublicChild(5);

        // Serialize extended keys
        byte [] buffer1 = new byte[(int) ExtendedPublicKey.EXTENDED_PUBLIC_KEY_SIZE];   // 93 bytes
        byte [] buffer2 = new byte[(int) ExtendedPrivateKey.EXTENDED_PRIVATE_KEY_SIZE]; // 77 bytes

        pkChild.Serialize(buffer1);
        skChild.Serialize(buffer2);
    }

    @Test
    public void voidNullTest() {
        try {
            InsecureSignature.FromBytes(null);
            fail();
        } catch(NullPointerException x) {

        }
    }

    @Test
    public void specVectorsTest() {
        // These test vectors are taken from this page:
        // https://github.com/Chia-Network/bls-signatures/blob/master/SPEC.md

        byte [] seed1 = {1, 2, 3, 4, 5};

        PrivateKey pk1 = PrivateKey.FromSeed(seed1, seed1.length);
        byte [] sk1 = HEX.decode("022fb42c08c12de3a6af053880199806532e79515f94e83461612101f9412f9e");
        byte [] skActual = new byte[(int)PrivateKey.PRIVATE_KEY_SIZE];
        pk1.Serialize(skActual);
        assertArrayEquals(sk1, skActual);
        assertEquals(0x26d53247, pk1.GetPublicKey().GetFingerprint());

        byte [] seed2 = {1, 2, 3, 4, 5, 6};
        PrivateKey pk2 = PrivateKey.FromSeed(seed2, seed2.length);
        assertEquals(0x289bb56e, pk2.GetPublicKey().GetFingerprint());

        byte [] msg = {7, 8, 9};
        Signature s1 = pk1.Sign(msg, msg.length);
        byte [] sig1 = HEX.decode("93eb2e1cb5efcfb31f2c08b235e8203a67265bc6a13d9f0ab77727293b74a357ff0459ac210dc851fcb8a60cb7d393a419915cfcf83908ddbeac32039aaa3e8fea82efcb3ba4f740f20c76df5e97109b57370ae32d9b70d256a98942e5806065");
        byte [] sig1Actual = new byte[(int)Signature.SIGNATURE_SIZE];
        s1.Serialize(sig1Actual);
        assertArrayEquals(sig1Actual, sig1);

        Signature s2 = pk2.Sign(msg, msg.length);
        byte [] sig2 = HEX.decode("975b5daa64b915be19b5ac6d47bc1c2fc832d2fb8ca3e95c4805d8216f95cf2bdbb36cc23645f52040e381550727db420b523b57d494959e0e8c0c6060c46cf173872897f14d43b2ac2aec52fc7b46c02c5699ff7a10beba24d3ced4e89c821e");
        byte [] sig2Actual = new byte[(int)Signature.SIGNATURE_SIZE];
        s2.Serialize(sig2Actual);
        assertArrayEquals(sig2Actual, sig2);

        AggregationInfo aggInfo1 = AggregationInfo.FromMsg(pk1.GetPublicKey(), msg, msg.length);
        AggregationInfo aggInfo2 = AggregationInfo.FromMsg(pk2.GetPublicKey(), msg, msg.length);

        s1.SetAggregationInfo(aggInfo1);
        s2.SetAggregationInfo(aggInfo2);
        assertEquals(true, s1.Verify());
        assertEquals(true, s2.Verify());

        //Aggregation
        SignatureVector signatureVector = new SignatureVector();
        signatureVector.push_back(s1);
        signatureVector.push_back(s2);
        Signature aggSig = Signature.AggregateSigs(signatureVector);
        byte [] aggSigExpected = HEX.decode("0a638495c1403b25be391ed44c0ab013390026b5892c796a85ede46310ff7d0e0671f86ebe0e8f56bee80f28eb6d999c0a418c5fc52debac8fc338784cd32b76338d629dc2b4045a5833a357809795ef55ee3e9bee532edfc1d9c443bf5bc658");
        byte [] aggSigActual = new byte[(int)Signature.SIGNATURE_SIZE];
        aggSig.Serialize(aggSigActual);
        assertArrayEquals(aggSigExpected, aggSigActual);

        AggregationInfoVector aggInfos = new AggregationInfoVector();
        aggInfos.push_back(aggInfo1);
        aggInfos.push_back(aggInfo2);
        aggSig.SetAggregationInfo(AggregationInfo.MergeInfos(aggInfos));
        assertEquals(true, aggSig.Verify());

        byte [] msg3 = {1, 2, 3};
        Signature sig3 = pk1.Sign(msg3, msg3.length);

        byte [] msg4 = {1, 2, 3, 4};
        Signature sig4 = pk1.Sign(msg4, msg4.length);

        byte [] msg5 = {1, 2};
        Signature sig5 = pk2.Sign(msg5, msg5.length);

        signatureVector = new SignatureVector();
        signatureVector.push_back(sig3);
        signatureVector.push_back(sig4);
        signatureVector.push_back(sig5);
        Signature aggSig2 = Signature.AggregateSigs(signatureVector);
        byte [] aggSig2Expected = HEX.decode("8b11daf73cd05f2fe27809b74a7b4c65b1bb79cc1066bdf839d96b97e073c1a635d2ec048e0801b4a208118fdbbb63a516bab8755cc8d850862eeaa099540cd83621ff9db97b4ada857ef54c50715486217bd2ecb4517e05ab49380c041e159b");
        byte [] aggSig2Actual = new byte[(int)Signature.SIGNATURE_SIZE];
        aggSig2.Serialize(aggSig2Actual);
        assertArrayEquals(aggSig2Expected, aggSig2Actual);

        aggInfos = new AggregationInfoVector();
        aggInfos.push_back(sig3.GetAggregationInfo());
        aggInfos.push_back(sig4.GetAggregationInfo());
        aggInfos.push_back(sig5.GetAggregationInfo());
        aggSig2.SetAggregationInfo(AggregationInfo.MergeInfos(aggInfos));
        assertEquals(true, aggSig2.Verify());

        byte [] msg6 = {1, 2, 3, 40};
        s1 = pk1.Sign(msg6, msg6.length);

        byte [] msg7 = {5, 6, 70, (byte)201};
        s2 = pk2.Sign(msg7, msg7.length);

        sig3 = pk2.Sign(msg6, msg6.length);

        byte [] msg8 = {9, 10, 11, 12, 13};
        sig4 = pk1.Sign(msg8, msg8.length);

        sig5 = pk1.Sign(msg6, msg6.length);
        byte [] msg9 = {15, 63, (byte)244, 92, 0, 1};
        Signature sig6 = pk1.Sign(msg9, msg9.length);

        SignatureVector s12 = new SignatureVector();
        s12.push_back(s1);
        s12.push_back(s2);
        Signature sigL = Signature.AggregateSigs(s12);

        SignatureVector s345 = new SignatureVector();
        s345.push_back(sig3);
        s345.push_back(sig4);
        s345.push_back(sig5);
        Signature sigR = Signature.AggregateSigs(s345);

        assertEquals(true, sigL.Verify());
        assertEquals(true, sigR.Verify());

        SignatureVector sLR6 = new SignatureVector();
        sLR6.push_back(sigL);
        sLR6.push_back(sigR);
        sLR6.push_back(sig6);
        Signature sigFinal = Signature.AggregateSigs(sLR6);
        byte [] sigFinalExpected = HEX.decode("07969958fbf82e65bd13ba0749990764cac81cf10d923af9fdd2723f1e3910c3fdb874a67f9d511bb7e4920f8c01232b12e2fb5e64a7c2d177a475dab5c3729ca1f580301ccdef809c57a8846890265d195b694fa414a2a3aa55c32837fddd80");
        byte [] sigFinalActual = new byte[(int)Signature.SIGNATURE_SIZE];
        sigFinal.Serialize(sigFinalActual);
        assertArrayEquals(sigFinalExpected, sigFinalActual);
        assertEquals(true, sigFinal.Verify());
    }

    int sizeof(byte [] bytes) {
        return bytes.length;
    }
    // These tests were taken from test.cpp
    // Copyright 2018 Chia Network Inc

    //TEST_CASE("Test vectors")
    @Test
    public void testVectors1() {
        byte [] seed1 = new byte [] {1, 2, 3, 4, 5};
        byte [] seed2 = new byte [] {1, 2, 3, 4, 5, 6};
        byte [] message1 = new byte [] {7, 8, 9};

        PrivateKey sk1 = PrivateKey.FromSeed(seed1, sizeof(seed1));
        PublicKey pk1 = sk1.GetPublicKey();
        Signature sig1 = sk1.Sign(message1, sizeof(message1));

        PrivateKey sk2 = PrivateKey.FromSeed(seed2, sizeof(seed2));
        PublicKey pk2 = sk2.GetPublicKey();
        Signature sig2 = sk2.Sign(message1, sizeof(message1));

        byte [] buf = new byte[(int)Signature.SIGNATURE_SIZE];
        byte [] buf2 = new byte [(int)PrivateKey.PRIVATE_KEY_SIZE];

        assertTrue(pk1.GetFingerprint() == 0x26d53247);
        assertTrue(pk2.GetFingerprint() == 0x289bb56e);


        sig1.Serialize(buf);
        sk1.Serialize(buf2);

        assertTrue(HEX.encode(buf).equals("93eb2e1cb5efcfb31f2c08b235e8203a67265bc6a13d9f0ab77727293b74a357ff0459ac210dc851fcb8a60cb7d393a419915cfcf83908ddbeac32039aaa3e8fea82efcb3ba4f740f20c76df5e97109b57370ae32d9b70d256a98942e5806065"));
        assertTrue(HEX.encode(buf2).equals("022fb42c08c12de3a6af053880199806532e79515f94e83461612101f9412f9e"));

        sig2.Serialize(buf);
        assertTrue(HEX.encode(buf).equals("975b5daa64b915be19b5ac6d47bc1c2fc832d2fb8ca3e95c4805d8216f95cf2bdbb36cc23645f52040e381550727db420b523b57d494959e0e8c0c6060c46cf173872897f14d43b2ac2aec52fc7b46c02c5699ff7a10beba24d3ced4e89c821e"));

        SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        Signature aggSig1 = Signature.AggregateSigs(sigs);

        aggSig1.Serialize(buf);
        assertTrue(HEX.encode(buf).equals("0a638495c1403b25be391ed44c0ab013390026b5892c796a85ede46310ff7d0e0671f86ebe0e8f56bee80f28eb6d999c0a418c5fc52debac8fc338784cd32b76338d629dc2b4045a5833a357809795ef55ee3e9bee532edfc1d9c443bf5bc658"));
        assertTrue(aggSig1.Verify());

        byte [] message2 = new byte [] {1, 2, 3};
        byte [] message3 = new byte [] {1, 2, 3, 4};
        byte [] message4 = new byte [] {1, 2};
        Signature sig3 = sk1.Sign(message2, sizeof(message2));
        Signature sig4 = sk1.Sign(message3, sizeof(message3));
        Signature sig5 = sk2.Sign(message4, sizeof(message4));
        SignatureVector sigs2 = new SignatureVector();
        sigs2.push_back(sig3);
        sigs2.push_back(sig4);
        sigs2.push_back(sig5);
        Signature aggSig2 = Signature.AggregateSigs(sigs2);
        assertTrue(aggSig2.Verify());
        aggSig2.Serialize(buf);
        assertTrue(HEX.encode(buf).equals("8b11daf73cd05f2fe27809b74a7b4c65b1bb79cc1066bdf839d96b97e073c1a635d2ec048e0801b4a208118fdbbb63a516bab8755cc8d850862eeaa099540cd83621ff9db97b4ada857ef54c50715486217bd2ecb4517e05ab49380c041e159b"));
    }

    @Test
    public void testVector2() {
        byte [] message1 = new byte [] {1, 2, 3, 40};
        byte [] message2 = new byte [] {5, 6, 70, (byte)201};
        byte [] message3 = new byte [] {9, 10, 11, 12, 13};
        byte [] message4 = new byte [] {15, 63, (byte)244, 92, 0, 1};

        byte [] seed1 = new byte [] {1, 2, 3, 4, 5};
        byte [] seed2 = new byte [] {1, 2, 3, 4, 5, 6};

        PrivateKey sk1 = PrivateKey.FromSeed(seed1, sizeof(seed1));
        PrivateKey sk2 = PrivateKey.FromSeed(seed2, sizeof(seed2));

        PublicKey pk1 = sk1.GetPublicKey();
        PublicKey pk2 = sk2.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message2, sizeof(message2));
        Signature sig3 = sk2.Sign(message1, sizeof(message1));
        Signature sig4 = sk1.Sign(message3, sizeof(message3));
        Signature sig5 = sk1.Sign(message1, sizeof(message1));
        Signature sig6 = sk1.Sign(message4, sizeof(message4));

        final SignatureVector sigsL = new SignatureVector();
        sigsL.push_back(sig1);
        sigsL.push_back(sig2);
        final Signature aggSigL = Signature.AggregateSigs(sigsL);

        final SignatureVector sigsR = new SignatureVector();
        sigsR.push_back(sig3);
        sigsR.push_back(sig4);
        sigsR.push_back(sig5);
        final Signature aggSigR = Signature.AggregateSigs(sigsR);

        SignatureVector sigs = new SignatureVector();
        sigs.push_back(aggSigL);
        sigs.push_back(aggSigR);
        sigs.push_back(sig6);

        Signature aggSig = Signature.AggregateSigs(sigs);

        assertTrue(aggSig.Verify());

        byte [] buf = new byte[(int)Signature.SIGNATURE_SIZE];
        aggSig.Serialize(buf);
        assertTrue(HEX.encode(buf).equals("07969958fbf82e65bd13ba0749990764cac81cf10d923af9fdd2723f1e3910c3fdb874a67f9d511bb7e4920f8c01232b12e2fb5e64a7c2d177a475dab5c3729ca1f580301ccdef809c57a8846890265d195b694fa414a2a3aa55c32837fddd80"));
        SignatureVector signatures_to_divide = new SignatureVector();
        signatures_to_divide.push_back(sig2);
        signatures_to_divide.push_back(sig5);
        signatures_to_divide.push_back(sig6);
        Signature quotient = aggSig.DivideBy(signatures_to_divide);
        aggSig.DivideBy(signatures_to_divide);

        quotient.Serialize(buf);
        assertTrue(HEX.encode(buf).equals("8ebc8a73a2291e689ce51769ff87e517be6089fd0627b2ce3cd2f0ee1ce134b39c4da40928954175014e9bbe623d845d0bdba8bfd2a85af9507ddf145579480132b676f027381314d983a63842fcc7bf5c8c088461e3ebb04dcf86b431d6238f"));

        assertTrue(quotient.Verify());
        assertTrue(quotient.DivideBy(new SignatureVector()).equals(quotient));
        signatures_to_divide.clear();
        signatures_to_divide.push_back(sig6);

        try {
            quotient.DivideBy(signatures_to_divide);
            fail();
        } catch (Exception x) {
            assertTrue(x.getMessage().contains("Signature is not a subset"));
        }
        // Should not throw
        signatures_to_divide.clear();
        signatures_to_divide.push_back(sig1);
        aggSig.DivideBy(signatures_to_divide);

        // Should throw due to not unique
        signatures_to_divide.clear();
        signatures_to_divide.push_back(aggSigL);
        try {
            aggSig.DivideBy(signatures_to_divide);
            fail();
        } catch (Exception x) {
        }

        Signature sig7 = sk2.Sign(message3, sizeof(message3));
        Signature sig8 = sk2.Sign(message4, sizeof(message4));

        // Divide by aggregate
        SignatureVector sigsR2 = new SignatureVector();
        sigsR2.push_back(sig7);
        sigsR2.push_back(sig8);
        Signature aggSigR2 = Signature.AggregateSigs(sigsR2);
        SignatureVector sigsFinal2 = new SignatureVector();
        sigsFinal2.push_back(aggSig);
        sigsFinal2.push_back(aggSigR2);
        Signature aggSig2 = Signature.AggregateSigs(sigsFinal2);
        SignatureVector divisorFinal2 = new SignatureVector();
        divisorFinal2.push_back(aggSigR2);
        Signature quotient2 = aggSig2.DivideBy(divisorFinal2);

        assertTrue(quotient2.Verify());
        quotient2.Serialize(buf);
        assertTrue(HEX.encode(buf).equals("06af6930bd06838f2e4b00b62911fb290245cce503ccf5bfc2901459897731dd08fc4c56dbde75a11677ccfbfa61ab8b14735fddc66a02b7aeebb54ab9a41488f89f641d83d4515c4dd20dfcf28cbbccb1472c327f0780be3a90c005c58a47d3"));
    }

    @Test
    public void testVector3() {
        byte [] seed = {1, 50, 6, (byte)244, 24, (byte)199, 1, 25};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));
        assertTrue(esk.GetPublicKey().GetFingerprint() == 0xa4700b27L);
        byte [] chainCode = new byte[32];
        esk.GetChainCode().Serialize(chainCode);
        assertTrue(HEX.encode(chainCode).equals("d8b12555b4cc5578951e4a7c80031e22019cc0dce168b3ed88115311b8feb1e3"));

        ExtendedPrivateKey esk77 = esk.PrivateChild(77 + (1 << 31));
        esk77.GetChainCode().Serialize(chainCode);
        assertTrue(HEX.encode(chainCode).equals("f2c8e4269bb3e54f8179a5c6976d92ca14c3260dd729981e9d15f53049fd698b"));
        assertTrue(esk77.GetPrivateKey().GetPublicKey().GetFingerprint() == 0xa8063dcfL);

        assertTrue(esk.PrivateChild(3)
                .PrivateChild(17)
                .GetPublicKey()
                .GetFingerprint() == 0xff26a31fL);
        assertTrue(esk.GetExtendedPublicKey()
                .PublicChild(3)
                .PublicChild(17)
                .GetPublicKey()
                .GetFingerprint() == 0xff26a31fL);
    }


    //TEST_CASE("Key generation") {
    @Test
    public void shouldGenerateKeyPairFromSeed() {
        byte [] seed = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};


        PrivateKey sk = PrivateKey.FromSeed(seed, sizeof(seed));
        PublicKey pk = sk.GetPublicKey();
        BLS.CheckRelicErrors();
        assertTrue(pk.GetFingerprint() == 0xddad59bbL);
    }


    //TEST_CASE("Error handling") {
    @Test
    public void shouldThrowOnBadPrivateKey() {
        byte [] seed = getRandomSeed(32);
        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        byte [] skData = new byte[(int)PrivateKey.PRIVATE_KEY_SIZE];
        sk1.Serialize(skData);
        PrivateKey.FromBytes(skData);
        skData[0] = (byte)255;
        try {
            PrivateKey.FromBytes(skData);
            fail();
        } catch (Exception x) {
            assertTrue(x.getMessage().contains("Key data too large, must be smaller than group order"));
        }
    }

    private byte [] getRandomSeed(int size) {
        Random rand = new Random();
        BigInteger result = new BigInteger((size) *8 - 1, rand); // (2^4-1) = 15 is the maximum value
        byte [] bytes = new byte [32];
        System.arraycopy(result.toByteArray(), 0, bytes, 0, result.toByteArray().length);
        return bytes;
    }

    @Test
    public void shouldThrowOnBadPublicKey() {
        byte [] buf = new byte[(int)PublicKey.PUBLIC_KEY_SIZE];
        HashSet<Integer> invalid = new HashSet<>();
        invalid.add(1);
        invalid.add(2);
        invalid.add(3);
        invalid.add(4);

        for (int i = 0; i < 10; i++) {
            buf[0] = (byte)i;
            try {
                PublicKey.FromBytes(buf);
                assertTrue(!invalid.contains(i));
            } catch (AssertionError x) {
                fail();
            } catch (Exception x) {
                assertTrue(invalid.contains(i));
            }
        }
    }

    @Test
    public void shouldThrowOnBadSignature() {
        byte [] buf = new byte[(int)Signature.SIGNATURE_SIZE];
        HashSet<Integer> invalid = new HashSet<>(9);
        int [] invalidValues = {0, 1, 2, 3, 5, 6, 7, 8};
        for(int i : invalidValues)
            invalid.add(i);

        for (int i = 0; i < 10; i++) {
            buf[0] = (byte)i;
            try {
                Signature.FromBytes(buf);
                assertTrue(!invalid.contains(i));
            } catch (AssertionError x) {
                fail();
            } catch (Exception x) {
                assertTrue(invalid.contains(i));
            }
        }
    }

    //  TODO::Make BLS ThreadSafe
    boolean ctxError = false;
    @Test
    public void errorHandlingShouldBeThreadSafe() throws InterruptedException {
        System.out.println("First Thread Running");
        BLS.SetContextError(10);
        System.out.println("First Thread:  Set Error Code to 10");
        assertTrue(BLS.GetContextError() == 10);

        long ctx1 = BLS.GetContext();
        System.out.println("First Thread Context:" + ctx1);


        // spawn a thread and make sure it uses a different context
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Second Thread Running");
                BLS.Init();
                if (ctx1 == BLS.GetContext()) {
                    ctxError = true;
                }
                System.out.println("Second Thread Context:" + BLS.GetContext());

                if (BLS.GetContextError() != STS_OK) {
                    ctxError = true;
                }
                // this should not modify the code of the main thread
                System.out.println("Second Thread:  Set Error Code to 1");
                BLS.SetContextError(1);
            }
        });
        thread.start();

        thread.join();

        System.out.println("Checking different contexts");
        assertTrue(!ctxError);

        // other thread should not modify code
        assertTrue(BLS.GetContextError() == 10);

        // reset so that future test cases don't fail
        BLS.SetContextError(STS_OK);
    }


    //TEST_CASE("Util tests") {
    @Test
    public void shouldCalculatePublicKeyFingerprint() {
        byte [] seed = {1, 50, 6, (byte)244, 24, (byte)199, 1, 25};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));
        long fingerprint = esk.GetPublicKey().GetFingerprint();
        assertTrue(fingerprint == 0xa4700b27L);
    }

    //TEST_CASE("Signatures") {
    @Test
    public void shouldSignAndVerify() {
        byte [] message1 = new byte[] {1, 65, (byte)254, 88, 90, 45, 22};

        byte [] seed = new byte[] {28, 20, 102, (byte)229, 1, (byte)157};
        PrivateKey sk1 = PrivateKey.FromSeed(seed, sizeof(seed));
        PublicKey pk1 = sk1.GetPublicKey();
        Signature sig1 = sk1.Sign(message1, sizeof(message1));

        sig1.SetAggregationInfo(
                AggregationInfo.FromMsg(pk1, message1, sizeof(message1)));
        assertTrue(sig1.Verify());

        byte [] hash = getSHA256Hash(message1);
        Signature sig2 = sk1.SignPrehashed(hash);
        sig2.SetAggregationInfo(
                AggregationInfo.FromMsg(pk1, message1, sizeof(message1)));
        assertTrue(sig1.equals(sig2));
        assertTrue(sig2.Verify());

        // Hashing to g1
        //byte mapMsg[0] = {};
        //g1_t result;
        //byte buf[49];
        //ep_map(result, mapMsg, 0);
        //g1_write_bin(buf, 49, result, 1);
        //assertTrue(HEX.encode(buf + 1, 48) == "12fc5ad5a2fbe9d4b6eb0bc16d530e5f263b6d59cbaf26c3f2831962924aa588ab84d46cc80d3a433ce064adb307f256");
    }

    private byte [] getSHA256Hash(byte [] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return hash; // make it printable
        }catch(Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Test
    public void shouldUseCopyConstructor() {
        byte [] message1 = new byte[] {1, 65, (byte)254, 88, 90, 45, 22};

        byte [] seed = getRandomSeed(32);
        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();
        PrivateKey sk2 = new PrivateKey(sk1);

        byte [] skBytes = new byte[(int)PrivateKey.PRIVATE_KEY_SIZE];
        sk2.Serialize(skBytes);
        PrivateKey sk4 = PrivateKey.FromBytes(skBytes);

        PublicKey pk2 = new PublicKey(pk1);
        Signature sig1 = sk4.Sign(message1, sizeof(message1));
        Signature sig2 = new Signature(sig1);

        assertTrue(sig2.Verify());
    }

    @Test
    public void shouldUseOperators() {
        byte [] message1 = {1, 65, (byte)254, 88, 90, 45, 22};
        byte [] seed = getRandomSeed(32);
        byte [] seed3 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PrivateKey sk2 = new PrivateKey(sk1);
        PrivateKey sk3 = PrivateKey.FromSeed(seed3, 32);
        PublicKey pk1 = sk1.GetPublicKey();
        PublicKey pk2 = sk2.GetPublicKey();
        PublicKey pk3 = new PublicKey(pk2);
        PublicKey pk4 = sk3.GetPublicKey();
        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk1.Sign(message1, sizeof(message1));
        Signature sig3 = sk2.Sign(message1, sizeof(message1));
        Signature sig4 = sk3.Sign(message1, sizeof(message1));

        assertTrue(sk1.equals(sk2));
        assertTrue(!sk1.equals(sk3));
        assertTrue(pk1.equals(pk2));
        assertTrue(pk2.equals(pk3));
        assertTrue(!pk1.equals(pk4));
        assertTrue(sig1.equals(sig2));
        assertTrue(sig2.equals(sig3));
        assertTrue(!sig3.equals(sig4));

        assertTrue(Arrays.equals(pk1.Serialize(), pk2.Serialize()));
        assertTrue(Arrays.equals(sig1.Serialize(),sig2.Serialize()));
    }

    @Test
    public void shouldSerializeAndDeserialize() {
        byte [] message1 = {1, 65, (byte)254, 88, 90, 45, 22};

        byte [] seed = getRandomSeed(32);
        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        byte [] skData = new byte [(int)PrivateKey.PRIVATE_KEY_SIZE];
        sk1.Serialize(skData);
        PrivateKey sk2 = PrivateKey.FromBytes(skData);
        assertTrue(sk1.equals(sk2));

        byte [] pkData = new byte[(int)PublicKey.PUBLIC_KEY_SIZE];
        pk1.Serialize(pkData);

        PublicKey pk2 = PublicKey.FromBytes(pkData);
        assertTrue(pk1.equals(pk2));

        Signature sig1 = sk1.Sign(message1, sizeof(message1));

        byte [] sigData = new byte[(int)Signature.SIGNATURE_SIZE];
        sig1.Serialize(sigData);

        Signature sig2 = Signature.FromBytes(sigData);
        assertTrue(sig1.equals(sig2));
        sig2.SetAggregationInfo(AggregationInfo.FromMsg(
                pk2, message1, sizeof(message1)));

        assertTrue(sig2.Verify());

        InsecureSignature sig3 = InsecureSignature.FromBytes(sigData);
        assertTrue(Signature.FromInsecureSig(sig3).equals(sig2));
    }

    @Test
    public void shouldNotValidateBadSignature() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 22};
        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);
        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);

        PublicKey pk1 = sk1.GetPublicKey();
        PublicKey pk2 = sk2.GetPublicKey();

        Signature sig2 = sk2.Sign(message1, sizeof(message1));
        sig2.SetAggregationInfo(AggregationInfo.FromMsg(
                pk1, message1, sizeof(message1)));

        assertTrue(sig2.Verify() == false);
    }

    @Test
    public void shouldInsecureleyAggregateAndVerify() {
        byte [] message = {100, 2, (byte)254, 88, 90, 45, 23};

        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);

        byte [] hash = getSHA256Hash(message);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);

        InsecureSignature sig1 = sk1.SignInsecure(message, sizeof(message));
        InsecureSignature sig2 = sk2.SignInsecure(message, sizeof(message));
        assertTrue(sig1 != sig2);
        assertTrue(sig1.Verify(hash, sk1.GetPublicKey()));
        assertTrue(sig2.Verify(hash, sk2.GetPublicKey()));

        InsecureSignatureVector sigs = new InsecureSignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        PublicKeyVector pks = new PublicKeyVector();
        pks.push_back(sk1.GetPublicKey());
        pks.push_back(sk2.GetPublicKey());
        InsecureSignature aggSig = InsecureSignature.Aggregate(sigs);
        PublicKey aggPk = PublicKey.AggregateInsecure(pks);
        assertTrue(aggSig.Verify(hash, aggPk));
    }

    @Test
    public void shouldInsecurelyAggregateAndVerifyAggregateDiffMessages() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] message2 = {100, 2, (byte)254, 88, 90, 45, 24, 1};

        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);

        byte [] hash1 = getSHA256Hash(message1);
        byte [] hash2 = getSHA256Hash(message2);

        InsecureSignature sig1 = sk1.SignInsecurePrehashed(hash1);
        InsecureSignature sig2 = sk2.SignInsecurePrehashed(hash2);
        assertTrue(sig1 != sig2);
        assertTrue(sig1.Verify(hash1, sk1.GetPublicKey()));
        assertTrue(sig2.Verify(hash2, sk2.GetPublicKey()));

        InsecureSignatureVector sigs = new InsecureSignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        PublicKeyVector pks = new PublicKeyVector();
        pks.push_back(sk1.GetPublicKey());
        pks.push_back(sk2.GetPublicKey());
        InsecureSignature aggSig = InsecureSignature.Aggregate(sigs);

        // same message verification should fail
        PublicKey aggPk = PublicKey.AggregateInsecure(pks);
        assertTrue(!aggSig.Verify(hash1, aggPk));
        assertTrue(!aggSig.Verify(hash2, aggPk));

        // diff message verification should succeed
        MessageHashVector hashes = new MessageHashVector();
        hashes.push_back(hash1);
        hashes.push_back(hash2);
        assertTrue(aggSig.Verify(hashes, pks));
    }

    @Test
    public void shouldSecurelyAggregateAndVerifyAggregate() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] message2 = {(byte)192, 29, 2, 0, 0, 45, 23};

        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);

        PublicKey pk1 = sk1.GetPublicKey();
        PublicKey pk2 = sk2.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message2, sizeof(message2));

        SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        Signature aggSig = Signature.AggregateSigs(sigs);

        Signature sig3 = sk1.Sign(message1, sizeof(message1));
        Signature sig4 = sk2.Sign(message2, sizeof(message2));

        SignatureVector sigs2 = new SignatureVector();
        sigs2.push_back(sig3);
        sigs2.push_back(sig4);

        Signature aggSig2 = Signature.AggregateSigs(sigs2);
        assertTrue(sig1.equals(sig3));
        assertTrue(sig2.equals(sig4));
        assertTrue(aggSig.equals(aggSig2));
        assertTrue(!sig1.equals(sig2));

        assertTrue(aggSig.Verify());
    }

    @Test
    public void shouldSecurelyAggregateManySignaturesDifferentMessage() {
        PrivateKeyVector sks = new PrivateKeyVector();
        SignatureVector sigs = new SignatureVector();

        for (int i = 0; i < 78; i++) {
            byte [] message = new byte[8];
            message[0] = 0;
            message[1] = 100;
            message[2] = 2;
            message[3] = 59;
            message[4] = (byte)255;
            message[5] = 92;
            message[6] = 5;
            message[7] = (byte)i;
            byte [] seed = getRandomSeed(32);
            final PrivateKey sk = PrivateKey.FromSeed(seed, 32);
            final PublicKey pk = sk.GetPublicKey();
            sks.push_back(sk);
            sigs.push_back(sk.Sign(message, sizeof(message)));
        }

        Signature aggSig = Signature.AggregateSigs(sigs);

        assertTrue(aggSig.Verify());
    }

    @Test
    public void shouldInsecurelyAggregateManySignaturesDifferentMessage() {
        PrivateKeyVector sks = new PrivateKeyVector();
        PublicKeyVector pks = new PublicKeyVector();
        InsecureSignatureVector sigs = new InsecureSignatureVector();
        MessageHashVector hashes = new MessageHashVector();

        for (int i = 0; i < 80; i++) {
            byte [] message = new byte[8];
            message[0] = 0;
            message[1] = 100;
            message[2] = 2;
            message[3] = 59;
            message[4] = (byte)255;
            message[5] = 92;
            message[6] = 5;
            message[7] = (byte)i;
            byte [] hash = getSHA256Hash(message);
            hashes.push_back(hash);
            byte [] seed = getRandomSeed(32);
            final PrivateKey sk = PrivateKey.FromSeed(seed, 32);
            final PublicKey pk = sk.GetPublicKey();
            sks.push_back(sk);
            pks.push_back(pk);
            sigs.push_back(sk.SignInsecurePrehashed(hash));
        }

        InsecureSignature aggSig = InsecureSignature.Aggregate(sigs);

        assertTrue(aggSig.Verify(hashes, pks));

        //std.swap(pks[0], pks[1]);
        PublicKey pks0 = new PublicKey(pks.get(0));
        PublicKey pks1 = new PublicKey(pks.get(1));
        pks.set(0, pks1);
        pks.set(1, pks0);
        assertTrue(!aggSig.Verify(hashes, pks));
        //std.swap(hashes[0], hashes[1]);
        byte [] hash0 = hashes.get(0);
        byte [] hash1 = hashes.get(1);
        hashes.set(0, hash1);
        hashes.set(1, hash0);
        assertTrue(aggSig.Verify(hashes, pks));
    }

    @Test
    public void shouldSecurelyAggregateSameMessage() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);
        byte [] seed3 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);
        PublicKey pk2 = sk2.GetPublicKey();

        PrivateKey sk3 = PrivateKey.FromSeed(seed3, 32);
        PublicKey pk3 = sk3.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message1, sizeof(message1));
        Signature sig3 = sk3.Sign(message1, sizeof(message1));

        SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        sigs.push_back(sig3);
        PublicKeyVector pubKeys = new PublicKeyVector();
        pubKeys.push_back(pk1);
        pubKeys.push_back(pk2);
        pubKeys.push_back(pk3);
        Signature aggSig = Signature.AggregateSigs(sigs);

        final PublicKey aggPubKey = PublicKey.Aggregate(pubKeys);
        aggSig.SetAggregationInfo(AggregationInfo.FromMsg(
                aggPubKey, message1, sizeof(message1)));
        assertTrue(aggSig.Verify());
    }

    @Test
    public void shouldSecurelyDivideSignatures() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);
        byte [] seed3 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);
        PublicKey pk2 = sk2.GetPublicKey();

        PrivateKey sk3 = PrivateKey.FromSeed(seed3, 32);
        PublicKey pk3 = sk3.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message1, sizeof(message1));
        Signature sig3 = sk3.Sign(message1, sizeof(message1));

        SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        sigs.push_back(sig3);
        Signature aggSig = Signature.AggregateSigs(sigs);

        assertTrue(sig2.Verify());
        assertTrue(sig3.Verify());
        SignatureVector divisorSigs = new SignatureVector();
        divisorSigs.push_back(sig2);
        divisorSigs.push_back(sig3);

        assertTrue(aggSig.Verify());
        assertTrue(aggSig.GetAggregationInfo().GetPubKeys().size() == 3);
        final Signature aggSig2 = aggSig.DivideBy(divisorSigs);
        assertTrue(aggSig.GetAggregationInfo().GetPubKeys().size() == 3);
        assertTrue(aggSig2.GetAggregationInfo().GetPubKeys().size() == 1);

        assertTrue(aggSig.Verify());
        assertTrue(aggSig2.Verify());
    }

    @Test
    public void shouldSecurelyDivideAggregateSignatures() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] message2 = {92, 20, 5, 89, 91, 15, 105};
        byte [] message3 = {(byte)200, 10, 10, (byte)159, 4, 15, 24};
        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);
        byte [] seed3 = getRandomSeed(32);
        byte [] seed4 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);
        PublicKey pk2 = sk2.GetPublicKey();

        PrivateKey sk3 = PrivateKey.FromSeed(seed3, 32);
        PublicKey pk3 = sk3.GetPublicKey();

        PrivateKey sk4 = PrivateKey.FromSeed(seed4, 32);
        PublicKey pk4 = sk4.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message1, sizeof(message1));
        Signature sig3 = sk3.Sign(message1, sizeof(message1));
        Signature sig4 = sk4.Sign(message2, sizeof(message2));
        Signature sig5 = sk4.Sign(message1, sizeof(message1));
        Signature sig6 = sk2.Sign(message3, sizeof(message3));

        SignatureVector sigsL = new SignatureVector();
        sigsL.push_back(sig1);
        sigsL.push_back(sig2);
        SignatureVector sigsC = new SignatureVector();
        sigsC.push_back(sig3);
        sigsC.push_back(sig4);
        SignatureVector sigsR = new SignatureVector();
        sigsR.push_back(sig5);
        sigsR.push_back(sig6);
        Signature aggSigL = Signature.AggregateSigs(sigsL);
        Signature aggSigC = Signature.AggregateSigs(sigsC);
        Signature aggSigR = Signature.AggregateSigs(sigsR);

        SignatureVector sigsL2 = new SignatureVector();
        sigsL2.push_back(aggSigL);
        sigsL2.push_back(aggSigC);
        Signature aggSigL2 = Signature.AggregateSigs(sigsL2);

        SignatureVector sigsFinal = new SignatureVector();
        sigsFinal.push_back(aggSigL2);
        sigsFinal.push_back(aggSigR);

        Signature aggSigFinal = Signature.AggregateSigs(sigsFinal);

        assertTrue(aggSigFinal.Verify());
        assertTrue(aggSigFinal.GetAggregationInfo().GetPubKeys().size() == 6);
        SignatureVector divisorSigs = new SignatureVector();
        divisorSigs.push_back(aggSigL);
        divisorSigs.push_back(sig6);
        aggSigFinal = aggSigFinal.DivideBy(divisorSigs);
        assertTrue(aggSigFinal.GetAggregationInfo().GetPubKeys().size() == 3);
        assertTrue(aggSigFinal.Verify());

        // Throws when the m/pk pair is not unique within the aggregate (sig1
        // is in both aggSigL2 and sig1.
        SignatureVector sigsFinal2 = new SignatureVector();
        sigsFinal2.push_back(aggSigL2);
        sigsFinal2.push_back(aggSigR);
        sigsFinal2.push_back(sig1);

        Signature aggSigFinal2 = Signature.AggregateSigs(sigsFinal2);
        SignatureVector divisorSigs2 = new SignatureVector();
        divisorSigs.push_back(aggSigL);
        SignatureVector divisorSigs3 = new SignatureVector();
        divisorSigs3.push_back(sig6);
        aggSigFinal2 = aggSigFinal2.DivideBy(divisorSigs3);
        try {
            aggSigFinal2.DivideBy(divisorSigs);
            fail();
        } catch (Exception x) {
        }
    }

    @Test
    public void shouldInsecurelyAggregateManySigsSameMessage() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};

        PrivateKeyVector sks = new PrivateKeyVector();
        PublicKeyVector pks = new PublicKeyVector();
        InsecureSignatureVector sigs = new InsecureSignatureVector();

        byte [] hash1 = getSHA256Hash(message1);

        for (int i = 0; i < 70; i++) {
            byte [] seed = getRandomSeed(32);
            PrivateKey sk = PrivateKey.FromSeed(seed, 32);
            final PublicKey pk = sk.GetPublicKey();
            sks.push_back(sk);
            pks.push_back(pk);
            sigs.push_back(sk.SignInsecure(message1, sizeof(message1)));
        }

        InsecureSignature aggSig = InsecureSignature.Aggregate(sigs);
        final PublicKey aggPubKey = PublicKey.AggregateInsecure(pks);
        assertTrue(aggSig.Verify(hash1, aggPubKey));
    }

    @Test
    public void shouldSecurelyAggreateManySigsSameMessage() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};

        PrivateKeyVector sks = new PrivateKeyVector();
        PublicKeyVector pks = new PublicKeyVector();
        SignatureVector sigs = new SignatureVector();

        for (int i = 0; i < 70; i++) {
            byte [] seed = getRandomSeed(32);
            PrivateKey sk = PrivateKey.FromSeed(seed, 32);
            final PublicKey pk = sk.GetPublicKey();
            sks.push_back(sk);
            pks.push_back(pk);
            sigs.push_back(sk.Sign(message1, sizeof(message1)));
        }

        Signature aggSig = Signature.AggregateSigs(sigs);
        final PublicKey aggPubKey = PublicKey.Aggregate(pks);
        aggSig.SetAggregationInfo(AggregationInfo.FromMsg(
                aggPubKey, message1, sizeof(message1)));
        assertTrue(aggSig.Verify());
    }

    @Test
    public void shouldHaveAtLeastOneSigWithAggregationInfo() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] seed = getRandomSeed(32);
        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));

        final SignatureVector sigs = new SignatureVector();
        try {
            Signature.AggregateSigs(sigs);
            fail();
        } catch (IllegalArgumentException x) {
            fail();
        } catch (Exception x) {
            assertTrue(x.getMessage().contains("Must have atleast one signatures and key"));
        }

        sig1.SetAggregationInfo(new AggregationInfo());
        final SignatureVector sigs2 = new SignatureVector();
        sigs2.push_back(sig1);

        try {
            Signature.AggregateSigs(sigs2);
            fail();
        } catch(Exception x) {
        }
    }

    @Test
    public void shouldPerformBatchVerification() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] message2 = {10, 28, (byte)254, 88, 90, 45, 29, 38};
        byte [] message3 = {10, 28, (byte)254, 88, 90, 45, 29, 38, (byte)177};
        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);
        byte [] seed3 = getRandomSeed(32);
        byte [] seed4 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);
        PublicKey pk2 = sk2.GetPublicKey();

        PrivateKey sk3 = PrivateKey.FromSeed(seed3, 32);
        PublicKey pk3 = sk3.GetPublicKey();

        PrivateKey sk4 = PrivateKey.FromSeed(seed4, 32);
        PublicKey pk4 = sk4.GetPublicKey();


        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message1, sizeof(message1));
        Signature sig3 = sk3.Sign(message2, sizeof(message2));
        Signature sig4 = sk4.Sign(message3, sizeof(message3));
        Signature sig5 = sk3.Sign(message1, sizeof(message1));
        Signature sig6 = sk2.Sign(message1, sizeof(message1));
        Signature sig7 = sk4.Sign(message2, sizeof(message2));

        final SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        sigs.push_back(sig3);
        sigs.push_back(sig4);
        sigs.push_back(sig5);
        sigs.push_back(sig6);
        sigs.push_back(sig7);
        final PublicKeyVector pubKeys = new PublicKeyVector();
        PublicKey [] pks = new PublicKey[]{pk1, pk2, pk3, pk4, pk3, pk2, pk4};
        for(PublicKey pk : pks)
            pubKeys.push_back(pk);

        /*
        byte [][] messages = new byte[][]
                {message1, message1, message2, message3, message1,
                        message1, message2};

*/
        // Verifier generates a batch signature for efficiency
        Signature aggSig = Signature.AggregateSigs(sigs);
        assertTrue(aggSig.Verify());
    }

    @Test
    public void shouldPeformBatchVerificationWithCacheOptimization() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] message2 = {10, 28, (byte)254, 88, 90, 45, 29, 38};
        byte [] message3 = {10, 28, (byte)254, 88, 90, 45, 29, 38, (byte)177};
        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);
        byte [] seed3 = getRandomSeed(32);
        byte [] seed4 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);
        PublicKey pk2 = sk2.GetPublicKey();

        PrivateKey sk3 = PrivateKey.FromSeed(seed3, 32);
        PublicKey pk3 = sk3.GetPublicKey();

        PrivateKey sk4 = PrivateKey.FromSeed(seed4, 32);
        PublicKey pk4 = sk4.GetPublicKey();


        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message1, sizeof(message1));
        Signature sig3 = sk3.Sign(message2, sizeof(message2));
        Signature sig4 = sk4.Sign(message3, sizeof(message3));
        Signature sig5 = sk3.Sign(message1, sizeof(message1));
        Signature sig6 = sk2.Sign(message1, sizeof(message1));
        Signature sig7 = sk4.Sign(message2, sizeof(message2));

        final SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        sigs.push_back(sig3);
        sigs.push_back(sig4);
        sigs.push_back(sig5);
        sigs.push_back(sig6);
        sigs.push_back(sig7);

        assertTrue(sig1.Verify());
        assertTrue(sig3.Verify());
        assertTrue(sig4.Verify());
        assertTrue(sig7.Verify());
        SignatureVector cache = new SignatureVector();
        cache.push_back(sig1);
        cache.push_back(sig3);
        cache.push_back(sig4);
        cache.push_back(sig7);

        // Verifier generates a batch signature for efficiency
        Signature aggSig = Signature.AggregateSigs(sigs);

        final Signature aggSig2 = aggSig.DivideBy(cache);
        assertTrue(aggSig.Verify());
        assertTrue(aggSig2.Verify());
    }

    @Test
    public void shouldAggregateSameMessageWithAggSK() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();

        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);
        PublicKey pk2 = sk2.GetPublicKey();

        final PrivateKeyVector privateKeys = new PrivateKeyVector();
        privateKeys.push_back(sk1);
        privateKeys.push_back(sk2);
        final PublicKeyVector pubKeys = new PublicKeyVector();
        pubKeys.push_back(pk1);
        pubKeys.push_back(pk2);
        final PrivateKey aggSk = PrivateKey.Aggregate(
                privateKeys, pubKeys);

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message1, sizeof(message1));

        Signature aggSig2 = aggSk.Sign(message1, sizeof(message1));

        final SignatureVector sigs = new SignatureVector();
        sigs.push_back(sig1);
        sigs.push_back(sig2);
        byte [][] messages = {message1, message1};
        Signature aggSig = Signature.AggregateSigs(sigs);
        assertTrue(aggSig.equals(aggSig2));

        final PublicKey aggPubKey = PublicKey.Aggregate(pubKeys);
        assertTrue(aggSig.Verify());
        assertTrue(aggSig2.Verify());
    }

    //TEST_CASE("HD keys") {
    @Test
    public void shouldCreateExtendedPrivateKeyFromSeed() {
        byte [] seed = new byte [] {1, 50, 6, (byte)244, 24, (byte)199, 1, 25};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));

        ExtendedPrivateKey esk77 = esk.PrivateChild(77 + (1 << 31));
        ExtendedPrivateKey esk77copy = esk.PrivateChild(77 + (1 << 31));

        assertTrue(esk77.equals(esk77copy));

        ExtendedPrivateKey esk77nh = esk.PrivateChild(77);

        ExtendedPrivateKey eskLong = esk.PrivateChild((1 << 31) + 5)
                .PrivateChild(0)
                .PrivateChild(0)
                .PrivateChild((1 << 31) + 56)
                .PrivateChild(70)
                .PrivateChild(4);
        byte [] chainCode = new byte [32];
        eskLong.GetChainCode().Serialize(chainCode);
    }

    @Test
    public void shouldMatchDeriviationThroughPrivateAndPublicKeys() {
        byte [] seed = new byte []{1, 50, 6, (byte)244, 24, (byte)199, 1, 25};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));
        ExtendedPublicKey epk = esk.GetExtendedPublicKey();

        PublicKey pk1 = esk.PrivateChild(238757).GetPublicKey();
        PublicKey pk2 = epk.PublicChild(238757).GetPublicKey();

        assertTrue(pk1.equals(pk2));

        PrivateKey sk3 = esk.PrivateChild(0)
                .PrivateChild(3)
                .PrivateChild(8)
                .PrivateChild(1)
                .GetPrivateKey();

        PublicKey pk4 = epk.PublicChild(0)
                .PublicChild(3)
                .PublicChild(8)
                .PublicChild(1)
                .GetPublicKey();
        assertTrue(sk3.GetPublicKey().equals(pk4));

        Signature sig = sk3.Sign(seed, sizeof(seed));

        assertTrue(sig.Verify());
    }

    @Test
    public void shouldPreventHardenedPKDerivation() {
        byte [] seed = {1, 50, 6, (byte)244, 24, (byte)199, 1, 25};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));
        ExtendedPublicKey epk = esk.GetExtendedPublicKey();

        ExtendedPrivateKey sk = esk.PrivateChild((1 << 31) + 3);
        try {
            epk.PublicChild((1 << 31) + 3);
            fail();
        } catch (Exception x) {

        }
    }

    @Test
    public void shouldDerivePublicChildFromParent() {
        byte [] seed = new byte[]{1, 50, 6, (byte)244, 24, (byte)199, 1, 0, 0, 0};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));
        ExtendedPublicKey epk = esk.GetExtendedPublicKey();

        ExtendedPublicKey pk1 = esk.PublicChild(13);
        ExtendedPublicKey pk2 = epk.PublicChild(13);

        assertTrue(pk1.equals(pk2));
    }

    @Test
    public void shouldPrintStructures() {
        byte [] seed = new byte[]{1, 50, 6, (byte)244, 24, (byte)199, 1, 0, 0, 0};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));
        ExtendedPublicKey epk = esk.GetExtendedPublicKey();

        System.out.println(epk);
        System.out.println(epk.GetPublicKey());
        System.out.println(epk.GetChainCode());

        Signature sig1 = esk.GetPrivateKey()
                .Sign(seed, sizeof(seed));
        System.out.println(sig1);
    }

    @Test
    public void shouldSerializeExtendedKeys() {
        byte [] seed = new byte[]{1, 50, 6, (byte)244, 25, (byte)199, 1, 25};
        ExtendedPrivateKey esk = ExtendedPrivateKey.FromSeed(
                seed, sizeof(seed));
        ExtendedPublicKey epk = esk.GetExtendedPublicKey();

        PublicKey pk1 = esk.PrivateChild(238757).GetPublicKey();
        PublicKey pk2 = epk.PublicChild(238757).GetPublicKey();

        assertTrue(pk1.equals(pk2));

        ExtendedPrivateKey sk3 = esk.PrivateChild(0)
                .PrivateChild(3)
                .PrivateChild(8)
                .PrivateChild(1);

        ExtendedPublicKey pk4 = epk.PublicChild(0)
                .PublicChild(3)
                .PublicChild(8)
                .PublicChild(1);
        byte [] buffer1 = new byte[(int)ExtendedPrivateKey.EXTENDED_PRIVATE_KEY_SIZE];
        byte [] buffer2 = new byte[(int)ExtendedPublicKey.EXTENDED_PUBLIC_KEY_SIZE];
        byte [] buffer3 = new byte[(int)ExtendedPublicKey.EXTENDED_PUBLIC_KEY_SIZE];

        sk3.Serialize(buffer1);
        sk3.GetExtendedPublicKey().Serialize(buffer2);
        pk4.Serialize(buffer3);
        assertTrue(Arrays.equals(buffer2, buffer3));
    }
    

    //TEST_CASE("AggregationInfo") {
    @Test
    public void shouldCreateObject() {
        byte [] message1 = new byte[]{1, 65, (byte)254, 88, 90, 45, 22};
        byte [] message2 = new byte[]{1, 65, (byte)254, 88, 90, 45, 22, 12};
        byte [] message3 = new byte[]{2, 65, (byte)254, 88, 90, 45, 22, 12};
        byte [] message4 = new byte[]{3, 65, (byte)254, 88, 90, 45, 22, 12};
        byte [] message5 = new byte[]{4, 65, (byte)254, 88, 90, 45, 22, 12};
        byte [] message6 = new byte[]{5, 65, (byte)254, 88, 90, 45, 22, 12};
        byte [] messageHash1 = getSHA256Hash(message1);
        byte [] messageHash2 = getSHA256Hash(message2);
        byte [] messageHash3 = getSHA256Hash(message3);
        byte [] messageHash4 = getSHA256Hash(message4);
        byte [] messageHash5 = getSHA256Hash(message5);
        byte [] messageHash6 = getSHA256Hash(message6);

        byte [] seed = getRandomSeed(32);
        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        seed = getRandomSeed(32);
        PrivateKey sk2 = PrivateKey.FromSeed(seed, 32);
        seed = getRandomSeed(32);
        PrivateKey sk3 = PrivateKey.FromSeed(seed, 32);
        seed = getRandomSeed(32);
        PrivateKey sk4 = PrivateKey.FromSeed(seed, 32);
        seed = getRandomSeed(32);
        PrivateKey sk5 = PrivateKey.FromSeed(seed, 32);
        seed = getRandomSeed(32);
        PrivateKey sk6 = PrivateKey.FromSeed(seed, 32);

        PublicKey pk1 = sk1.GetPublicKey();
        PublicKey pk2 = sk2.GetPublicKey();
        PublicKey pk3 = sk3.GetPublicKey();
        PublicKey pk4 = sk4.GetPublicKey();
        PublicKey pk5 = sk5.GetPublicKey();
        PublicKey pk6 = sk6.GetPublicKey();

        AggregationInfo a1 = AggregationInfo.FromMsgHash(pk1, messageHash1);
        AggregationInfo a2 = AggregationInfo.FromMsgHash(pk2, messageHash2);
        AggregationInfoVector infosA = new AggregationInfoVector();
        infosA.push_back(a1);
        infosA.push_back(a2);
        AggregationInfoVector infosAcopy = new AggregationInfoVector();
        infosAcopy.push_back(a2);
        infosAcopy.push_back(a1);
        AggregationInfo a3 = AggregationInfo.FromMsgHash(pk3, messageHash1);
        AggregationInfo a4 = AggregationInfo.FromMsgHash(pk4, messageHash1);
        AggregationInfoVector infosB = new AggregationInfoVector();
        infosB.push_back(a3);
        infosB.push_back(a4);
        AggregationInfoVector infosBcopy = new AggregationInfoVector();
        infosBcopy.push_back(a4);
        infosBcopy.push_back(a3);
        AggregationInfoVector infosC = new AggregationInfoVector();
        infosC.push_back(a1);
        infosC.push_back(a2);
        infosC.push_back(a3);
        infosC.push_back(a4);
        AggregationInfo a5 = AggregationInfo.MergeInfos(infosA);
        AggregationInfo a5b = AggregationInfo.MergeInfos(infosAcopy);
        AggregationInfo a6 = AggregationInfo.MergeInfos(infosB);
        AggregationInfo a6b = AggregationInfo.MergeInfos(infosBcopy);
        AggregationInfoVector infosD = new AggregationInfoVector();
        infosD.push_back(a5);
        infosD.push_back(a6);

        AggregationInfo a7 = AggregationInfo.MergeInfos(infosC);
        AggregationInfo a8 = AggregationInfo.MergeInfos(infosD);

        assertTrue(a5.equals(a5b));
        assertTrue(!a5.equals(a6));
        assertTrue(a6.equals(a6b));

        AggregationInfoVector infosE = new AggregationInfoVector();
        infosE.push_back(a1);
        infosE.push_back(a3);
        infosE.push_back(a4);
        AggregationInfo a9 = AggregationInfo.MergeInfos(infosE);
        AggregationInfoVector infosF = new AggregationInfoVector();
        infosF.push_back(a2);
        infosF.push_back(a9);
        AggregationInfo a10 = AggregationInfo.MergeInfos(infosF);

        assertTrue(a10.equals(a7));

        AggregationInfo a11 = AggregationInfo.FromMsgHash(pk1, messageHash1);
        AggregationInfo a12 = AggregationInfo.FromMsgHash(pk2, messageHash2);
        AggregationInfo a13 = AggregationInfo.FromMsgHash(pk3, messageHash3);
        AggregationInfo a14 = AggregationInfo.FromMsgHash(pk4, messageHash4);
        AggregationInfo a15 = AggregationInfo.FromMsgHash(pk5, messageHash5);
        AggregationInfo a16 = AggregationInfo.FromMsgHash(pk6, messageHash6);
        AggregationInfo a17 = AggregationInfo.FromMsgHash(pk6, messageHash5);
        AggregationInfo a18 = AggregationInfo.FromMsgHash(pk5, messageHash6);

        // Tree L
        AggregationInfoVector L1 = new AggregationInfoVector();
        L1.push_back(a15);
        L1.push_back(a17);
        AggregationInfoVector L2 = new AggregationInfoVector();
        L2.push_back(a11);
        L2.push_back(a13);
        AggregationInfoVector L3 = new AggregationInfoVector();
        L3.push_back(a18);
        L3.push_back(a14);

        AggregationInfo a19 = AggregationInfo.MergeInfos(L1);
        AggregationInfo a20 = AggregationInfo.MergeInfos(L2);
        AggregationInfo a21 = AggregationInfo.MergeInfos(L3);

        AggregationInfoVector L4 = new AggregationInfoVector();
        L4.push_back(a21);
        L4.push_back(a16);
        AggregationInfoVector L5 = new AggregationInfoVector();
        L5.push_back(a19);
        L5.push_back(a20);
        AggregationInfo a22 = AggregationInfo.MergeInfos(L4);
        AggregationInfo a23 = AggregationInfo.MergeInfos(L5);

        AggregationInfoVector L6 = new AggregationInfoVector();
        L6.push_back(a22);
        L6.push_back(a12);
        AggregationInfo a24 = AggregationInfo.MergeInfos(L6);
        AggregationInfoVector L7 = new AggregationInfoVector();
        L7.push_back(a23);
        L7.push_back(a24);
        AggregationInfo LFinal = AggregationInfo.MergeInfos(L7);

        // Tree R
        AggregationInfoVector R1 = new AggregationInfoVector();
        R1.push_back(a17);
        R1.push_back(a12);
        R1.push_back(a11);
        R1.push_back(a15);
        AggregationInfoVector R2 = new AggregationInfoVector();
        R2.push_back(a14);
        R2.push_back(a18);


        AggregationInfo a25 = AggregationInfo.MergeInfos(R1);
        AggregationInfo a26 = AggregationInfo.MergeInfos(R2);

        AggregationInfoVector R3 = new AggregationInfoVector();
        R3.push_back(a26);
        R3.push_back(a16);

        AggregationInfo a27 = AggregationInfo.MergeInfos(R3);

        AggregationInfoVector R4 = new AggregationInfoVector();
        R4.push_back(a27);
        R4.push_back(a13);

        AggregationInfo a28 = AggregationInfo.MergeInfos(R4);
        AggregationInfoVector R5 = new AggregationInfoVector();
        R5.push_back(a25);
        R5.push_back(a28);

        AggregationInfo RFinal = AggregationInfo.MergeInfos(R5);

        assertTrue(LFinal.equals(RFinal));
    }

    @Test
    public void shouldAggregateWithMultipleLevels() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] message2 = {(byte)192, 29, 2, 0, 0, 45, 23, (byte)192};
        byte [] message3 = {52, 29, 2, 0, 0, 45, 102};
        byte [] message4 = {99, 29, 2, 0, 0, 45, (byte)222};

        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);

        PublicKey pk1 = sk1.GetPublicKey();
        PublicKey pk2 = sk2.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message2, sizeof(message2));
        Signature sig3 = sk2.Sign(message1, sizeof(message1));
        Signature sig4 = sk1.Sign(message3, sizeof(message3));
        Signature sig5 = sk1.Sign(message4, sizeof(message4));
        Signature sig6 = sk1.Sign(message1, sizeof(message1));

        final SignatureVector sigsL = new SignatureVector();
        sigsL.push_back(sig1);
        sigsL.push_back(sig2);
        final PublicKeyVector pksL = new PublicKeyVector();
        pksL.push_back(pk1);
        pksL.push_back(pk2);
        final Signature aggSigL = Signature.AggregateSigs(sigsL);

        final SignatureVector sigsR = new SignatureVector();
        sigsR.push_back(sig3);
        sigsR.push_back(sig4);
        sigsR.push_back(sig6);
        final Signature aggSigR = Signature.AggregateSigs(sigsR);

        PublicKeyVector pk1Vec = new PublicKeyVector();
        pk1Vec.push_back(pk1);

        SignatureVector sigs = new SignatureVector();
        sigs.push_back(aggSigL);
        sigs.push_back(aggSigR);
        sigs.push_back(sig5);

        final Signature aggSig = Signature.AggregateSigs(sigs);
        assertTrue(aggSig.Verify());
    }

    @Test
    public void shouldAggregateWithMultipleLevelsDegenerate() {
        byte [] message1 = new byte[]{100, 2, (byte)254, 88, 90, 45, 23};
        byte [] seed = getRandomSeed(32);
        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PublicKey pk1 = sk1.GetPublicKey();
        Signature aggSig = sk1.Sign(message1, sizeof(message1));

        for (int i = 0; i < 10; i++) {
            seed = getRandomSeed(32);
            PrivateKey sk = PrivateKey.FromSeed(seed, 32);
            PublicKey pk = sk.GetPublicKey();
            Signature sig = sk.Sign(message1, sizeof(message1));
            SignatureVector sigs = new SignatureVector();
            sigs.push_back(aggSig);
            sigs.push_back(sig);
            aggSig = Signature.AggregateSigs(sigs);
        }
        assertTrue(aggSig.Verify());
        byte [] sigSerialized = new byte[(int)Signature.SIGNATURE_SIZE];
        aggSig.Serialize(sigSerialized);

        final Signature aggSig2 = Signature.FromBytes(sigSerialized);
        assertTrue(aggSig2.Verify() == false);
    }

    @Test
    public void shouldAggregateWithMultipleLevelsDifferentMessage() {
        byte [] message1 = {100, 2, (byte)254, 88, 90, 45, 23};
        byte [] message2 = {(byte)192, 29, 2, 0, 0, 45, 23};
        byte [] message3 = {52, 29, 2, 0, 0, 45, 102};
        byte [] message4 = {99, 29, 2, 0, 0, 45, (byte)222};

        byte [] seed = getRandomSeed(32);
        byte [] seed2 = getRandomSeed(32);

        PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
        PrivateKey sk2 = PrivateKey.FromSeed(seed2, 32);

        PublicKey pk1 = sk1.GetPublicKey();
        PublicKey pk2 = sk2.GetPublicKey();

        Signature sig1 = sk1.Sign(message1, sizeof(message1));
        Signature sig2 = sk2.Sign(message2, sizeof(message2));
        Signature sig3 = sk2.Sign(message3, sizeof(message4));
        Signature sig4 = sk1.Sign(message4, sizeof(message4));

        SignatureVector sigsL = new SignatureVector();
        sigsL.push_back(sig1);
        sigsL.push_back(sig2);
        PublicKeyVector pksL = new PublicKeyVector();
        pksL.push_back(pk1);
        pksL.push_back(pk2);
        final byte [][] messagesL = {message1, message2};

        final Signature aggSigL = Signature.AggregateSigs(sigsL);

        SignatureVector  sigsR = new SignatureVector();
        sigsR.push_back(sig3);
        sigsR.push_back(sig4);
        final PublicKeyVector pksR = new PublicKeyVector();
        pksR.push_back(pk2);
        pksR.push_back(pk1);
        final byte [][] messagesR = {message3, message4};

        final Signature aggSigR = Signature.AggregateSigs(sigsR);

        SignatureVector sigs = new SignatureVector();
        sigs.push_back(aggSigL);
        sigs.push_back(aggSigR);
        //PublicKeyVector pks = new PublicKeyVector();
        //pks.push_back(pksL);
        //pks.push_back(pksR);
        //final byte [][][] messages = {messagesL, messagesR};

        final Signature aggSig = Signature.AggregateSigs(sigs);

        PublicKeyVector allPks = new PublicKeyVector();
        allPks.push_back(pk1);
        allPks.push_back(pk2);
        allPks.push_back(pk2);
        allPks.push_back(pk1);
        final byte [][] allMessages = {message1, message2,
                message3, message4};

        assertTrue(aggSig.Verify());
    }


}
