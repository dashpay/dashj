/*
 * Copyright 2013 Google Inc.
 * Copyright 2018 Andreas Schildbach
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

package org.bitcoinj.wallet;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.bls.BLSDeterministicKey;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.AnyDeterministicKeyChain;
import org.bitcoinj.wallet.AnyKeyChainGroup;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.listeners.AbstractKeyChainEventListener;
import org.bouncycastle.crypto.params.KeyParameter;
import org.dashj.bls.ExtendedPrivateKey;
import org.dashj.bls.ExtendedPublicKey;
import org.dashj.bls.PrivateKey;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AnyDeterministicKeyChainTest {
    private AnyDeterministicKeyChain chain;
    private AnyDeterministicKeyChain bip44chain;
    private final byte[] ENTROPY = Sha256Hash.hash("don't use a string seed like this in real life".getBytes());
    private static final NetworkParameters UNITTEST = UnitTestParams.get();
    private static final NetworkParameters MAINNET = MainNetParams.get();
    //private static final Context context = Context.getOrCreate(UNITTEST);
    private static final ImmutableList<ChildNumber> BIP44_ACCOUNT_ONE_PATH = ImmutableList.of(new ChildNumber(44, true),
            new ChildNumber(1, true), ChildNumber.ZERO_HARDENED);

    @Before
    public void setup() {
        BriefLogFormatter.init();
        // You should use a random seed instead. The secs constant comes from the unit test file, so we can compare
        // serialized data properly.
        long secs = 1389353062L;
        chain = AnyDeterministicKeyChain.builder().entropy(ENTROPY, secs)
                .accountPath(AnyDeterministicKeyChain.ACCOUNT_ZERO_PATH)
                .outputScriptType(Script.ScriptType.P2PKH)
                .keyFactory(ECKeyFactory.get())
                .build();
        chain.setLookaheadSize(10);

        bip44chain = AnyDeterministicKeyChain.builder().entropy(ENTROPY, secs)
                .accountPath(BIP44_ACCOUNT_ONE_PATH)
                .outputScriptType(Script.ScriptType.P2PKH)
                .keyFactory(ECKeyFactory.get())
                .build();
        bip44chain.setLookaheadSize(10);
    }

    @Test
    public void derive() throws Exception {
        IKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        assertFalse(key1.isPubKeyOnly());
        IKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        assertFalse(key2.isPubKeyOnly());

        final Address address = Address.fromBase58(UNITTEST, "ygPtvwtJj99Ava2fnU3WAW9T9VwmPuyTV1");
        assertEquals(address, Address.fromKey(UNITTEST, key1));
        assertEquals("yT5yAz7rX677oKD6ETsf3pUxYVzDoRXWeQ", Address.fromKey(UNITTEST, key2).toString());
        assertEquals(key1, chain.findKeyFromPubHash(address.getHash()));
        assertEquals(key2, chain.findKeyFromPubKey(key2.getPubKey()));

        key1.signHash(Sha256Hash.ZERO_HASH);
        assertFalse(key1.isPubKeyOnly());

        IKey key3 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertFalse(key3.isPubKeyOnly());
        assertEquals("yWiFqq8aRcSKEwuPhMRxAnvJ5QQw4F7cuz", Address.fromKey(UNITTEST, key3).toString());
        key3.signHash(Sha256Hash.ZERO_HASH);
        assertFalse(key3.isPubKeyOnly());
    }

    @Test
    public void getKeys() throws Exception {
        chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        chain.getKey(KeyChain.KeyPurpose.CHANGE);
        chain.maybeLookAhead();
        assertEquals(2, chain.getKeys(false, false).size());
    }

    @Test
    public void deriveAccountOne() throws Exception {
        final long secs = 1389353062L;
        final ImmutableList<ChildNumber> accountOne = ImmutableList.of(ChildNumber.ONE);
        AnyDeterministicKeyChain chain1 = AnyDeterministicKeyChain.builder().accountPath(accountOne)
                .entropy(ENTROPY, secs).keyFactory(chain.getKeyFactory()).build();
        IKey key1 = chain1.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IKey key2 = chain1.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        final Address address = Address.fromBase58(UNITTEST, "yhamqZwDhh9yABWRCsDipdTUiHy2vn5fka");
        assertEquals(address, Address.fromKey(UNITTEST, key1));
        assertEquals("yTcXHJdvgDoKhd7S68WYGARWpVfzMuaQ85", Address.fromKey(UNITTEST, key2).toString());
        assertEquals(key1, chain1.findKeyFromPubHash(address.getHash()));
        assertEquals(key2, chain1.findKeyFromPubKey(key2.getPubKey()));

        key1.signHash(Sha256Hash.ZERO_HASH);

        IKey key3 = chain1.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals("yVXvFteQT9vXeLqPhn8eZBPEFbXdzJgphs", Address.fromKey(UNITTEST, key3).toString());
        key3.signHash(Sha256Hash.ZERO_HASH);
    }

    @Test
    public void serializeAccountOne() throws Exception {
        final long secs = 1389353062L;
        final ImmutableList<ChildNumber> accountOne = ImmutableList.of(ChildNumber.ONE);
        AnyDeterministicKeyChain chain1 = AnyDeterministicKeyChain.builder().accountPath(accountOne)
                .entropy(ENTROPY, secs).keyFactory(chain.getKeyFactory()).build();
        IKey key1 = chain1.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        final Address address = Address.fromBase58(UNITTEST, "yhamqZwDhh9yABWRCsDipdTUiHy2vn5fka");
        assertEquals(address, Address.fromKey(UNITTEST, key1));

        IDeterministicKey watching = chain1.getWatchingKey();

        List<Protos.Key> keys = chain1.serializeToProtobuf();
        chain1 = AnyDeterministicKeyChain.fromProtobuf(keys, null, chain1.getKeyFactory(), false).get(0);
        assertEquals(accountOne, chain1.getAccountPath());

        IKey key2 = chain1.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        assertEquals("yTcXHJdvgDoKhd7S68WYGARWpVfzMuaQ85", Address.fromKey(UNITTEST, key2).toString());
        assertEquals(key1, chain1.findKeyFromPubHash(address.getHash()));
        assertEquals(key2, chain1.findKeyFromPubKey(key2.getPubKey()));

        key1.signHash(Sha256Hash.ZERO_HASH);

        IKey key3 = chain1.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals("yVXvFteQT9vXeLqPhn8eZBPEFbXdzJgphs", Address.fromKey(UNITTEST, key3).toString());
        key3.signHash(Sha256Hash.ZERO_HASH);

        assertEquals(watching, chain1.getWatchingKey());
    }

    @Test
    public void signMessage() throws Exception {
        IKey key = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        key.verifyMessage("test", key.signMessage("test"));
    }

    @Test
    public void events() throws Exception {
        // Check that we get the right events at the right time.
        final List<List<IKey>> listenerKeys = new ArrayList<>();
        long secs = 1389353062L;
        chain = AnyDeterministicKeyChain.builder().entropy(ENTROPY, secs).outputScriptType(Script.ScriptType.P2PKH)
                .build();
        chain.addEventListener(new AbstractKeyChainEventListener() {
            @Override
            public void onKeysAdded(List<IKey> keys) {
                listenerKeys.add(keys);
            }
        }, Threading.SAME_THREAD);
        assertEquals(0, listenerKeys.size());
        chain.setLookaheadSize(5);
        assertEquals(0, listenerKeys.size());
        IKey key = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(1, listenerKeys.size());  // 1 event
        final List<IKey> firstEvent = listenerKeys.get(0);
        assertEquals(1, firstEvent.size());
        assertTrue(firstEvent.contains(key));   // order is not specified.
        listenerKeys.clear();

        chain.maybeLookAhead();
        final List<IKey> secondEvent = listenerKeys.get(0);
        assertEquals(12, secondEvent.size());  // (5 lookahead keys, +1 lookahead threshold) * 2 chains
        listenerKeys.clear();

        chain.getKey(KeyChain.KeyPurpose.CHANGE);
        // At this point we've entered the threshold zone so more keys won't immediately trigger more generations.
        assertEquals(0, listenerKeys.size());  // 1 event
        final int lookaheadThreshold = chain.getLookaheadThreshold() + chain.getLookaheadSize();
        for (int i = 0; i < lookaheadThreshold; i++)
            chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(1, listenerKeys.size());  // 1 event
        assertEquals(1, listenerKeys.get(0).size());  // 1 key.
    }

    @Test
    public void random() {
        // Can't test much here but verify the constructor worked and the class is functional. The other tests rely on
        // a fixed seed to be deterministic.
        chain = AnyDeterministicKeyChain.builder().random(new SecureRandom(), 384).build();
        chain.setLookaheadSize(10);
        chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).signHash(Sha256Hash.ZERO_HASH);
        chain.getKey(KeyChain.KeyPurpose.CHANGE).signHash(Sha256Hash.ZERO_HASH);
    }

    @Test
    public void serializeUnencrypted() throws UnreadableWalletException {
        chain.maybeLookAhead();
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key3 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        List<Protos.Key> keys = chain.serializeToProtobuf();
        // 1 mnemonic/seed, 1 master key, 1 account key, 2 internal keys, 3 derived, 20 lookahead and 5 lookahead threshold.
        int numItems =
                1  // mnemonic/seed
              + 1  // master key
              + 1  // account key
              + 2  // ext/int parent keys
              + (chain.getLookaheadSize() + chain.getLookaheadThreshold()) * 2   // lookahead zone on each chain
        ;
        assertEquals(numItems, keys.size());

        // Get another key that will be lost during round-tripping, to ensure we can derive it again.
        IDeterministicKey key4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);

        final String EXPECTED_SERIALIZATION = checkSerialization(keys, "deterministic-wallet-serialization.txt");

        // Round trip the data back and forth to check it is preserved.
        int oldLookaheadSize = chain.getLookaheadSize();
        chain = AnyDeterministicKeyChain.fromProtobuf(keys, null, chain.getKeyFactory(), false).get(0);
        assertEquals(AnyDeterministicKeyChain.ACCOUNT_ZERO_PATH, chain.getAccountPath());
        assertEquals(EXPECTED_SERIALIZATION, protoToString(chain.serializeToProtobuf()));
        assertEquals(key1, chain.findKeyFromPubHash(key1.getPubKeyHash()));
        assertEquals(key2, chain.findKeyFromPubHash(key2.getPubKeyHash()));
        assertEquals(key3, chain.findKeyFromPubHash(key3.getPubKeyHash()));
        assertEquals(key4, chain.getKey(KeyChain.KeyPurpose.CHANGE));
        key1.signHash(Sha256Hash.ZERO_HASH);
        key2.signHash(Sha256Hash.ZERO_HASH);
        key3.signHash(Sha256Hash.ZERO_HASH);
        key4.signHash(Sha256Hash.ZERO_HASH);
        assertEquals(oldLookaheadSize, chain.getLookaheadSize());
    }

    @Test
    public void serializeUnencryptedBIP44() throws UnreadableWalletException {
        bip44chain.maybeLookAhead();
        IDeterministicKey key1 = bip44chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = bip44chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key3 = bip44chain.getKey(KeyChain.KeyPurpose.CHANGE);
        List<Protos.Key> keys = bip44chain.serializeToProtobuf();
        // 1 mnemonic/seed, 1 master key, 1 account key, 2 internal keys, 3 derived, 20 lookahead and 5 lookahead
        // threshold.
        int numItems = 3 // mnemonic/seed
                + 1 // master key
                + 1 // account key
                + 2 // ext/int parent keys
                + (bip44chain.getLookaheadSize() + bip44chain.getLookaheadThreshold()) * 2 // lookahead zone on each chain
        ;
        assertEquals(numItems, keys.size());

        // Get another key that will be lost during round-tripping, to ensure we can derive it again.
        IDeterministicKey key4 = bip44chain.getKey(KeyChain.KeyPurpose.CHANGE);

        final String EXPECTED_SERIALIZATION = checkSerialization(keys, "deterministic-wallet-bip44-serialization.txt");

        // Round trip the data back and forth to check it is preserved.
        int oldLookaheadSize = bip44chain.getLookaheadSize();
        bip44chain = AnyDeterministicKeyChain.fromProtobuf(keys, null, bip44chain.getKeyFactory(), false).get(0);
        assertEquals(BIP44_ACCOUNT_ONE_PATH, bip44chain.getAccountPath());
        assertEquals(EXPECTED_SERIALIZATION, protoToString(bip44chain.serializeToProtobuf()));
        assertEquals(key1, bip44chain.findKeyFromPubHash(key1.getPubKeyHash()));
        assertEquals(key2, bip44chain.findKeyFromPubHash(key2.getPubKeyHash()));
        assertEquals(key3, bip44chain.findKeyFromPubHash(key3.getPubKeyHash()));
        assertEquals(key4, bip44chain.getKey(KeyChain.KeyPurpose.CHANGE));
        key1.signHash(Sha256Hash.ZERO_HASH);
        key2.signHash(Sha256Hash.ZERO_HASH);
        key3.signHash(Sha256Hash.ZERO_HASH);
        key4.signHash(Sha256Hash.ZERO_HASH);
        assertEquals(oldLookaheadSize, bip44chain.getLookaheadSize());
    }

    @Test(expected = IllegalStateException.class)
    public void notEncrypted() {
        chain.toDecrypted("fail");
    }

    @Test(expected = IllegalStateException.class)
    public void encryptTwice() {
        chain = chain.toEncrypted("once");
        chain = chain.toEncrypted("twice");
    }

    private void checkEncryptedKeyChain(AnyDeterministicKeyChain encChain, IDeterministicKey key1) {
        // Check we can look keys up and extend the chain without the AES key being provided.
        IDeterministicKey encKey1 = encChain.findKeyFromPubKey(key1.getPubKey());
        IDeterministicKey encKey2 = encChain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        assertFalse(key1.isEncrypted());
        assertTrue(encKey1.isEncrypted());
        assertEquals(encKey1.getPubKeyObject(), key1.getPubKeyObject());
        final KeyParameter aesKey = checkNotNull(encChain.getKeyCrypter()).deriveKey("open secret");
        encKey1.signHash(Sha256Hash.ZERO_HASH, aesKey);
        encKey2.signHash(Sha256Hash.ZERO_HASH, aesKey);
        assertTrue(encChain.checkAESKey(aesKey));
        assertFalse(encChain.checkPassword("access denied"));
        assertTrue(encChain.checkPassword("open secret"));
    }

    @Test
    public void encryption() throws UnreadableWalletException {
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        AnyDeterministicKeyChain encChain = chain.toEncrypted("open secret");
        IDeterministicKey encKey1 = encChain.findKeyFromPubKey(key1.getPubKey());
        checkEncryptedKeyChain(encChain, key1);

        // Round-trip to ensure de/serialization works and that we can store two chains and they both deserialize.
        List<Protos.Key> serialized = encChain.serializeToProtobuf();
        List<Protos.Key> doubled = Lists.newArrayListWithExpectedSize(serialized.size() * 2);
        doubled.addAll(serialized);
        doubled.addAll(serialized);
        final List<AnyDeterministicKeyChain> chains = AnyDeterministicKeyChain.fromProtobuf(doubled, encChain.getKeyCrypter(), chain.getKeyFactory(), false);
        assertEquals(2, chains.size());
        encChain = chains.get(0);
        checkEncryptedKeyChain(encChain, chain.findKeyFromPubKey(key1.getPubKey()));
        encChain = chains.get(1);
        checkEncryptedKeyChain(encChain, chain.findKeyFromPubKey(key1.getPubKey()));

        IDeterministicKey encKey2 = encChain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        // Decrypt and check the keys match.
        AnyDeterministicKeyChain decChain = encChain.toDecrypted("open secret");
        IDeterministicKey decKey1 = decChain.findKeyFromPubHash(encKey1.getPubKeyHash());
        IDeterministicKey decKey2 = decChain.findKeyFromPubHash(encKey2.getPubKeyHash());
        assertEquals(decKey1.getPubKeyObject(), encKey1.getPubKeyObject());
        assertEquals(decKey2.getPubKeyObject(), encKey2.getPubKeyObject());
        assertFalse(decKey1.isEncrypted());
        assertFalse(decKey2.isEncrypted());
        assertNotEquals(encKey1.getParent(), decKey1.getParent());   // parts of a different hierarchy
        // Check we can once again derive keys from the decrypted chain.
        decChain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).signHash(Sha256Hash.ZERO_HASH);
        decChain.getKey(KeyChain.KeyPurpose.CHANGE).signHash(Sha256Hash.ZERO_HASH);
    }

    @Test
    public void watchingChain() throws UnreadableWalletException {
        Utils.setMockClock();
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key3 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        IDeterministicKey key4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);

        IDeterministicKey watchingKey = chain.getWatchingKey();
        final String pub58 = watchingKey.serializePubB58(MAINNET);
        assertEquals("xpub69KR9epSNBM59KLuasxMU5CyKytMJjBP5HEZ5p8YoGUCpM6cM9hqxB9DDPCpUUtqmw5duTckvPfwpoWGQUFPmRLpxs5jYiTf2u6xRMcdhDf", pub58);
        watchingKey = chain.getKeyFactory().deserializeB58(null, pub58, MAINNET);
        watchingKey.setCreationTimeSeconds(100000);
        chain = AnyDeterministicKeyChain.builder().watch(watchingKey).outputScriptType(chain.getOutputScriptType())
                .build();
        assertEquals(100000, chain.getEarliestKeyCreationTime());
        chain.setLookaheadSize(10);
        chain.maybeLookAhead();

        assertEquals(key1.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        assertEquals(key2.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        final IDeterministicKey key = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key3.getPubKeyObject(), key.getPubKeyObject());
        try {
            // Can't sign with a key from a watching chain.
            key.signHash(Sha256Hash.ZERO_HASH);
            fail();
        } catch (IKey.MissingPrivateKeyException e) {
            // Ignored.
        }
        // Test we can serialize and deserialize a watching chain OK.
        List<Protos.Key> serialization = chain.serializeToProtobuf();
        checkSerialization(serialization, "watching-wallet-serialization.txt");
        chain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, chain.getKeyFactory(), false).get(0);
        assertEquals(AnyDeterministicKeyChain.ACCOUNT_ZERO_PATH, chain.getAccountPath());
        final IDeterministicKey rekey4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key4.getPubKeyObject(), rekey4.getPubKeyObject());
    }

    @Test
    public void watchingChainArbitraryPath() throws UnreadableWalletException {
        Utils.setMockClock();
        IDeterministicKey key1 = bip44chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = bip44chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key3 = bip44chain.getKey(KeyChain.KeyPurpose.CHANGE);
        IDeterministicKey key4 = bip44chain.getKey(KeyChain.KeyPurpose.CHANGE);

        IDeterministicKey watchingKey = bip44chain.getWatchingKey();
        watchingKey = watchingKey.dropPrivateBytes().dropParent();
        watchingKey.setCreationTimeSeconds(100000);
        chain = AnyDeterministicKeyChain.builder().watch(watchingKey).outputScriptType(bip44chain.getOutputScriptType())
                .build();
        assertEquals(100000, chain.getEarliestKeyCreationTime());
        chain.setLookaheadSize(10);
        chain.maybeLookAhead();

        assertEquals(key1.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        assertEquals(key2.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        final IDeterministicKey key = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key3.getPubKeyObject(), key.getPubKeyObject());
        try {
            // Can't signHash with a key from a watching chain.
            key.signHash(Sha256Hash.ZERO_HASH);
            fail();
        } catch (IKey.MissingPrivateKeyException e) {
            // Ignored.
        }
        // Test we can serialize and deserialize a watching chain OK.
        List<Protos.Key> serialization = chain.serializeToProtobuf();
        checkSerialization(serialization, "watching-wallet-arbitrary-path-serialization.txt");
        chain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, chain.getKeyFactory(), false).get(0);
        assertEquals(BIP44_ACCOUNT_ONE_PATH, chain.getAccountPath());
        final IDeterministicKey rekey4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key4.getPubKeyObject(), rekey4.getPubKeyObject());
    }

    @Test
    public void watchingChainAccountOne() throws UnreadableWalletException {
        Utils.setMockClock();
        final ImmutableList<ChildNumber> accountOne = ImmutableList.of(ChildNumber.ONE);
        AnyDeterministicKeyChain chain1 = AnyDeterministicKeyChain.builder().accountPath(accountOne)
                .seed(chain.getSeed()).build();
        IDeterministicKey key1 = chain1.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = chain1.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key3 = chain1.getKey(KeyChain.KeyPurpose.CHANGE);
        IDeterministicKey key4 = chain1.getKey(KeyChain.KeyPurpose.CHANGE);

        IDeterministicKey watchingKey = chain1.getWatchingKey();
        final String pub58 = watchingKey.serializePubB58(MAINNET);
        assertEquals("xpub69KR9epJ2Wp6ywiv4Xu5WfBUpX4GLu6D5NUMd4oUkCFoZoRNyk3ZCxfKPDkkGvCPa16dPgEdY63qoyLqEa5TQQy1nmfSmgWcagRzimyV7uA", pub58);
        watchingKey = chain1.getKeyFactory().deserializeB58(null, pub58, MAINNET);
        watchingKey.setCreationTimeSeconds(100000);
        chain = AnyDeterministicKeyChain.builder().watch(watchingKey).outputScriptType(chain1.getOutputScriptType())
                .build();
        assertEquals(accountOne, chain.getAccountPath());
        assertEquals(100000, chain.getEarliestKeyCreationTime());
        chain.setLookaheadSize(10);
        chain.maybeLookAhead();

        assertEquals(key1.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        assertEquals(key2.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        final IDeterministicKey key = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key3.getPubKeyObject(), key.getPubKeyObject());
        try {
            // Can't signHash with a key from a watching chain.
            key.signHash(Sha256Hash.ZERO_HASH);
            fail();
        } catch (IKey.MissingPrivateKeyException e) {
            // Ignored.
        }
        // Test we can serialize and deserialize a watching chain OK.
        List<Protos.Key> serialization = chain.serializeToProtobuf();
        checkSerialization(serialization, "watching-wallet-serialization-account-one.txt");
        chain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, chain.getKeyFactory(), false).get(0);
        assertEquals(accountOne, chain.getAccountPath());
        final IDeterministicKey rekey4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key4.getPubKeyObject(), rekey4.getPubKeyObject());
    }

    @Test
    public void spendingChain() throws UnreadableWalletException {
        Utils.setMockClock();
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key3 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        IDeterministicKey key4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);

        NetworkParameters params = MainNetParams.get();
        IDeterministicKey watchingKey = chain.getWatchingKey();
        final String prv58 = watchingKey.serializePrivB58(params);
        assertEquals("xprv9vL4k9HYXonmvqGSUrRM6wGEmx3ruGTXi4JxHRiwEvwDwYmTocPbQNpjN89gpqPrFofmfvALwgnNFBCH2grse1YDf8ERAwgdvbjRtoMfsbV", prv58);
        watchingKey = chain.getKeyFactory().deserializeB58(null, prv58, params);
        watchingKey.setCreationTimeSeconds(100000);
        chain = AnyDeterministicKeyChain.builder().spend(watchingKey).outputScriptType(chain.getOutputScriptType())
                .build();
        assertEquals(100000, chain.getEarliestKeyCreationTime());
        chain.setLookaheadSize(10);
        chain.maybeLookAhead();

        assertEquals(key1.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        assertEquals(key2.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        final IDeterministicKey key = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key3.getPubKeyObject(), key.getPubKeyObject());
        try {
            // We can signHash with a key from a spending chain.
            key.signHash(Sha256Hash.ZERO_HASH);
        } catch (IKey.MissingPrivateKeyException e) {
            fail();
        }
        // Test we can serialize and deserialize a watching chain OK.
        List<Protos.Key> serialization = chain.serializeToProtobuf();
        checkSerialization(serialization, "spending-wallet-serialization.txt");
        chain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, chain.getKeyFactory(), false).get(0);
        assertEquals(AnyDeterministicKeyChain.ACCOUNT_ZERO_PATH, chain.getAccountPath());
        final IDeterministicKey rekey4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(key4.getPubKeyObject(), rekey4.getPubKeyObject());
    }

    @Test
    public void spendingChainAccountTwo() throws UnreadableWalletException {
        Utils.setMockClock();
        final long secs = 1389353062L;
        final ImmutableList<ChildNumber> accountTwo = ImmutableList.of(new ChildNumber(2, true));
        chain = AnyDeterministicKeyChain.builder().accountPath(accountTwo).entropy(ENTROPY, secs).build();
        IDeterministicKey firstReceiveKey = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey secondReceiveKey = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey firstChangeKey = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        IDeterministicKey secondChangeKey = chain.getKey(KeyChain.KeyPurpose.CHANGE);

        NetworkParameters params = MainNetParams.get();
        IDeterministicKey watchingKey = chain.getWatchingKey();

        final String prv58 = watchingKey.serializePrivB58(params);
        assertEquals("xprv9vL4k9HYXonmzR7UC1ngJ3hTjxkmjLLUo3RexSfUGSWcACHzghWBLJAwW6xzs59XeFizQxFQWtscoTfrF9PSXrUgAtBgr13Nuojax8xTBRz", prv58);
        watchingKey = watchingKey.getKeyFactory().deserializeB58(null, prv58, params);
        watchingKey.setCreationTimeSeconds(secs);
        chain = AnyDeterministicKeyChain.builder().spend(watchingKey).outputScriptType(chain.getOutputScriptType())
                .build();
        assertEquals(accountTwo, chain.getAccountPath());
        assertEquals(secs, chain.getEarliestKeyCreationTime());
        chain.setLookaheadSize(10);
        chain.maybeLookAhead();

        verifySpendableKeyChain(firstReceiveKey, secondReceiveKey, firstChangeKey, secondChangeKey, chain, "spending-wallet-account-two-serialization.txt");
    }

    @Test
    public void masterKeyAccount() throws UnreadableWalletException {
        Utils.setMockClock();
        long secs = 1389353062L;
        IDeterministicKey firstReceiveKey = bip44chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey secondReceiveKey = bip44chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey firstChangeKey = bip44chain.getKey(KeyChain.KeyPurpose.CHANGE);
        IDeterministicKey secondChangeKey = bip44chain.getKey(KeyChain.KeyPurpose.CHANGE);

        NetworkParameters params = MainNetParams.get();
        IDeterministicKey watchingKey = bip44chain.getWatchingKey(); //m/44'/1'/0'
        IDeterministicKey coinLevelKey = bip44chain.getWatchingKey().getParent(); //m/44'/1'

        //Simulate Wallet.fromSpendingKeyB58(PARAMS, prv58, secs)
        final String prv58 = watchingKey.serializePrivB58(params);
        assertEquals("xprv9yYQhynAmWWuz62PScx5Q2frBET2F1raaXna5A2E9Lj8XWgmKBL7S98Yand8F736j9UCTNWQeiB4yL5pLZP7JDY2tY8eszGQkiKDwBkezeS", prv58);
        watchingKey = watchingKey.getKeyFactory().deserializeB58(null, prv58, params);
        watchingKey.setCreationTimeSeconds(secs);
        AnyDeterministicKeyChain fromPrivBase58Chain = AnyDeterministicKeyChain.builder().spend(watchingKey)
                .outputScriptType(bip44chain.getOutputScriptType()).keyFactory(bip44chain.getKeyFactory()).build();
        assertEquals(secs, fromPrivBase58Chain.getEarliestKeyCreationTime());
        fromPrivBase58Chain.setLookaheadSize(10);
        fromPrivBase58Chain.maybeLookAhead();

        verifySpendableKeyChain(firstReceiveKey, secondReceiveKey, firstChangeKey, secondChangeKey, fromPrivBase58Chain, "spending-wallet-from-bip44-serialization.txt");

        //Simulate Wallet.fromMasterKey(params, coinLevelKey, 0)
        IDeterministicKey accountKey = coinLevelKey.deriveChildKey(new ChildNumber(0, true));
        accountKey = accountKey.dropParent();
        accountKey.setCreationTimeSeconds(watchingKey.getCreationTimeSeconds());
        AnyKeyChainGroup group = AnyKeyChainGroup.builder(params, accountKey.getKeyFactory()).addChain(AnyDeterministicKeyChain.builder().spend(accountKey)
                .outputScriptType(bip44chain.getOutputScriptType()).keyFactory(accountKey.getKeyFactory()).build()).build();
        AnyDeterministicKeyChain fromMasterKeyChain = group.getActiveKeyChain();
        assertEquals(BIP44_ACCOUNT_ONE_PATH, fromMasterKeyChain.getAccountPath());
        assertEquals(secs, fromMasterKeyChain.getEarliestKeyCreationTime());
        fromMasterKeyChain.setLookaheadSize(10);
        fromMasterKeyChain.maybeLookAhead();

        verifySpendableKeyChain(firstReceiveKey, secondReceiveKey, firstChangeKey, secondChangeKey, fromMasterKeyChain, "spending-wallet-from-bip44-serialization-two.txt");
    }

    /**
     * verifySpendableKeyChain
     *
     * firstReceiveKey and secondReceiveKey are the first two keys of the external chain of a known key chain
     * firstChangeKey and secondChangeKey are the first two keys of the internal chain of a known key chain
     * keyChain is a AnyDeterministicKeyChain loaded from a serialized format or derived in some other way from
     * the known key chain
     *
     * This method verifies that known keys match a newly created keyChain and that keyChain's protobuf
     * matches the serializationFile.
     */
    private void verifySpendableKeyChain(IDeterministicKey firstReceiveKey, IDeterministicKey secondReceiveKey,
                                         IDeterministicKey firstChangeKey, IDeterministicKey secondChangeKey,
                                         AnyDeterministicKeyChain keyChain, String serializationFile) throws UnreadableWalletException {

        //verify that the keys are the same as the keyChain
        assertEquals(firstReceiveKey.getPubKeyObject(), keyChain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        assertEquals(secondReceiveKey.getPubKeyObject(), keyChain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        final IDeterministicKey key = keyChain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(firstChangeKey.getPubKeyObject(), key.getPubKeyObject());

        try {
            key.signHash(Sha256Hash.ZERO_HASH);
        } catch (IKey.MissingPrivateKeyException e) {
            // We can signHash with a key from a spending chain.
            fail();
        }

        // Test we can serialize and deserialize the chain OK
        List<Protos.Key> serialization = keyChain.serializeToProtobuf();
        checkSerialization(serialization, serializationFile);

        // Check that the second change key matches after loading from the serialization, serializing and deserializing
        long secs = keyChain.getEarliestKeyCreationTime();
        keyChain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, keyChain.getKeyFactory(), false).get(0);
        serialization = keyChain.serializeToProtobuf();
        checkSerialization(serialization, serializationFile);
        assertEquals(secs, keyChain.getEarliestKeyCreationTime());
        final IDeterministicKey nextChangeKey = keyChain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(secondChangeKey.getPubKeyObject(), nextChangeKey.getPubKeyObject());
    }

    @Test(expected = IllegalStateException.class)
    public void watchingCannotEncrypt() throws Exception {
        final IDeterministicKey accountKey = chain.getKeyByPath(AnyDeterministicKeyChain.ACCOUNT_ZERO_PATH);
        chain = AnyDeterministicKeyChain.builder().watch(accountKey.dropPrivateBytes().dropParent())
                .outputScriptType(chain.getOutputScriptType()).build();
        assertEquals(AnyDeterministicKeyChain.ACCOUNT_ZERO_PATH, chain.getAccountPath());
        chain = chain.toEncrypted("this doesn't make any sense");
    }

    @Test
    public void xpubsMatchAfterEncryption() {
        NetworkParameters params = MainNetParams.get();

        String expectedBip32xpub = "xpub69KR9epSNBM59KLuasxMU5CyKytMJjBP5HEZ5p8YoGUCpM6cM9hqxB9DDPCpUUtqmw5duTckvPfwpoWGQUFPmRLpxs5jYiTf2u6xRMcdhDf";
        String expectedBip44xpub = "xpub6CXm7VK4bt5DCa6rYeV5mAcajGHWeUaRwkiAsYRqhgG7QK1urieMywT2S49CncqBbBXQvf8wLmeayPtmjsYN1o5uMz9esQeNWqarhxthYiR";

        // Check the BIP32 keychain
        IDeterministicKey watchingKey = chain.getWatchingKey(); //m/0'
        final String pub58 = watchingKey.serializePubB58(params);
        assertEquals(expectedBip32xpub, pub58);
        // Make sure that the xpub remains the same after encrypting the keychain
        AnyDeterministicKeyChain chainEncrypted = chain.toEncrypted("hello");
        final String pub58encrypted = chainEncrypted.getWatchingKey().serializePubB58(params);
        assertEquals(expectedBip32xpub, pub58encrypted);

        // Check the BIP44 keychain
        IDeterministicKey watchingKeyBip44 = bip44chain.getWatchingKey(); //m/44'/1'/0'
        final String pub58Bip44 = watchingKeyBip44.serializePubB58(params);
        assertEquals(expectedBip44xpub, pub58Bip44);
        // Make sure that the xpub remains the same after encrypting the keychain
        AnyDeterministicKeyChain bip44chainEncrypted = bip44chain.toEncrypted("hello");
        final String pub58encryptedBip44 = bip44chainEncrypted.getWatchingKey().serializePubB58(params);
        assertEquals(expectedBip44xpub, pub58encryptedBip44);
    }

    @Test
    public void bloom1() {
        IDeterministicKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        int numEntries =
                (((chain.getLookaheadSize() + chain.getLookaheadThreshold()) * 2)   // * 2 because of internal/external
              + chain.numLeafKeysIssued()
              + 4  // one root key + one account key + two chain keys (internal/external)
                ) * 2;  // because the filter contains keys and key hashes.
        assertEquals(numEntries, chain.numBloomFilterEntries());
        BloomFilter filter = chain.getFilter(numEntries, 0.001, 1);
        assertTrue(filter.contains(key1.getPubKey()));
        assertTrue(filter.contains(key1.getPubKeyHash()));
        assertTrue(filter.contains(key2.getPubKey()));
        assertTrue(filter.contains(key2.getPubKeyHash()));

        // The lookahead zone is tested in bloom2 and via KeyChainGroupTest.bloom
    }

    @Test
    public void bloom2() throws Exception {
        // Verify that if when we watch a key, the filter contains at least 100 keys.
        IDeterministicKey[] keys = new IDeterministicKey[100];
        for (int i = 0; i < keys.length; i++)
            keys[i] = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        chain = AnyDeterministicKeyChain.builder().watch(chain.getWatchingKey().dropPrivateBytes().dropParent())
                .outputScriptType(chain.getOutputScriptType()).build();
        int e = chain.numBloomFilterEntries();
        BloomFilter filter = chain.getFilter(e, 0.001, 1);
        for (IDeterministicKey key : keys)
            assertTrue("key " + key, filter.contains(key.getPubKeyHash()));
    }

    @Test
    public void blsCheckPathDerivationTest() {
        // TODO: should this be legacy or basic?
        BLSScheme.setLegacyDefault(true);
        HDPath operatorPath = DerivationPathFactory.get(UNITTEST).masternodeOperatorDerivationPath();

        AnyDeterministicKeyChain blsChain = AuthenticationKeyChain.builder()
                .seed(chain.getSeed())
                .keyFactory(BLSKeyFactory.get())
                .accountPath(operatorPath)
                .build();

        ExtendedPrivateKey extendedPrivateKey = ExtendedPrivateKey.fromSeed(chain.getSeed().getSeedBytes())
                .privateChild(new ChildNumber(9, true).getI())
                .privateChild(ChildNumber.ONE_HARDENED.getI())
                .privateChild(new ChildNumber(3, true).getI())
                .privateChild(new ChildNumber(3, true).getI());

        ExtendedPrivateKey blsExtPrivKeyCursor = null;
        // check each element along the path
        for (int i = 0; i < operatorPath.size(); ++i) {
            IDeterministicKey key = blsChain.getKeyByPath(operatorPath.subList(0, i));
            if (i == 0) {
                blsExtPrivKeyCursor = ExtendedPrivateKey.fromSeed(chain.getSeed().getSeedBytes());
            } else {
                int child = operatorPath.get(i-1).getI();
                blsExtPrivKeyCursor = blsExtPrivKeyCursor.privateChild(child);
            }
            assertArrayEquals(blsExtPrivKeyCursor.getChainCode().serialize(), key.getChainCode());
            assertEquals(blsExtPrivKeyCursor.getParentFingerprint(), key.getParentFingerprint());
            assertArrayEquals(blsExtPrivKeyCursor.getPrivateKey().serialize(), key.getPrivKeyBytes());
            assertArrayEquals(blsExtPrivKeyCursor.getPublicKey().serialize(true), key.getPubKey());
            assertEquals(0x1L, blsExtPrivKeyCursor.getVersion());
        }

        // check the extended private key for the full path
        assertArrayEquals(extendedPrivateKey.getChainCode().serialize(), blsChain.getWatchingKey().getChainCode());
        assertEquals(extendedPrivateKey.getParentFingerprint(), blsChain.getWatchingKey().getParentFingerprint());
        assertArrayEquals(extendedPrivateKey.getPrivateKey().serialize(), blsChain.getWatchingKey().getPrivKeyBytes());
        assertArrayEquals(extendedPrivateKey.getPublicKey().serialize(true), blsChain.getWatchingKey().getPubKey());
        assertEquals(0x1L, extendedPrivateKey.getVersion());

        // check the extended public key for the full path
        ExtendedPublicKey extendedPublicKey = extendedPrivateKey.getExtendedPublicKey();
        IDeterministicKey blsChainPublicKey = blsChain.getWatchingKey().dropPrivateBytes();
        assertArrayEquals(extendedPublicKey.getChainCode().serialize(), blsChainPublicKey.getChainCode());
        assertEquals(extendedPublicKey.getParentFingerprint(), blsChainPublicKey.getParentFingerprint());
        assertArrayEquals(extendedPublicKey.getPublicKey().serialize(true), blsChainPublicKey.getPubKey());
        assertEquals(0x1L, extendedPublicKey.getVersion());
    }

    private String protoToString(List<Protos.Key> keys) {
        StringBuilder sb = new StringBuilder();
        for (Protos.Key key : keys) {
            sb.append(key.toString());
            sb.append("\n");
        }
        int hashTag = sb.indexOf("# org");
        while(hashTag != -1) {
            int endOfLine = sb.indexOf("\n", hashTag);
            sb.replace(hashTag, endOfLine+1, "\n");
            hashTag = sb.indexOf("# org");
        }
        return sb.toString().trim();
    }

    private String checkSerialization(List<Protos.Key> keys, String filename) {
        try {
            String sb = protoToString(keys);
            List<String> lines = Resources.readLines(getClass().getResource(filename), StandardCharsets.UTF_8);
            String expected = Joiner.on('\n').join(lines);
            assertEquals(expected, sb);
            return expected;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
