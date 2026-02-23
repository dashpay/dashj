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
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyType;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.crypto.factory.Ed25519KeyFactory;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.listeners.AbstractKeyChainEventListener;
import org.bouncycastle.crypto.params.KeyParameter;
import org.dashj.bls.BLSJniLibrary;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests several of the AnyDeterministicKeyChain of different key types
 */
@RunWith(Parameterized.class)
public class AllDeterministicKeyChainTest {
    static {
        BLSJniLibrary.init();
    }
    private KeyType keyType;
    private List<ChildNumber> accountPath;
    private KeyFactory keyFactory;
    private boolean hardenedOnly;
    private String derivedAddressOne;
    private String derivedAddressTwo;
    private String derivedAddressThree;
    private String watchingXpub;
    private String spendingXprv;



    private AnyDeterministicKeyChain chain;

    private static final byte[] ENTROPY = Sha256Hash.hash("don't use a string seed like this in real life".getBytes());
    private static final NetworkParameters UNITTEST = UnitTestParams.get();
    private static final NetworkParameters MAINNET = MainNetParams.get();

    public AllDeterministicKeyChainTest(KeyType keyType, List<ChildNumber> accountPath, KeyFactory keyFactory,
                                        boolean hardenedOnly,
                                        String derivedAddressOne, String derivedAddressTwo, String derivedAddressThree,
                                        String watchingXpub, String spendingXprv) {
        this.keyType = keyType;
        this.accountPath = accountPath;
        this.keyFactory = keyFactory;
        this.hardenedOnly = hardenedOnly;
        this.derivedAddressOne = derivedAddressOne;
        this.derivedAddressTwo = derivedAddressTwo;
        this.derivedAddressThree = derivedAddressThree;
        this.watchingXpub = watchingXpub;
        this.spendingXprv = spendingXprv;
    }

    @Parameterized.Parameters(name = "Deterministic Key Chain {index} (type={0}, accountPath={1}, keyFactory={2})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                        KeyType.BLS,
                        DeterministicKeyChain.PROVIDER_OPERATOR_PATH,
                        BLSKeyFactory.get(),
                        false,
                        "yX6buKrz1wLSAY7BXdtg9FdG1f95X4GZhi",
                        "yeX9LUwxbXXMCmTKFPCzTppUpFhawDgaek",
                        "yN3fc7m2vpuKq7KKqoxYgsrqavg5ivFvMk",
                        "7tqAshzJAHbxH9X3n8ZUqVoqBy3SAh8WZ4Z5R6YYZoFLiMQhDt7Jih32X9kgXVNb3GfK2tqxCxkn5wbqqg1mXTJex7QHETspCCHvehLc2dNqe6st3hLFTf7gLs9wSMRhm2SQ",
                        "DeaWiU2QoeFHqtY4mMKB4kxZErdCvY8VTdDr8UBJ9mTHrn1zD2Qp1uJnKReqxws4VTiZkVrSrF1g2czTAFSsguPZR4RYQZwXZ38mR5P4Re8XtV"
                },
                {
                        KeyType.ECDSA,
                        DeterministicKeyChain.ACCOUNT_ZERO_PATH,
                        ECKeyFactory.get(),
                        false,
                        "ygPtvwtJj99Ava2fnU3WAW9T9VwmPuyTV1",
                        "yT5yAz7rX677oKD6ETsf3pUxYVzDoRXWeQ",
                        "yWiFqq8aRcSKEwuPhMRxAnvJ5QQw4F7cuz",
                        "xpub69KR9epSNBM59KLuasxMU5CyKytMJjBP5HEZ5p8YoGUCpM6cM9hqxB9DDPCpUUtqmw5duTckvPfwpoWGQUFPmRLpxs5jYiTf2u6xRMcdhDf",
                        "xprv9vL4k9HYXonmvqGSUrRM6wGEmx3ruGTXi4JxHRiwEvwDwYmTocPbQNpjN89gpqPrFofmfvALwgnNFBCH2grse1YDf8ERAwgdvbjRtoMfsbV"
                },
                {
                        KeyType.ECDSA,
                        DeterministicKeyChain.BLOCKCHAIN_USER_PATH,
                        ECKeyFactory.get(),
                        true,
                        "yfinjDg242cvzvwTgYBTgcrZaoH7KqWi4G",
                        "yarpR6L7Mq9ga7MrZ3FiQccNHAmiT3vuzv",
                        "yP6kqHv1bCcYTfDphQpJ7XD83oRKJXuMj9",
                        "xpub6Eky4sfWLHTdhbDiPbKbvFdLRoG4Rx9PLKzF1ENJqVJy1LnvdGTZ2WpStJwiYMAvvuWNyDz7ni24vuEQNYr5SSsoArxdpTktayCxhdfaruD",
                        "xprvA1mcfN8cVuuLV79FHZnbZ7gbsmRa2VRXy74eCqxhH9mz8YTn5j9JUiVy32eY6KnDwDn2u94ztj6jdZc1SjWdTaQ4tior7UGbAaXPr5vzkXB"
                },
                {
                        KeyType.EdDSA,
                        DerivationPathFactory.get(UNITTEST).masternodeHoldingsDerivationPath(),
                        Ed25519KeyFactory.get(),
                        true,
                        "yhakQ64EW7M5DyDCFUA2W2xTxUq5xaTqkb",
                        "yiY5rsU7xvUfqg26nP3qtb7Gn7EBJ7HPYf",
                        "ydEc6yebzFmbWMTorB36W6aRaoUNHoZZBt",
                        "xpub6ENeUVdAJsaMfbePETuJVgoUYfG3suZFUv7ydR4j6REK9PETouLbWFyL16Wswab9UPMZfNJDePbKe2CbidWi1MoPTLXrfSiGMbo2GHnbBnz",
                        "xprvA1PJ4z6GUW24T7Zv8SNJ8YrjzdRZUSqQ7hCNq2f7Y5hLGauKGN2LxTer9ujXRcR38DVrYSAZ7QgKtN8dqwgsFEJpVksh5dMxZocn3wWcD8g"
                }
        });
    }

    @Before
    public void setup() {
        BriefLogFormatter.init();
        // You should use a random seed instead. The secs constant comes from the unit test file, so we can compare
        // serialized data properly.
        long secs = 1389353062L;
        chain = AnyDeterministicKeyChain.builder().entropy(ENTROPY, secs)
                .accountPath(accountPath)
                .outputScriptType(Script.ScriptType.P2PKH)
                .keyFactory(keyFactory)
                .hardenedKeysOnly(hardenedOnly)
                .build();
        chain.setLookaheadSize(10);
    }

    @Test
    public void derive() throws Exception {
        IKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        assertFalse(key1.isPubKeyOnly());
        IKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        assertFalse(key2.isPubKeyOnly());

        final Address address = Address.fromBase58(UNITTEST, derivedAddressOne);
        assertEquals(address, Address.fromKey(UNITTEST, key1));
        assertEquals(derivedAddressTwo, Address.fromKey(UNITTEST, key2).toString());
        assertEquals(key1, chain.findKeyFromPubHash(address.getHash()));
        assertEquals(key2, chain.findKeyFromPubKey(key2.getPubKey()));

        key1.signHash(Sha256Hash.ZERO_HASH);
        assertFalse(key1.isPubKeyOnly());

        IKey key3 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertFalse(key3.isPubKeyOnly());
        assertEquals(derivedAddressThree, Address.fromKey(UNITTEST, key3).toString());
        key3.signHash(Sha256Hash.ZERO_HASH);
        assertFalse(key3.isPubKeyOnly());
    }

    @Test
    public void getKeys() throws Exception {
        chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        chain.getKey(KeyChain.KeyPurpose.CHANGE);
        chain.maybeLookAhead();
        // TODO: hardened chains always return 28, fix this
        assertEquals(hardenedOnly ? 28 : 2, chain.getKeys(false, false).size());
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
              + accountPath.size()  // account key
              + 2  // ext/int parent keys
              + (chain.getLookaheadSize() + chain.getLookaheadThreshold()) * 2   // lookahead zone on each chain
        ;
        assertEquals(numItems, keys.size());

        // Get another key that will be lost during round-tripping, to ensure we can derive it again.
        IDeterministicKey key4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);


        // Round trip the data back and forth to check it is preserved.
        int oldLookaheadSize = chain.getLookaheadSize();
        chain = AnyDeterministicKeyChain.fromProtobuf(keys, null, chain.getKeyFactory(), hardenedOnly).get(0);
        assertEquals(accountPath, chain.getAccountPath());
        assertEquals(key1, chain.findKeyFromPubHash(key1.getPubKeyHash()));
        assertEquals(key2, chain.findKeyFromPubHash(key2.getPubKeyHash()));
        assertEquals(key3, chain.findKeyFromPubHash(key3.getPubKeyHash()));
        assertEquals(key4, chain.getKey(KeyChain.KeyPurpose.CHANGE));
        key1.signHash(Sha256Hash.ZERO_HASH);
        key2.signHash(Sha256Hash.ZERO_HASH);
        key3.signHash(Sha256Hash.ZERO_HASH);
        key4.signHash(Sha256Hash.ZERO_HASH);
        assertEquals(hardenedOnly ? 0 : oldLookaheadSize, chain.getLookaheadSize());
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
        if (hardenedOnly)
            return;
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
        if (hardenedOnly)
            return;
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        AnyDeterministicKeyChain encChain = chain.toEncrypted("open secret");
        IDeterministicKey encKey1 = encChain.findKeyFromPubKey(key1.getPubKey());
        checkEncryptedKeyChain(encChain, key1);

        // Round-trip to ensure de/serialization works and that we can store two chains and they both deserialize.
        List<Protos.Key> serialized = encChain.serializeToProtobuf();
        List<Protos.Key> doubled = Lists.newArrayListWithExpectedSize(serialized.size() * 2);
        doubled.addAll(serialized);
        doubled.addAll(serialized);
        final List<AnyDeterministicKeyChain> chains = AnyDeterministicKeyChain.fromProtobuf(doubled, encChain.getKeyCrypter(), chain.getKeyFactory(), hardenedOnly);
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
        if (hardenedOnly)
            return;
        Utils.setMockClock();
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key3 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        IDeterministicKey key4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);

        IDeterministicKey watchingKey = chain.getWatchingKey();
        final String pub58 = watchingKey.serializePubB58(MAINNET);
        assertEquals(watchingXpub, pub58);
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
        chain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, chain.getKeyFactory(), hardenedOnly).get(0);
        assertEquals(Lists.newArrayList(accountPath.get(accountPath.size() - 1)), chain.getAccountPath());
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
        assertEquals(spendingXprv, prv58);
        watchingKey = chain.getKeyFactory().deserializeB58(null, prv58, params);
        watchingKey.setCreationTimeSeconds(100000);
        chain = AnyDeterministicKeyChain.builder().spend(watchingKey).outputScriptType(chain.getOutputScriptType())
                .hardenedKeysOnly(hardenedOnly).build();
        assertEquals(100000, chain.getEarliestKeyCreationTime());
        chain.setLookaheadSize(10);
        chain.maybeLookAhead();

        if (key1.getKeyFactory().getKeyType() == KeyType.EdDSA) {
            assertArrayEquals(key1.getPubKey(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKey());
            assertArrayEquals(key2.getPubKey(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKey());
        } else {
            assertEquals(key1.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
            assertEquals(key2.getPubKeyObject(), chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS).getPubKeyObject());
        }
        final IDeterministicKey key = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        if (key1.getKeyFactory().getKeyType() == KeyType.EdDSA) {
            assertArrayEquals(key3.getPubKey(), key.getPubKey());
        } else {
            assertEquals(key3.getPubKeyObject(), key.getPubKeyObject());
        }
        try {
            // We can signHash with a key from a spending chain.
            key.signHash(Sha256Hash.ZERO_HASH);
        } catch (IKey.MissingPrivateKeyException e) {
            fail();
        }
        // Test we can serialize and deserialize a watching chain OK.
        List<Protos.Key> serialization = chain.serializeToProtobuf();
        chain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, chain.getKeyFactory(), hardenedOnly).get(0);
        assertEquals(Lists.newArrayList(accountPath.get(accountPath.size()-1)), chain.getAccountPath());
        final IDeterministicKey rekey4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        if (key1.getKeyFactory().getKeyType() == KeyType.EdDSA) {
            assertArrayEquals(key4.getPubKey(), rekey4.getPubKey());
        } else {
            assertEquals(key4.getPubKeyObject(), rekey4.getPubKeyObject());
        }
    }

    @Test
    public void spendingChainAccountTwo() throws UnreadableWalletException {
        Utils.setMockClock();
        final long secs = 1389353062L;
        final ImmutableList<ChildNumber> accountTwo = ImmutableList.of(new ChildNumber(2, true));
        chain = AnyDeterministicKeyChain.builder().accountPath(accountTwo).entropy(ENTROPY, secs).hardenedKeysOnly(hardenedOnly).build();
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
                .hardenedKeysOnly(hardenedOnly).build();
        assertEquals(accountTwo, chain.getAccountPath());
        assertEquals(secs, chain.getEarliestKeyCreationTime());
        chain.setLookaheadSize(10);
        chain.maybeLookAhead();

        verifySpendableKeyChain(firstReceiveKey, secondReceiveKey, firstChangeKey, secondChangeKey, chain, "spending-wallet-account-two-serialization.txt");
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
        //checkSerialization(serialization, serializationFile);

        // Check that the second change key matches after loading from the serialization, serializing and deserializing
        long secs = keyChain.getEarliestKeyCreationTime();
        keyChain = AnyDeterministicKeyChain.fromProtobuf(serialization, null, keyChain.getKeyFactory(), hardenedOnly).get(0);
        serialization = keyChain.serializeToProtobuf();
        //checkSerialization(serialization, serializationFile);
        assertEquals(secs, keyChain.getEarliestKeyCreationTime());
        final IDeterministicKey nextChangeKey = keyChain.getKey(KeyChain.KeyPurpose.CHANGE);
        assertEquals(secondChangeKey.getPubKeyObject(), nextChangeKey.getPubKeyObject());
    }

    @Test(expected = IllegalStateException.class)
    public void watchingCannotEncrypt() throws Exception {
        final IDeterministicKey accountKey = chain.getKeyByPath(accountPath);
        AnyDeterministicKeyChain.Builder<?> builder = AnyDeterministicKeyChain.builder();
        if (!hardenedOnly)
            builder.watch(chain.getWatchingKey().dropPrivateBytes().dropParent());
        else builder.spend(chain.getWatchingKey().dropParent());
        //chain = AnyDeterministicKeyChain.builder().watch(chain.getWatchingKey().dropPrivateBytes().dropParent())
        chain = builder.outputScriptType(chain.getOutputScriptType()).hardenedKeysOnly(hardenedOnly).build();
//        chain = AnyDeterministicKeyChain.builder().watch(accountKey.dropPrivateBytes().dropParent())
//                .outputScriptType(chain.getOutputScriptType()).hardenedKeysOnly(hardenedOnly).build();
        assertEquals(accountPath, chain.getAccountPath());
        chain = chain.toEncrypted("this doesn't make any sense");
    }

    @Test
    public void xpubsMatchAfterEncryption() {
        NetworkParameters params = MainNetParams.get();

        String expectedXpub = watchingXpub;

        // Check the BIP32 keychain
        IDeterministicKey watchingKey = chain.getWatchingKey(); //m/0'
        final String pub58 = watchingKey.serializePubB58(params);
        assertEquals(expectedXpub, pub58);
        // Make sure that the xpub remains the same after encrypting the keychain
        AnyDeterministicKeyChain chainEncrypted = chain.toEncrypted("hello");
        final String pub58encrypted = chainEncrypted.getWatchingKey().serializePubB58(params);
        assertEquals(expectedXpub, pub58encrypted);
    }

    @Test
    public void bloom1() {
        IDeterministicKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        IDeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        int numEntries =
                (((chain.getLookaheadSize() + chain.getLookaheadThreshold()) * 2)   // * 2 because of internal/external
              + chain.numLeafKeysIssued()
              + 3 + accountPath.size()  // one root key + one account key + two chain keys (internal/external)
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
        AnyDeterministicKeyChain.Builder<?> builder = AnyDeterministicKeyChain.builder();
        if (!hardenedOnly)
            builder.watch(chain.getWatchingKey().dropPrivateBytes().dropParent());
        else builder.spend(chain.getWatchingKey().dropParent());
        // chain = AnyDeterministicKeyChain.builder().watch(chain.getWatchingKey().dropPrivateBytes().dropParent())
        chain = builder.outputScriptType(chain.getOutputScriptType()).hardenedKeysOnly(hardenedOnly).build();
        int e = chain.numBloomFilterEntries();
        BloomFilter filter = chain.getFilter(e, 0.001, 1);
        for (IDeterministicKey key : keys)
            assertTrue("key " + key, filter.contains(key.getPubKeyHash()));
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
