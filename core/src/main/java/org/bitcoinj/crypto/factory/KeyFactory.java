package org.bitcoinj.crypto.factory;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyType;

public interface KeyFactory {

    IKey newKey();
    IKey fromEncrypted (EncryptedData e, KeyCrypter keyCrypter, byte [] pub);
    IKey fromPrivate(byte[] privateKeyBytes, boolean pubKeyCompressed);
    IKey fromPrivateAndPrecalculatedPublic(byte [] priv, byte[] pub);
    IKey fromPublicOnly(byte[] pub);

    IDeterministicKey fromExtended(ImmutableList<ChildNumber> immutablePath, byte [] chainCode, byte[] pubkey, byte [] priv, IDeterministicKey parent);
    IDeterministicKey fromExtendedEncrypted(ImmutableList<ChildNumber> immutablePath, byte [] chainCode, KeyCrypter keyCrypter, byte[] pubkey, EncryptedData data, IDeterministicKey parent);
    IDeterministicKey fromChildAndParent(IDeterministicKey child, IDeterministicKey parent);
    IDeterministicKey deserializeB58(IDeterministicKey parent, String pub58, NetworkParameters mainnet);
    IDeterministicKey createMasterPrivateKey(byte[] checkNotNull);
    KeyType getKeyType();
}
