/*
 * Copyright 2013 Matija Mazi.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2023 Dash Core Group
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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * A deterministic key is a node in a {@link DeterministicHierarchy}. As per
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">the BIP 32 specification</a> it is a pair
 * (key, chaincode). If you know its path in the tree and its chain code you can derive more keys from this. To obtain
 * one of these, you can call {@link HDKeyDerivation#createMasterPrivateKey(byte[])}.
 */
public interface IDeterministicKey extends IKey {

    /** Sorts deterministic keys in the order of their child number. That's <i>usually</i> the order used to derive them. */
    Comparator<IKey> CHILDNUM_ORDER = new Comparator<IKey>() {
        @Override
        public int compare(IKey k1, IKey k2) {
            ChildNumber cn1 = ((IDeterministicKey) k1).getChildNumber();
            ChildNumber cn2 = ((IDeterministicKey) k2).getChildNumber();
            return cn1.compareTo(cn2);
        }
    };


    /**
     * Returns the path through some {@link DeterministicHierarchy} which reaches this keys position in the tree.
     * A path can be written as 0/1/0 which means the first child of the root, the second child of that node, then
     * the first child of that node.
     */
    HDPath getPath();
    
    /**
     * Returns the path of this key as a human readable string starting with M to indicate the master key.
     */
    String getPathAsString();

    /**
     * Return this key's depth in the hierarchy, where the root node is at depth zero.
     * This may be different than the number of segments in the path if this key was
     * deserialized without access to its parent.
     */
    int getDepth();

    /** Returns the last element of the path returned by {@link IDeterministicKey#getPath()} */
    ChildNumber getChildNumber();

    /**
     * Returns the chain code associated with this key. See the specification to learn more about chain codes.
     */
    byte[] getChainCode();

    /**
     * Returns RIPE-MD160(SHA256(pub key bytes)).
     */
    byte[] getIdentifier();

    /** Returns the first 32 bits of the result of {@link #getIdentifier()}. */
    int getFingerprint();

    @Nullable
    IDeterministicKey getParent();

    /**
     * Return the fingerprint of the key from which this key was derived, if this is a
     * child key, or else an array of four zero-value bytes.
     */
    int getParentFingerprint();

    IDeterministicKey dropPrivateBytes();

    /**
     * <p>Returns the same key with the parent pointer removed (it still knows its own path and the parent fingerprint).</p>
     *
     * <p>If this key doesn't have private key bytes stored/cached itself, but could rederive them from the parent, then
     * the new key returned by this method won't be able to do that. Thus, using dropPrivateBytes().dropParent() on a
     * regular DeterministicKey will yield a new DeterministicKey that cannot sign or do other things involving the
     * private key at all.</p>
     */
    IDeterministicKey dropParent();

    IDeterministicKey encrypt(KeyCrypter keyCrypter, KeyParameter aesKey, @Nullable IDeterministicKey newParent) throws KeyCrypterException;

    @Nullable
    @Override
    byte[] getSecretBytes();

    byte[] getPrivKeyBytes33();

    /**
     * A deterministic key is considered to be encrypted if it has access to encrypted private key bytes, OR if its
     * parent does. The reason is because the parent would be encrypted under the same key and this key knows how to
     * rederive its own private key bytes from the parent, if needed.
     */
    @Override
    boolean isEncrypted();


    /**
     * Derives a child at the given index using hardened derivation.  Note: {@code index} is
     * not the "i" value.  If you want the softened derivation, then use instead
     * {@code HDKeyDerivation.deriveChildKey(this, new ChildNumber(child, false))}.
     */
    IDeterministicKey derive(int child);

    String serializePubB58(NetworkParameters params);
    String serializePubB58(NetworkParameters params, Script.ScriptType outputScriptType);
    String serializePrivB58(NetworkParameters params);
    String serializePrivB58(NetworkParameters params, Script.ScriptType outputScriptType);

    IDeterministicKey deriveChildKey(ChildNumber childNumber);
    IDeterministicKey deriveThisOrNextChildKey(int nextChild);
}
