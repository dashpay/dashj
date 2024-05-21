/*
 * Copyright 2022 Dash Core Group
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

package org.bitcoinj.examples;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.crypto.BLSSignature;
import org.dashj.bls.*;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Random;


public class BLSVerifySignature {

    private static final Random rand = new Random();
    static {
        BLS.Init();
    }

    private static byte [] getRandomSeed(int size) {
        BigInteger result = new BigInteger((size) *8 - 1, rand); // (2^4-1) = 15 is the maximum value
        byte [] bytes = new byte [32];
        System.arraycopy(result.toByteArray(), 0, bytes, 0, result.toByteArray().length);
        return bytes;
    }

    private static byte [] getSHA256Hash(byte [] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return hash;
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static int sizeof(byte [] bytes) {
        return bytes.length;
    }


    public static void main(String[] args) throws Exception {
        System.out.println("BLS-Signature Test");
        byte [] message = getRandomSeed(32);
        byte[] hash = getSHA256Hash(message);
        Sha256Hash sha256Hash = Sha256Hash.wrap(hash);
        String hashStr = sha256Hash.toString();
        System.out.println("message = " + Sha256Hash.wrap(message));
        Stopwatch watch = Stopwatch.createStarted();

        for (int i = 0; i < 1000000; i++) {
            byte[] seed = getRandomSeed(32);
            PrivateKey sk1 = PrivateKey.FromSeed(seed, 32);
            InsecureSignature sig1 = sk1.SignInsecure(message, sizeof(message));
            PublicKey pk1 = sk1.GetPublicKey();
            Preconditions.checkState(sig1.Verify(hash, pk1), "failed verification:" + sig1 + ".Verify(" + hashStr, ", " + pk1 + ") " + sk1);

            PublicKeyVector pks = new PublicKeyVector();
            pks.push_back(pk1);

            MessageHashVector messages = new MessageHashVector();
            messages.push_back(message);

            byte [] skBytes = sk1.Serialize();
            BLSSecretKey secretKey = new BLSSecretKey(skBytes);
            BLSSignature signature = secretKey.sign(sha256Hash);
            BLSPublicKey publicKey = secretKey.getPublicKey();
            ArrayList<BLSPublicKey> pks2 = new ArrayList<>(1);
            pks2.add(publicKey);

            ArrayList<Sha256Hash> messageList = new ArrayList<>(1);
            messageList.add(sha256Hash);

            Preconditions.checkState(signature.verifyInsecureAggregated(pks2, messageList), "failed verification 2:" + signature + ".Verify(" + hashStr, ", " + pk1 + ") " + sk1);


            if(i % 1000 == 0)
                System.out.println("verifying " + i + " " + sk1.toString());
        }
        watch.stop();
        System.out.println("completed test in " + watch.toString());
    }
}
