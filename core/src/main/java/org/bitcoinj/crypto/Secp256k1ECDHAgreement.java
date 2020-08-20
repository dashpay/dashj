/*
 * Copyright 2020 Dash Core Group
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.crypto;

import org.bitcoinj.core.Sha256Hash;
import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECConstants;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

/**
 * P1363 7.2.1 ECSVDP-DH
 *
 * ECSVDP-DH is Elliptic Curve Secret Value Derivation Primitive,
 * Diffie-Hellman version. It is based on the work of [DH76], [Mil86],
 * and [Kob87]. This primitive derives a shared secret value from one
 * party's private key and another party's public key, where both have
 * the same set of EC domain parameters. If two parties correctly
 * execute this primitive, they will produce the same output. This
 * primitive can be invoked by a scheme to derive a shared secret key;
 * specifically, it may be used with the schemes ECKAS-DH1 and
 * DL/ECKAS-DH2. It assumes that the input keys are valid (see also
 * Section 7.2.2).
 *
 * This also follows the derivation in secp256k1_ecdh, which computes
 * the hash of the version (taken from the last byte of y) and x.
 */
public class Secp256k1ECDHAgreement
        implements BasicAgreement
{
    private ECPrivateKeyParameters key;

    public void init(
            CipherParameters key)
    {
        this.key = (ECPrivateKeyParameters)key;
    }

    public int getFieldSize()
    {
        return (key.getParameters().getCurve().getFieldSize() + 7) / 8;
    }

    public BigInteger calculateAgreement(
            CipherParameters pubKey)
    {
        ECPublicKeyParameters pub = (ECPublicKeyParameters)pubKey;
        ECDomainParameters params = key.getParameters();
        if (!params.equals(pub.getParameters()))
        {
            throw new IllegalStateException("ECDH public key has wrong domain parameters");
        }

        BigInteger d = key.getD();

        // Always perform calculations on the exact curve specified by our private key's parameters
        ECPoint Q = ECAlgorithms.cleanPoint(params.getCurve(), pub.getQ());
        if (Q.isInfinity())
        {
            throw new IllegalStateException("Infinity is not a valid public key for ECDH");
        }

        BigInteger h = params.getH();
        if (!h.equals(ECConstants.ONE))
        {
            d = params.getHInv().multiply(d).mod(params.getN());
            Q = ECAlgorithms.referenceMultiply(Q, h);
        }

        ECPoint P = Q.multiply(d).normalize();
        if (P.isInfinity())
        {
            throw new IllegalStateException("Infinity is not a valid agreement value for ECDH");
        }

        // this method differs from calculateAgreement in ECDHBasicAgreement which returns
        // P.getAffineXCoord().toBigInteger().  Since this class follows the secp256k1_ecdh
        // key derivation method, we will need to compute the hash of the point by
        // SHA256((y[31]&0x2|0x1) + x) which is the hash of the version and x
        byte [] x32 = P.getAffineXCoord().getEncoded();
        byte [] y32 = P.getAffineYCoord().getEncoded();
        byte [] x32withVersion = new byte [x32.length + 1];
        x32withVersion[0] = (byte)((y32[y32.length - 1] & 0x01) | 0x02);
        System.arraycopy(x32, 0, x32withVersion, 1, 32);
        return new BigInteger(Sha256Hash.hash(x32withVersion));
    }
}
