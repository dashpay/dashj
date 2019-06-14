package org.dashj.bls;

import com.google.common.io.BaseEncoding;
import org.junit.Test;

import java.math.BigInteger;

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
        //skChild = ExtendedPrivateKey.FromSeed(new byte [0], 0);
        //skChild.GetPrivateKey().Sign(new byte [0], -);
    }

    @Test
    public void voidNullTest() {
        try {
            InsecureSignature insecureSignature = InsecureSignature.FromBytes(null);
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

}
