/*
 * Copyright 2014 Mike Hearn
 * Copyright 2019 Andreas Schildbach
 * Copyright
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

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.Script.ScriptType;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractKeyChainGroupExtensionTest {
    // Number of initial keys in this tests HD wallet, including interior keys.
    private static final int INITIAL_KEYS = 4;
    private static final int LOOKAHEAD_SIZE = 5;
    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final Context CONTEXT = new Context(MAINNET);
    private static final String XPUB = "xpub68KFnj3bqUx1s7mHejLDBPywCAKdJEu1b49uniEEn2WSbHmZ7xbLqFTjJbtx1LUcAt1DwhoqWHmo2s5WMJp6wi38CiF2hYD49qVViKVvAoi";
    private static final byte[] ENTROPY = Sha256Hash.hash("don't use a string seed like this in real life".getBytes());
    private static final KeyCrypterScrypt KEY_CRYPTER = new KeyCrypterScrypt(2);
    private static final KeyParameter AES_KEY = KEY_CRYPTER.deriveKey("password");
    private AnyKeyChainGroup group;
    private IDeterministicKey watchingAccountKey;
    
    private final KeyFactory EC_KEYFACTORY = ECKeyFactory.get();

    static class KeyChainGroupGroupExtension extends AbstractKeyChainGroupExtension {

        AnyKeyChainGroup keyChainGroup;

        protected KeyChainGroupGroupExtension(AnyKeyChainGroup keyChainGroup) {
            super(new Wallet(CONTEXT));
            this.keyChainGroup = keyChainGroup;
        }

        @Override
        public AnyKeyChainGroup getKeyChainGroup() {
            return keyChainGroup;
        }

        @Override
        public boolean supportsBloomFilters() {
            return true;
        }

        @Override
        public boolean supportsEncryption() {
            return true;
        }

        @Override
        public boolean hasSpendableKeys() {
            return true;
        }

        /**
         * Returns a Java package/class style name used to disambiguate this extension from others.
         */
        @Override
        public String getWalletExtensionID() {
            return "org.dashj.keychaingroup";
        }

        /**
         * If this returns true, the mandatory flag is set when the wallet is serialized and attempts to load it without
         * the extension being in the wallet will throw an exception. This method should not change its result during
         * the objects lifetime.
         */
        @Override
        public boolean isWalletExtensionMandatory() {
            return false;
        }

        /**
         * Returns bytes that will be saved in the wallet.
         */
        @Override
        public byte[] serializeWalletExtension() {
            return new byte[0];
        }

        /**
         * Loads the contents of this object from the wallet.
         *
         * @param containingWallet
         * @param data
         */
        @Override
        public void deserializeWalletExtension(Wallet containingWallet, byte[] data) throws Exception {

        }
    }
    private KeyChainGroupGroupExtension groupExt;

    @Before
    public void setup() {
        BriefLogFormatter.init();
        Utils.setMockClock();
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).lookaheadSize(LOOKAHEAD_SIZE).fromRandom(ScriptType.P2PKH)
                .build();
        groupExt = new KeyChainGroupGroupExtension(group);
        groupExt.getActiveKeyChain();  // Force create a chain.

        watchingAccountKey = EC_KEYFACTORY.deserializeB58(null, XPUB, MAINNET);
    }

    @Test
    public void createDeterministic_P2PKH() {
        AnyKeyChainGroup kcg = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).fromRandom(ScriptType.P2PKH).build();
        KeyChainGroupGroupExtension kcgExt = new KeyChainGroupGroupExtension(kcg);
        // check default
        Address address = kcgExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(ScriptType.P2PKH, address.getOutputScriptType());
    }

//    private KeyChainGroupWalletExtension createMarriedKeyChainGroup() {
//        AnyDeterministicKeyChain chain = createMarriedKeyChain();
//        AnyKeyChainGroup group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).lookaheadSize(LOOKAHEAD_SIZE).addChain(chain).build();
//        KeyChainGroupWalletExtension groupExt = new KeyChainGroupWalletExtension(group);
//        group.getActiveKeyChain();
//        return groupExt;
//    }
//
//    private MarriedKeyChain createMarriedKeyChain() {
//        byte[] entropy = Sha256Hash.hash("don't use a seed like this in real life".getBytes());
//        DeterministicSeed seed = new DeterministicSeed(entropy, "", MnemonicCode.BIP39_STANDARDISATION_TIME_SECS);
//        MarriedKeyChain chain = MarriedKeyChain.builder()
//                .seed(seed)
//                .followingKeys(watchingAccountKey)
//                .threshold(2).build();
//        return chain;
    //}

    @Test
    public void freshCurrentKeys() throws Exception {
        int numKeys = ((groupExt.getLookaheadSize() + groupExt.getLookaheadThreshold()) * 2)   // * 2 because of internal/external
                + 1  // keys issued
                + groupExt.getActiveKeyChain().getAccountPath().size() + 2  /* account key + int/ext parent keys */;
        assertEquals(numKeys, groupExt.numKeys());
        assertEquals(2 * numKeys, groupExt.getBloomFilterElementCount());
        IKey r1 = groupExt.currentKey(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(numKeys, group.numKeys());
        assertEquals(2 * numKeys, groupExt.getBloomFilterElementCount());

        IKey i1 = EC_KEYFACTORY.newKey();
        group.importKeys(i1);
        numKeys++;
        assertEquals(numKeys, groupExt.numKeys());
        assertEquals(2 * numKeys, groupExt.getBloomFilterElementCount());

        IKey r2 = groupExt.currentKey(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(r1, r2);
        IKey c1 = groupExt.currentKey(KeyPurpose.CHANGE);
        assertNotEquals(r1, c1);
        IKey r3 = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        assertNotEquals(r1, r3);
        IKey c2 = groupExt.freshKey(KeyPurpose.CHANGE);
        assertNotEquals(r3, c2);
        // Current key has not moved and will not under marked as used.
        IKey r4 = groupExt.currentKey(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(r2, r4);
        IKey c3 = groupExt.currentKey(KeyPurpose.CHANGE);
        assertEquals(c1, c3);
        // Mark as used. Current key is now different.
        groupExt.markPubKeyAsUsed(r4.getPubKey());
        IKey r5 = groupExt.currentKey(KeyPurpose.RECEIVE_FUNDS);
        assertNotEquals(r4, r5);
    }

//    @Test
//    public void freshCurrentKeysForMarriedKeychain() throws Exception {
//        groupExt = createMarriedKeyChainGroup();
//
//        try {
//            groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
//            fail();
//        } catch (UnsupportedOperationException e) {
//        }
//
//        try {
//            groupExt.currentKey(KeyPurpose.RECEIVE_FUNDS);
//            fail();
//        } catch (UnsupportedOperationException e) {
//        }
//    }

    @Test
    public void imports() throws Exception {
        IKey key1 = EC_KEYFACTORY.newKey();
        int numKeys = groupExt.numKeys();
        assertFalse(groupExt.removeImportedKey(key1));
        assertEquals(1, groupExt.importKeys(ImmutableList.of(key1)));
        assertEquals(numKeys + 1, groupExt.numKeys());   // Lookahead is triggered by requesting a key, so none yet.
        group.removeImportedKey(key1);
        assertEquals(numKeys, groupExt.numKeys());
    }


    @Test
    public void findKey() throws Exception {
        IKey a = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        IKey b = groupExt.freshKey(KeyPurpose.CHANGE);
        IKey c = EC_KEYFACTORY.newKey();
        IKey d = EC_KEYFACTORY.newKey();   // Not imported.
        group.importKeys(c);
        assertTrue(groupExt.hasKey(a));
        assertTrue(groupExt.hasKey(b));
        assertTrue(group.hasKey(c));
        assertFalse(groupExt.hasKey(d));
        IKey result = groupExt.findKeyFromPubKey(a.getPubKey());
        assertEquals(a, result);
        result = groupExt.findKeyFromPubKey(b.getPubKey());
        assertEquals(b, result);
        result = groupExt.findKeyFromPubKeyHash(a.getPubKeyHash(), null);
        assertEquals(a, result);
        result = groupExt.findKeyFromPubKeyHash(b.getPubKeyHash(), null);
        assertEquals(b, result);
        result = group.findKeyFromPubKey(c.getPubKey());
        assertEquals(c, result);
        result = group.findKeyFromPubKeyHash(c.getPubKeyHash(), null);
        assertEquals(c, result);
        assertNull(groupExt.findKeyFromPubKey(d.getPubKey()));
        assertNull(groupExt.findKeyFromPubKeyHash(d.getPubKeyHash(), null));
    }

//    @Test
//    public void currentP2SHAddress() throws Exception {
//        groupExt = createMarriedKeyChainGroup();
//        Address a1 = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertEquals(ScriptType.P2SH, a1.getOutputScriptType());
//        Address a2 = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertEquals(a1, a2);
//        Address a3 = groupExt.currentAddress(KeyPurpose.CHANGE);
//        assertNotEquals(a2, a3);
//    }
//
//    @Test
//    public void freshAddress() throws Exception {
//        groupExt = createMarriedKeyChainGroup();
//        Address a1 = groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS);
//        Address a2 = groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertEquals(ScriptType.P2SH, a1.getOutputScriptType());
//        assertNotEquals(a1, a2);
//        groupExt.getBloomFilterElementCount();
//        assertEquals(((groupExt.getLookaheadSize() + groupExt.getLookaheadThreshold()) * 2)   // * 2 because of internal/external
//                + (2 - groupExt.getLookaheadThreshold())  // keys issued
//                + groupExt.getActiveKeyChain().getAccountPath().size() + 3  /* master, account, int, ext */, groupExt.numKeys());
//
//        Address a3 = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertEquals(a2, a3);
//    }

//    @Test
//    public void findRedeemData() throws Exception {
//        groupExt = createMarriedKeyChainGroup();
//
//        // test script hash that we don't have
//        assertNull(groupExt.findRedeemDataFromScriptHash(EC_KEYFACTORY.newKey().getPubKey()));
//
//        // test our script hash
//        Address address = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
//        IRedeemData redeemData = groupExt.findRedeemDataFromScriptHash(address.getHash());
//        assertNotNull(redeemData);
//        assertNotNull(redeemData.redeemScript);
//        assertEquals(2, redeemData.keys.size());
//    }

    // Check encryption with and without a basic keychain.

    @Test
    public void encryptionWithoutImported() throws Exception {
        encryption(false);
    }

    public void encryption(boolean withImported) throws Exception {
        Utils.rollMockClock(0);
        long now = Utils.currentTimeSeconds();
        IKey a = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(now, group.getEarliestKeyCreationTime());
        Utils.rollMockClock(-86400);
        long yesterday = Utils.currentTimeSeconds();
        IKey b = EC_KEYFACTORY.newKey();

        assertFalse(groupExt.isEncrypted());
        try {
            group.checkPassword("foo");   // Cannot check password of an unencrypted group.
            fail();
        } catch (IllegalStateException e) {
        }
        if (withImported) {
            assertEquals(now, group.getEarliestKeyCreationTime());
            group.importKeys(b);
            assertEquals(yesterday, group.getEarliestKeyCreationTime());
        }
        groupExt.encrypt(KEY_CRYPTER, AES_KEY);
        assertTrue(groupExt.isEncrypted());
        assertTrue(groupExt.checkPassword("password"));
        assertFalse(groupExt.checkPassword("wrong password"));
        final IKey ea = groupExt.findKeyFromPubKey(a.getPubKey());
        assertTrue(checkNotNull(ea).isEncrypted());
        if (withImported) {
            assertTrue(checkNotNull(group.findKeyFromPubKey(b.getPubKey())).isEncrypted());
            assertEquals(yesterday, group.getEarliestKeyCreationTime());
        } else {
            assertEquals(now, group.getEarliestKeyCreationTime());
        }
        try {
            ea.signHash(Sha256Hash.ZERO_HASH);
            fail();
        } catch (IKey.KeyIsEncryptedException e) {
            // Ignored.
        }
        if (withImported) {
            IKey c = EC_KEYFACTORY.newKey();
            try {
                groupExt.importKeys(c);
                fail();
            } catch (KeyCrypterException e) {
            }
            groupExt.importKeysAndEncrypt(ImmutableList.of(c), AES_KEY);
            IKey ec = groupExt.findKeyFromPubKey(c.getPubKey());
            try {
                groupExt.importKeysAndEncrypt(ImmutableList.of(ec), AES_KEY);
                fail();
            } catch (IllegalArgumentException e) {
            }
        }

        try {
            groupExt.decrypt(KEY_CRYPTER.deriveKey("WRONG PASSWORD"));
            fail();
        } catch (KeyCrypterException e) {
        }

        group.decrypt(AES_KEY);
        assertFalse(group.isEncrypted());
        assertFalse(checkNotNull(groupExt.findKeyFromPubKey(a.getPubKey())).isEncrypted());
        assertEquals(now, groupExt.getEarliestKeyCreationTime());
    }

    @Test
    public void encryptionWhilstEmpty() throws Exception {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).lookaheadSize(5).fromRandom(ScriptType.P2PKH).build();
        group.encrypt(KEY_CRYPTER, AES_KEY);
        assertTrue(group.freshKey(KeyPurpose.RECEIVE_FUNDS).isEncrypted());
        final IKey key = group.currentKey(KeyPurpose.RECEIVE_FUNDS);
        group.decrypt(AES_KEY);
        assertFalse(checkNotNull(group.findKeyFromPubKey(key.getPubKey())).isEncrypted());
    }

    @Test
    public void bloom() throws Exception {
        IKey key1 = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        IKey key2 = EC_KEYFACTORY.newKey();
        BloomFilter filter = groupExt.getBloomFilter(groupExt.getBloomFilterElementCount(), 0.001, (long)(Math.random() * Long.MAX_VALUE));
        assertTrue(filter.contains(key1.getPubKeyHash()));
        assertTrue(filter.contains(key1.getPubKey()));
        assertFalse(filter.contains(key2.getPubKey()));
        // Check that the filter contains the lookahead buffer and threshold zone.
        for (int i = 0; i < LOOKAHEAD_SIZE + group.getLookaheadThreshold(); i++) {
            IKey k = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
            assertTrue(filter.contains(k.getPubKeyHash()));
        }
        // We ran ahead of the lookahead buffer.
        assertFalse(filter.contains(groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS).getPubKey()));
        groupExt.importKeys(key2);
        filter = group.getBloomFilter(groupExt.getBloomFilterElementCount(), 0.001, (long) (Math.random() * Long.MAX_VALUE));
        assertTrue(filter.contains(key1.getPubKeyHash()));
        assertTrue(filter.contains(key1.getPubKey()));
        assertTrue(filter.contains(key2.getPubKey()));
    }

//    @Test
//    public void findRedeemScriptFromPubHash() throws Exception {
//        groupExt = createMarriedKeyChainGroup();
//        Address address = groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertTrue(groupExt.findRedeemDataFromScriptHash(address.getHash()) != null);
//        groupExt.getBloomFilterElementCount();
//        KeyChainGroupWalletExtension group2Ext = createMarriedKeyChainGroup();
//        group2Ext.freshAddress(KeyPurpose.RECEIVE_FUNDS);
//        group2Ext.getBloomFilterElementCount();  // Force lookahead.
//        // test address from lookahead zone and lookahead threshold zone
//        for (int i = 0; i < groupExt.getLookaheadSize() + groupExt.getLookaheadThreshold(); i++) {
//            address = groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS);
//            assertTrue(group2Ext.findRedeemDataFromScriptHash(address.getHash()) != null);
//        }
//        assertFalse(group2Ext.findRedeemDataFromScriptHash(groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS).getHash()) != null);
//    }

//    @Test
//    public void bloomFilterForMarriedChains() throws Exception {
//        groupExt = createMarriedKeyChainGroup();
//        int bufferSize = groupExt.getLookaheadSize() + groupExt.getLookaheadThreshold();
//        int expected = bufferSize * 2 /* chains */ * 2 /* elements */;
//        assertEquals(expected, groupExt.getBloomFilterElementCount());
//        Address address1 = groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertEquals(expected, groupExt.getBloomFilterElementCount());
//        BloomFilter filter = groupExt.getBloomFilter(expected + 2, 0.001, (long)(Math.random() * Long.MAX_VALUE));
//        assertTrue(filter.contains(address1.getHash()));
//
//        Address address2 = groupExt.freshAddress(KeyPurpose.CHANGE);
//        assertTrue(filter.contains(address2.getHash()));
//
//        // Check that the filter contains the lookahead buffer.
//        for (int i = 0; i < bufferSize - 1 /* issued address */; i++) {
//            Address address = groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS);
//            assertTrue("key " + i, filter.contains(address.getHash()));
//        }
//        // We ran ahead of the lookahead buffer.
//        assertFalse(filter.contains(groupExt.freshAddress(KeyPurpose.RECEIVE_FUNDS).getHash()));
//    }

    @Test
    public void earliestKeyTime() throws Exception {
        long now = Utils.currentTimeSeconds();   // mock
        long yesterday = now - 86400;
        assertEquals(now, groupExt.getEarliestKeyCreationTime());
        Utils.rollMockClock(10000);
        groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        Utils.rollMockClock(10000);
        groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        // Check that all keys are assumed to be created at the same instant the seed is.
        assertEquals(now, groupExt.getEarliestKeyCreationTime());
        IKey key = EC_KEYFACTORY.newKey();
        key.setCreationTimeSeconds(yesterday);
        group.importKeys(key);
        assertEquals(yesterday, group.getEarliestKeyCreationTime());
    }

    @Test
    public void events() throws Exception {
        // Check that events are registered with the right chains and that if a chain is added, it gets the event
        // listeners attached properly even post-hoc.
        final AtomicReference<IKey> ran = new AtomicReference<>(null);
        final KeyChainEventListener listener = new KeyChainEventListener() {
            @Override
            public void onKeysAdded(List<IKey> keys) {
                ran.set(keys.get(0));
            }
        };
        groupExt.addEventListener(listener, Threading.SAME_THREAD);
        IKey key = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(key, ran.getAndSet(null));
        IKey key2 = EC_KEYFACTORY.newKey();
        group.importKeys(key2);
        assertEquals(key2, ran.getAndSet(null));
        groupExt.removeEventListener(listener);
        IKey key3 = EC_KEYFACTORY.newKey();
        group.importKeys(key3);
        assertNull(ran.get());
    }

    @Test
    public void serialization() throws Exception {
        int initialKeys = INITIAL_KEYS + group.getActiveKeyChain().getAccountPath().size() - 1;
        assertEquals(initialKeys + 1 /* for the seed */, group.serializeToProtobuf().size());
        group = AnyKeyChainGroup.fromProtobufUnencrypted(MAINNET, group.serializeToProtobuf(), EC_KEYFACTORY);
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key1 = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = groupExt.freshKey(KeyPurpose.CHANGE);
        groupExt.getBloomFilterElementCount();
        List<Protos.Key> protoKeys1 = groupExt.serializeToProtobuf();
        assertEquals(initialKeys + ((LOOKAHEAD_SIZE + 1) * 2) + 1 /* for the seed */ + 1, protoKeys1.size());
        group.importKeys(EC_KEYFACTORY.newKey());
        List<Protos.Key> protoKeys2 = groupExt.serializeToProtobuf();
        groupExt.importKeys(EC_KEYFACTORY.newKey());
        assertEquals(initialKeys + ((LOOKAHEAD_SIZE + 1) * 2) + 1 /* for the seed */ + 2, protoKeys2.size());

        group = AnyKeyChainGroup.fromProtobufUnencrypted(MAINNET, protoKeys1, EC_KEYFACTORY);
        groupExt = new KeyChainGroupGroupExtension(group);
        assertEquals(initialKeys + ((LOOKAHEAD_SIZE + 1)  * 2)  + 1 /* for the seed */ + 1, protoKeys1.size());
        assertTrue(groupExt.hasKey(key1));
        assertTrue(groupExt.hasKey(key2));
        assertEquals(key2, groupExt.currentKey(KeyPurpose.CHANGE));
        assertEquals(key1, groupExt.currentKey(KeyPurpose.RECEIVE_FUNDS));
        group = AnyKeyChainGroup.fromProtobufUnencrypted(MAINNET, protoKeys2, EC_KEYFACTORY);
        assertEquals(initialKeys + ((LOOKAHEAD_SIZE + 1) * 2) + 1 /* for the seed */ + 2, protoKeys2.size());
        assertTrue(groupExt.hasKey(key1));
        assertTrue(groupExt.hasKey(key2));

        groupExt.encrypt(KEY_CRYPTER, AES_KEY);
        List<Protos.Key> protoKeys3 = groupExt.serializeToProtobuf();
        group = AnyKeyChainGroup.fromProtobufEncrypted(MAINNET, protoKeys3, KEY_CRYPTER, EC_KEYFACTORY);
        groupExt = new KeyChainGroupGroupExtension(group);
        assertTrue(groupExt.isEncrypted());
        assertTrue(groupExt.checkPassword("password"));
        groupExt.decrypt(AES_KEY);

        // No need for extensive contents testing here, as that's done in the keychain class tests.
    }

    @Test
    public void serializeWatching() throws Exception {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).lookaheadSize(LOOKAHEAD_SIZE).addChain(AnyDeterministicKeyChain.builder()
                .watch(watchingAccountKey).build()).build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);

        groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        groupExt.freshKey(KeyPurpose.CHANGE);
        groupExt.getBloomFilterElementCount();  // Force lookahead.
        List<Protos.Key> protoKeys1 = groupExt.serializeToProtobuf();
        assertEquals(3 + (groupExt.getLookaheadSize() + groupExt.getLookaheadThreshold() + 1) * 2, protoKeys1.size());
        group = AnyKeyChainGroup.fromProtobufUnencrypted(MAINNET, protoKeys1, EC_KEYFACTORY);
        groupExt = new KeyChainGroupGroupExtension(group);
        assertEquals(3 + (groupExt.getLookaheadSize() + groupExt.getLookaheadThreshold() + 1) * 2, group.serializeToProtobuf().size());
    }

//    @Test
//    public void serializeMarried() throws Exception {
//        groupExt = createMarriedKeyChainGroup();
//        Address address1 = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertTrue(groupExt.isMarried());
//        assertEquals(2, groupExt.getActiveKeyChain().getSigsRequiredToSpend());
//
//        List<Protos.Key> protoKeys = groupExt.serializeToProtobuf();
//        AnyKeyChainGroup group2 = AnyKeyChainGroup.fromProtobufUnencrypted(MAINNET, protoKeys, EC_KEYFACTORY);
//        KeyChainGroupWalletExtension group2Ext = new KeyChainGroupWalletExtension(group2);
//
//        assertTrue(group2Ext.isMarried());
//        assertEquals(2, group2Ext.getActiveKeyChain().getSigsRequiredToSpend());
//        Address address2 = group2Ext.currentAddress(KeyPurpose.RECEIVE_FUNDS);
//        assertEquals(address1, address2);
//    }

//    @Test
//    public void addFollowingAccounts() throws Exception {
//        assertFalse(groupExt.isMarried());
//        groupExt.addAndActivateHDChain(createMarriedKeyChain());
//        assertTrue(groupExt.isMarried());
//    }

    @Test
    public void constructFromSeed() throws Exception {
        IKey key1 = groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);
        final DeterministicSeed seed = checkNotNull(groupExt.getActiveKeyChain().getSeed());
        AnyKeyChainGroup group2 = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).lookaheadSize(5)
                .addChain(AnyDeterministicKeyChain.builder().seed(seed).build())
                .build();
        IKey key2 = group2.freshKey(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(key1, key2);
    }

    @Test
    public void addAndActivateHDChain_freshCurrentAddress() {
        DeterministicSeed seed = new DeterministicSeed(ENTROPY, "", 0);
        AnyDeterministicKeyChain chain1 = AnyDeterministicKeyChain.builder().seed(seed)
                .accountPath(AnyDeterministicKeyChain.ACCOUNT_ZERO_PATH).outputScriptType(ScriptType.P2PKH).build();
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).addChain(chain1).build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        assertEquals("XvmHuzosHbV6aq78Dcj78Uj6sDTPstxrqU", groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS).toString());

        final AnyDeterministicKeyChain chain2 = AnyDeterministicKeyChain.builder().seed(seed)
                .accountPath(AnyDeterministicKeyChain.ACCOUNT_ONE_PATH).outputScriptType(ScriptType.P2PKH).build();
        groupExt.addAndActivateHDChain(chain2);
        assertEquals("Xt2dZYtRagQZKX6gjimBESxWYmpCQWTgR3", groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS).toString());

        final AnyDeterministicKeyChain chain3 = AnyDeterministicKeyChain.builder().seed(seed)
                .accountPath(AnyDeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH).outputScriptType(ScriptType.P2PKH)
                .build();
        groupExt.addAndActivateHDChain(chain3);
        assertEquals("XmaJ4TMovJGiRDzH3ZV6qJF3oB4k1E38TZ",
                groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS).toString());
    }

    @Test(expected = DeterministicUpgradeRequiredException.class)
    public void deterministicUpgradeRequired() throws Exception {
        // Check that if we try to use HD features in a KCG that only has random keys, we get an exception.
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        groupExt.importKeys(EC_KEYFACTORY.newKey(), EC_KEYFACTORY.newKey());
        assertTrue(groupExt.isDeterministicUpgradeRequired(ScriptType.P2PKH, 0));
        assertTrue(groupExt.isDeterministicUpgradeRequired(ScriptType.P2WPKH, 0));
        groupExt.freshKey(KeyPurpose.RECEIVE_FUNDS);   // throws
    }

    @Test
    public void deterministicUpgradeUnencrypted() throws Exception {
        // Check that a group that contains only random keys has its HD chain created using the private key bytes of
        // the oldest random key, so upgrading the same wallet twice gives the same outcome.
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).lookaheadSize(LOOKAHEAD_SIZE).build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        IKey key1 = EC_KEYFACTORY.newKey();
        Utils.rollMockClock(86400);
        IKey key2 = EC_KEYFACTORY.newKey();
        groupExt.importKeys(key2, key1);

        List<Protos.Key> protobufs = groupExt.serializeToProtobuf();
        groupExt.upgradeToDeterministic(Script.ScriptType.P2PKH, KeyChainGroupStructure.DEFAULT, 0, null);
        assertFalse(groupExt.isEncrypted());
        assertFalse(groupExt.isDeterministicUpgradeRequired(Script.ScriptType.P2PKH, 0));
        assertTrue(groupExt.isDeterministicUpgradeRequired(Script.ScriptType.P2WPKH, 0));
        IDeterministicKey dkey1 = groupExt.freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicSeed seed1 = groupExt.getActiveKeyChain().getSeed();
        assertNotNull(seed1);

        group = AnyKeyChainGroup.fromProtobufUnencrypted(MAINNET, protobufs, EC_KEYFACTORY);
        groupExt = new KeyChainGroupGroupExtension(group);
        groupExt.upgradeToDeterministic(Script.ScriptType.P2PKH, KeyChainGroupStructure.DEFAULT, 0, null);  // Should give same result as last time.
        assertFalse(groupExt.isEncrypted());
        assertFalse(groupExt.isDeterministicUpgradeRequired(Script.ScriptType.P2PKH, 0));
        assertTrue(groupExt.isDeterministicUpgradeRequired(Script.ScriptType.P2WPKH, 0));
        IDeterministicKey dkey2 = groupExt.freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicSeed seed2 = groupExt.getActiveKeyChain().getSeed();
        assertEquals(seed1, seed2);
        assertEquals(dkey1, dkey2);

        // Check we used the right (oldest) key despite backwards import order.
        byte[] truncatedBytes = Arrays.copyOfRange(key1.getSecretBytes(), 0, 16);
        assertArrayEquals(seed1.getEntropyBytes(), truncatedBytes);
    }

    @Test
    public void deterministicUpgradeRotating() throws Exception {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).lookaheadSize(LOOKAHEAD_SIZE).build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        long now = Utils.currentTimeSeconds();
        IKey key1 = EC_KEYFACTORY.newKey();
        Utils.rollMockClock(86400);
        IKey key2 = EC_KEYFACTORY.newKey();
        Utils.rollMockClock(86400);
        IKey key3 = EC_KEYFACTORY.newKey();
        groupExt.importKeys(key2, key1, key3);
        groupExt.upgradeToDeterministic(Script.ScriptType.P2PKH, KeyChainGroupStructure.DEFAULT, now + 10, null);
        DeterministicSeed seed = groupExt.getActiveKeyChain().getSeed();
        assertNotNull(seed);
        // Check we used the right key: oldest non rotating.
        byte[] truncatedBytes = Arrays.copyOfRange(key2.getSecretBytes(), 0, 16);
        assertArrayEquals(seed.getEntropyBytes(), truncatedBytes);
    }

    @Test
    public void deterministicUpgradeEncrypted() throws Exception {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        final IKey key = EC_KEYFACTORY.newKey();
        groupExt.importKeys(key);
        assertTrue(group.isDeterministicUpgradeRequired(Script.ScriptType.P2PKH, 0));
        groupExt.encrypt(KEY_CRYPTER, AES_KEY);
        assertTrue(groupExt.isDeterministicUpgradeRequired(Script.ScriptType.P2PKH, 0));
        try {
            groupExt.upgradeToDeterministic(Script.ScriptType.P2PKH, KeyChainGroupStructure.DEFAULT, 0, null);
            fail();
        } catch (DeterministicUpgradeRequiresPassword e) {
            // Expected.
        }
        groupExt.upgradeToDeterministic(Script.ScriptType.P2PKH, KeyChainGroupStructure.DEFAULT, 0, AES_KEY);
        assertTrue(groupExt.isEncrypted());
        assertFalse(groupExt.isDeterministicUpgradeRequired(Script.ScriptType.P2PKH, 0));
        assertTrue(groupExt.isDeterministicUpgradeRequired(Script.ScriptType.P2WPKH, 0));
        final DeterministicSeed deterministicSeed = groupExt.getActiveKeyChain().getSeed();
        assertNotNull(deterministicSeed);
        assertTrue(deterministicSeed.isEncrypted());
        byte[] entropy = checkNotNull(groupExt.getActiveKeyChain().toDecrypted(AES_KEY).getSeed()).getEntropyBytes();
        // Check we used the right key: oldest non rotating.
        byte[] truncatedBytes = Arrays.copyOfRange(key.getSecretBytes(), 0, 16);
        assertArrayEquals(entropy, truncatedBytes);
    }

    @Test
    public void markAsUsed() throws Exception {
        Address addr1 = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
        Address addr2 = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
        assertEquals(addr1, addr2);
        groupExt.markPubKeyHashAsUsed(addr1.getHash());
        Address addr3 = groupExt.currentAddress(KeyPurpose.RECEIVE_FUNDS);
        assertNotEquals(addr2, addr3);
    }

    @Test
    public void isNotWatching() {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).fromRandom(ScriptType.P2PKH).build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        final IKey key = EC_KEYFACTORY.fromPrivate(BigInteger.TEN);
        groupExt.importKeys(key);
        assertFalse(groupExt.isWatching());
    }

    @Test
    public void isWatching() {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY)
                .addChain(AnyDeterministicKeyChain.builder().watch(EC_KEYFACTORY.deserializeB58(
                        "xpub69bjfJ91ikC5ghsqsVDHNq2dRGaV2HHVx7Y9LXi27LN9BWWAXPTQr4u8U3wAtap8bLdHdkqPpAcZmhMS5SnrMQC4ccaoBccFhh315P4UYzo",
                        MAINNET)).outputScriptType(ScriptType.P2PKH).build())
                .build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        final IKey watchingKey = EC_KEYFACTORY.fromPublicOnly(EC_KEYFACTORY.newKey());
        groupExt.importKeys(watchingKey);
        assertTrue(groupExt.isWatching());
    }

    @Test(expected = IllegalStateException.class)
    public void isWatchingNoKeys() {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY).build();
        group.isWatching();
    }

    @Test(expected = IllegalStateException.class)
    public void isWatchingMixedKeys() {
        group = AnyKeyChainGroup.builder(MAINNET, EC_KEYFACTORY)
                .addChain(AnyDeterministicKeyChain.builder().watch(EC_KEYFACTORY.deserializeB58(
                        "xpub69bjfJ91ikC5ghsqsVDHNq2dRGaV2HHVx7Y9LXi27LN9BWWAXPTQr4u8U3wAtap8bLdHdkqPpAcZmhMS5SnrMQC4ccaoBccFhh315P4UYzo",
                        MAINNET)).outputScriptType(ScriptType.P2PKH).build())
                .build();
        KeyChainGroupGroupExtension groupExt = new KeyChainGroupGroupExtension(group);
        final IKey key = EC_KEYFACTORY.fromPrivate(BigInteger.TEN);
        groupExt.importKeys(key);
        groupExt.isWatching();
    }

    @Test
    public void activeKeyChains() {
        assertEquals(1, groupExt.getActiveKeyChains(0).size());
    }
}
