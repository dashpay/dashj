package org.dashj.bls;

import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.fail;

public class BlsSignatureTest extends BaseTest {

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

}
