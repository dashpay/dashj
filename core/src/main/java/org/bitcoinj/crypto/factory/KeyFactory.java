package org.bitcoinj.crypto.factory;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyType;

import java.math.BigInteger;

public interface KeyFactory {

    // Keys
    IKey newKey();
    IKey fromEncrypted (EncryptedData e, KeyCrypter keyCrypter, byte [] pub);
    IKey fromPrivate(BigInteger bi);
    IKey fromPrivate(byte[] privateKeyBytes, boolean pubKeyCompressed);
    IKey fromPrivateAndPrecalculatedPublic(byte [] priv, byte[] pub);
    IKey fromPublicOnly(byte[] pub);
    IKey fromPublicOnly(IKey key);

    // Deterministic Keys
    IDeterministicKey fromExtended(ImmutableList<ChildNumber> immutablePath, byte [] chainCode, byte[] pubkey, byte [] priv, IDeterministicKey parent);
    IDeterministicKey fromExtendedEncrypted(ImmutableList<ChildNumber> immutablePath, byte [] chainCode, KeyCrypter keyCrypter, byte[] pubkey, EncryptedData data, IDeterministicKey parent);
    IDeterministicKey fromChildAndParent(IDeterministicKey child, IDeterministicKey parent);
    IDeterministicKey deserializeB58(String base58, NetworkParameters params);
    IDeterministicKey deserializeB58(IDeterministicKey parent, String pub58, NetworkParameters params);
    IDeterministicKey deserializeB58(String base58, ImmutableList<ChildNumber> path, NetworkParameters params);
    IDeterministicKey createMasterPrivateKey(byte[] checkNotNull);

    // other information (key type, WIF suffix, etc)
    KeyType getKeyType();
    byte getDumpedPrivateKeyLastByte();

}
