/*
 * Copyright 2019 by Dash Core Group
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
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.ExtendedChildNumber;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.evolution.EvolutionContact;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.BriefLogFormatter;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.wallet.FriendKeyChain.KeyChainType.RECEIVING_CHAIN;
import static org.bitcoinj.wallet.FriendKeyChain.KeyChainType.SENDING_CHAIN;
import static org.junit.Assert.*;

public class FriendKeyChainTest {

    private UnitTestParams PARAMS = UnitTestParams.get();
    private MainNetParams MAINNET = MainNetParams.get();
    private DeterministicKeyChain chain;
    private DeterministicKeyChain bip44chain;
    private final byte[] ENTROPY = Sha256Hash.hash("don't use a string seed like this in real life".getBytes());
    private long secs = 1389353062L;

    @Before
    public void setup() {
        BriefLogFormatter.init();
        // You should use a random seed instead. The secs constant comes from the unit test file, so we can compare
        // serialized data properly.
        chain = DeterministicKeyChain.builder().entropy(ENTROPY, secs).passphrase("").build();
        chain.setLookaheadSize(10);
        assertEquals(secs, checkNotNull(chain.getSeed()).getCreationTimeSeconds());

        bip44chain = DeterministicKeyChain.builder().entropy(ENTROPY, secs).passphrase("")
                .accountPath(ImmutableList.of(new ChildNumber(44, true), new ChildNumber(1, true), ChildNumber.ZERO_HARDENED))
                .build();
        bip44chain.setLookaheadSize(10);
        assertEquals(secs, checkNotNull(bip44chain.getSeed()).getCreationTimeSeconds());
    }

    @Test
    public void deriveMasterKey() throws Exception {

        FriendKeyChain friend = new FriendKeyChain(new DeterministicSeed(ENTROPY, "", secs),
                ImmutableList.of(new ChildNumber(44, true), new ChildNumber(1, true), ChildNumber.ZERO_HARDENED));

        ECKey bip44chainMasterKey = bip44chain.getWatchingKey();
        ECKey friendMasterKey = friend.getWatchingKey();

        assertEquals(bip44chainMasterKey, friendMasterKey);
    }

    @Test
    public void deriveFriendshipKey() {
        Sha256Hash userAhash = Sha256Hash.wrap("c27eb14f698b32e9bb306dba7bbbee831263dcf658abeebb39930460ead117e5");
        Sha256Hash userBhash = Sha256Hash.wrap("ee2052ff075c5ca3c16c3e20e9ac8223834475cc1324ab07889cb24ce6a62793");
        FriendKeyChain friend = new FriendKeyChain(new DeterministicSeed(ENTROPY, "", secs),
                FriendKeyChain.FRIEND_ROOT_PATH, 0, userAhash, userBhash);

        DeterministicKey key = friend.getWatchingKey();

        ImmutableList<ChildNumber> accountPath = ImmutableList.of(ChildNumber.NINE_HARDENED, ChildNumber.FIVE_HARDENED,
                DerivationPathFactory.FEATURE_PURPOSE_DASHPAY, ChildNumber.ZERO_HARDENED,
                new ExtendedChildNumber(userAhash), new ExtendedChildNumber(userBhash));

        assertEquals(accountPath, key.getPath());

        for (ChildNumber i : accountPath)
            System.out.println(i.toString());
    }

    @Test
    public void test256BitDerivation() throws UnreadableWalletException {

        String seedPhrase = "upper renew that grow pelican pave subway relief describe enforce suit hedgehog blossom dose swallow";

        long now = Utils.currentTimeSeconds();
        DeterministicSeed seed = new DeterministicSeed(seedPhrase, null, "", now);

        byte[] bytes = new byte[32];

        Utils.uint32ToByteArrayLE(5, bytes, 0);
        Utils.uint32ToByteArrayLE(12, bytes, 8);
        Utils.uint32ToByteArrayLE(15, bytes, 16);
        Utils.uint32ToByteArrayLE(1337, bytes, 24);

        Sha256Hash hash = Sha256Hash.wrap(bytes);
        assertEquals("05000000000000000c000000000000000f000000000000003905000000000000", hash.toString());

        ChildNumber num = new ExtendedChildNumber(hash);
        ImmutableList<ChildNumber> path = ImmutableList.of(num);

        DeterministicKeyChain keyChain = DeterministicKeyChain.builder().seed(seed).accountPath(path).build();
        DeterministicKey watchingKey = keyChain.getWatchingKey();
        assertEquals("029d469d2a7070d6367afc099be3d0a8d6467ced43228b8ce3d1723f6f4f78cac7", Utils.HEX.encode(watchingKey.getPubKey()));
    }

    @Test
    public void serializeTest() throws UnreadableWalletException {
        final NetworkParameters PARAMS = UnitTestParams.get();

        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
        Wallet wallet = new Wallet(PARAMS);

        Sha256Hash userAhash = Sha256Hash.wrap("c27eb14f698b32e9bb306dba7bbbee831263dcf658abeebb39930460ead117e5");
        Sha256Hash userBhash = Sha256Hash.wrap("ee2052ff075c5ca3c16c3e20e9ac8223834475cc1324ab07889cb24ce6a62793");

        EvolutionContact contact = new EvolutionContact(userAhash, 0, userBhash, 0);

        wallet.addAndActivateHDChain(bip44chain);

        wallet.addReceivingFromFriendKeyChain(bip44chain.getSeed(), bip44chain.getKeyCrypter(),
                0, userAhash, userBhash);

        DeterministicKey currentKey = wallet.receivingFromFriendsGroup.getFriendKeyChain(contact, FriendKeyChain.KeyChainType.RECEIVING_CHAIN).getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        Protos.Wallet protos = new WalletProtobufSerializer().walletToProto(wallet);

        Wallet walletReloaded = new WalletProtobufSerializer().readWallet(PARAMS, null, protos);

        assertTrue(walletReloaded.hasReceivingFriendKeyChains());

        DeterministicKeyChain friendChain = walletReloaded.receivingFromFriendsGroup.getActiveKeyChain();

        DeterministicKey key = friendChain.getWatchingKey();

        DeterministicKey currentKeyAfterDeserialization = walletReloaded.receivingFromFriendsGroup.getFriendKeyChain(contact, FriendKeyChain.KeyChainType.RECEIVING_CHAIN).getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        ImmutableList<ChildNumber> accountPath = ImmutableList.of(ChildNumber.NINE_HARDENED, ChildNumber.ONE_HARDENED,
                DerivationPathFactory.FEATURE_PURPOSE_DASHPAY, ChildNumber.ZERO_HARDENED,
                new ExtendedChildNumber(userAhash), new ExtendedChildNumber(userBhash));

        assertEquals(accountPath, key.getPath());

        assertEquals(wallet.receivingFromFriendsGroup.freshKey(contact, FriendKeyChain.KeyChainType.RECEIVING_CHAIN), currentKeyAfterDeserialization);
        assertEquals(currentKey, walletReloaded.receivingFromFriendsGroup.findKeyFromPubKeyHash(currentKey.getPubKeyHash(), Script.ScriptType.P2PKH));

    }

    @Test
    public void testFriendSendKeyChain() {
        final NetworkParameters PARAMS = UnitTestParams.get();
        Sha256Hash userAhash = Sha256Hash.wrap("a11ce14f698b32e9bb306dba7bbbee831263dcf658abeebb39930460ead117e5");
        Sha256Hash userBhash = Sha256Hash.wrap("b0b052ff075c5ca3c16c3e20e9ac8223834475cc1324ab07889cb24ce6a62793");

        EvolutionContact contact = new EvolutionContact(userAhash, 0, userBhash, 0);

        FriendKeyChain privateChain = new FriendKeyChain(bip44chain.getSeed(),
                FriendKeyChain.FRIEND_ROOT_PATH_TESTNET, 0, userAhash, userBhash);
        FriendKeyChainGroup privateGroup = FriendKeyChainGroup.friendlybuilder(PARAMS).build();
        privateGroup.addAndActivateHDChain(privateChain);
        DeterministicKey privateKey = privateGroup.freshKey(contact, FriendKeyChain.KeyChainType.RECEIVING_CHAIN);

        DeterministicKey privateChainWatchingKey = privateChain.getWatchingKey().dropPrivateBytes().dropParent();

        FriendKeyChain publicChainFromKey = new FriendKeyChain(privateChainWatchingKey);
        FriendKeyChainGroup publicGroupFromKey = FriendKeyChainGroup.friendlybuilder(PARAMS).build();
        publicGroupFromKey.addAndActivateHDChain(publicChainFromKey);
        DeterministicKey publicKey = privateGroup.currentKey(contact, FriendKeyChain.KeyChainType.RECEIVING_CHAIN);

        // this tpub is taken from privateChain.getWatchingKey().serializePub58
        String tpub = "tpubDKVkbAHG8foFoR8iAP2ueWdM6frdNmNXNAoUETNaZCPU6j1iBtjbZTRZmQbbXCcwsgf77KbGcKUo3LyDyuvqkE2zPoBNgBYZSm2YUtWV5KA";
        assertEquals(tpub, privateChain.getWatchingKey().serializePubB58(PARAMS));

        //Their contact info - we still need to figure out what is going one with the direction!!!!
        EvolutionContact theirContact = new EvolutionContact(contact.getFriendUserId(), contact.getUserAccount(), contact.getEvolutionUserId(), 0);
        FriendKeyChain publicChainFromB58 = new FriendKeyChain(PARAMS, tpub, theirContact);
        FriendKeyChainGroup publicGroupFromB58 = FriendKeyChainGroup.friendlybuilder(PARAMS).build();
        publicChainFromB58.setLookaheadSize(5);
        publicGroupFromB58.addAndActivateHDChain(publicChainFromB58);
        publicChainFromB58.maybeLookAhead();
        DeterministicKey publicKeyFromB58 = publicGroupFromB58.freshKey(theirContact, SENDING_CHAIN);


        DeterministicKey publicWatchingKeyFromKey = publicChainFromKey.getWatchingKey();
        DeterministicKey publicWatchingKeyFromB58 = publicChainFromB58.getWatchingKey();

        String publicWatchingKeyFromKeyB58 = publicWatchingKeyFromKey.serializePubB58(PARAMS);
        String publicWatchingKeyFromB58B58 = publicWatchingKeyFromB58.serializePubB58(PARAMS);


        assertEquals(privateChainWatchingKey, publicWatchingKeyFromKey);
        assertEquals(privateChainWatchingKey, publicWatchingKeyFromB58);

        assertArrayEquals(privateKey.getPubKey(), publicKeyFromB58.getPubKey());
        assertArrayEquals(privateKey.getPubKey(), publicKeyFromB58.getPubKey());

    }

    @Test
    public void contactPubTest() {
        final NetworkParameters PARAMS = UnitTestParams.get();
        Sha256Hash userAhash = Sha256Hash.wrap("a11ce14f698b32e9bb306dba7bbbee831263dcf658abeebb39930460ead117e5");
        Sha256Hash userBhash = Sha256Hash.wrap("b0b052ff075c5ca3c16c3e20e9ac8223834475cc1324ab07889cb24ce6a62793");

        EvolutionContact contact = new EvolutionContact(userAhash, 0, userBhash, 0);
        String tpub = "tpubDLkp5kSwctd6bsLgG2pfbUpLSyjedfkBjy8HYtuczzPUwMCBkRW2Fe7TcEoVin5cLTr6YApGpy2MdKU7sfgLL7cMXTn16dLPdgKMqGg9tVE";

        EvolutionContact theirContact = new EvolutionContact(contact.getFriendUserId(), contact.getUserAccount(), contact.getEvolutionUserId(), contact.getFriendAccountReference());
        DeterministicKeyChain publicChainFromB58 = new FriendKeyChain(PARAMS, tpub, theirContact);

        DeterministicKey contactKey = publicChainFromB58.getWatchingKey();
        byte[] contactPub = contactKey.serializeContactPub();
        DeterministicKey contactPubKey = DeterministicKey.deserializeContactPub(PARAMS, contactPub);

        // compare the parts that should match.  xpub and tpub strings will not match
        // due to different depth and child numbers
        assertEquals(contactKey.getFingerprint(), contactPubKey.getFingerprint());
        assertArrayEquals(contactKey.getChainCode(), contactPubKey.getChainCode());
        assertArrayEquals(contactKey.getPubKey(), contactPubKey.getPubKey());
        DeterministicKey contactKeyFirstKey = HDKeyDerivation.deriveChildKey(contactKey, new ChildNumber(0, false));
        DeterministicKey contactPubKeyFirstKey = HDKeyDerivation.deriveChildKey(contactPubKey, new ChildNumber(0, false));
        assertArrayEquals(contactKeyFirstKey.getPubKey(), contactPubKeyFirstKey.getPubKey());
    }

    /*

        Test Vector 1:
            Mnemonic : birth kingdom trash renew flavor utility donkey gasp regular alert pave layer
            Seed Data : b16d3782e714da7c55a397d5f19104cfed7ffa8036ac514509bbb50807f8ac598eeb26f0797bd8cc221a6cbff2168d90a5e9ee025a5bd977977b9eccd97894bb
            Key Type : Secp256k1
            Derivation (Note : The second derivation is hardened) :
                “m/
                    0x775d3854c910b7dee436869c4724bed2fe0784e198b8a39f02bbb49d8ebcfc3b/
                    0xf537439f36d04a15474ff7423e4b904a14373fafb37a41db74c84f1dbb5c89a6'/
                    0x4c4592ca670c983fc43397dfd21a6f427fac9b4ac53cb4dcdc6522ec51e81e79
                    0”
            Key : 0xe8781fdef72862968cd9a4d2df34edaf9dcc5b17629ec505f0d2d1a8ed6f9f09

        Test Vector 2:
            Mnemonic : birth kingdom trash renew flavor utility donkey gasp regular alert pave layer
            Seed Data : b16d3782e714da7c55a397d5f19104cfed7ffa8036ac514509bbb50807f8ac598eeb26f0797bd8cc221a6cbff2168d90a5e9ee025a5bd977977b9eccd97894bb
            Key Type : Secp256k1
            Derivation (Note : All derivations except the last are hardened) : “m/9'/5'/15'/0'/
            0x555d3854c910b7dee436869c4724bed2fe0784e198b8a39f02bbb49d8ebcfc3a'/
            0xa137439f36d04a15474ff7423e4b904a14373fafb37a41db74c84f1dbb5c89b5'/0”
            Key : 0xfac40790776d171ee1db90899b5eb2df2f7d2aaf35ad56f07ffb8ed2c57f8e60

     */
    @Test
    public void test256BitDerivationDip14() throws UnreadableWalletException {
        String seedPhrase = "birth kingdom trash renew flavor utility donkey gasp regular alert pave layer";

        long now = Utils.currentTimeSeconds();
        DeterministicSeed seed = new DeterministicSeed(seedPhrase, null, "", now);

        assertEquals("b16d3782e714da7c55a397d5f19104cfed7ffa8036ac514509bbb50807f8ac598eeb26f0797bd8cc221a6cbff2168d90a5e9ee025a5bd977977b9eccd97894bb", Utils.HEX.encode(seed.getSeedBytes()));

        ImmutableList<ChildNumber> path = ImmutableList.of(
                new ExtendedChildNumber(Sha256Hash.wrap("775d3854c910b7dee436869c4724bed2fe0784e198b8a39f02bbb49d8ebcfc3b")),
                new ExtendedChildNumber(Sha256Hash.wrap("f537439f36d04a15474ff7423e4b904a14373fafb37a41db74c84f1dbb5c89a6"), true),
                new ExtendedChildNumber(Sha256Hash.wrap("4c4592ca670c983fc43397dfd21a6f427fac9b4ac53cb4dcdc6522ec51e81e79")),
                ChildNumber.ZERO
        );

        DeterministicKeyChain keyChain = DeterministicKeyChain.builder().seed(seed).accountPath(path).build();
        DeterministicKey watchingKey = keyChain.getWatchingKey();
        assertEquals("e8781fdef72862968cd9a4d2df34edaf9dcc5b17629ec505f0d2d1a8ed6f9f09", watchingKey.getPrivateKeyAsHex());

        assertEquals("tpubDF4tEyAdpXySRui9CvGRTSQU4BgLwGxuLPKEb9WqEE93raF2ffU1PRQ6oJHCgZ7dArzcMj9iKG8s8EFA1DdwgzWAXs61uFuRE1bQi8kAmLy", watchingKey.serializePubB58(PARAMS));
        assertEquals("tprv8iNr6Z8PgAHmYSgMKGbq42kMVAAQmwmzm5iTJdUXoxLf25zG3GeRCvnEdC6HKTHkU59nZkfjvcGk9VW2YHsFQMwsZrQLyNrGx9c37kgb368", watchingKey.serializePrivB58(PARAMS));

        // using a DIP15 path
        ImmutableList<ChildNumber> dip15path = ImmutableList.of(
                new ChildNumber(9, true),
                ChildNumber.FIVE_HARDENED,
                DerivationPathFactory.FEATURE_PURPOSE_DASHPAY,
                ChildNumber.ZERO_HARDENED,
                new ExtendedChildNumber(Sha256Hash.wrap("555d3854c910b7dee436869c4724bed2fe0784e198b8a39f02bbb49d8ebcfc3a"), true),
                new ExtendedChildNumber(Sha256Hash.wrap("a137439f36d04a15474ff7423e4b904a14373fafb37a41db74c84f1dbb5c89b5"), true),
                ChildNumber.ZERO
        );

        DeterministicKeyChain keyChainDip15 = DeterministicKeyChain.builder().seed(seed).accountPath(dip15path).build();
        DeterministicKey watchingKeyDip15 = keyChainDip15.getWatchingKey();
        assertEquals("fac40790776d171ee1db90899b5eb2df2f7d2aaf35ad56f07ffb8ed2c57f8e60", watchingKeyDip15.getPrivateKeyAsHex());

        assertEquals("tpubDLqNye58JQGox9dqWN5xZUgC5XGC6KwWmTSX6qGugrGEa5QffDm3iDfsVtX7qyXuWoQsXA6YCSuckKshyjnwiGGoYWHonAv2X98HTU613UH", watchingKeyDip15.serializePubB58(PARAMS));
        assertEquals("tprv8p9LqE2tA2b94gc3ciRNA525WVkFvzkcC9qjpKEcGaTqjb9u2pwTXj41KkZTj3c1a6fJUpyXRfcB4dimsYsLMjQjsTJwi5Ukx6tJ5BpmYpx", watchingKeyDip15.serializePrivB58(PARAMS));

        // Test Vector 3
        ImmutableList<ChildNumber> path3 = ImmutableList.of(
                new ExtendedChildNumber(Sha256Hash.wrap("775d3854c910b7dee436869c4724bed2fe0784e198b8a39f02bbb49d8ebcfc3b"))
        );

        DeterministicKeyChain keyChain3 = DeterministicKeyChain.builder().seed(seed).accountPath(path3).build();
        DeterministicKey watchingKey3 = keyChain3.getWatchingKey();
        assertEquals("f6a95ae75ea8362d9478932f71b262b3d981918fe030316686a475dea4889938", watchingKey3.getPrivateKeyAsHex());

        assertEquals("dptp1C5gGd8NzvAke5WNKyRfpDRyvV2UZ3jjrZVZU77qk9yZemMGSdZpkWp7y6wt3FzvFxAHSW8VMCaC1p6Ny5EqWuRm2sjvZLUUFMMwXhmW6eS69qjX958RYBH5R8bUCGZkCfUyQ8UVWcx9katkrRr", watchingKey3.serializeDip14PubB58(PARAMS));
        assertEquals("dpts1vgMVEs9mmv1YLwURCeoTn9CFMZ8JMVhyZuxQSKttNSETR3zydMFHMKTTNDQPf6nnupCCtcNnSu3nKZXAJhaguyoJWD4Ju5PE6PSkBqAKWci7HLz37qmFmZZU6GMkLvNLtST2iV8NmqqbX37c45", watchingKey3.serializeDip14PrivB58(PARAMS));

        assertEquals("dpmp1eNNCFkMFRrR75TjzPJ6FT6xqZLJ85CVC9xGU9JSrA77jjfCcZeSirqo4VAW5CjnoQN4nN6bjdX2tPhSJGp9QHemmUbpV7MU77ySecCqPFHin73MDBrxR9ydy1dVxtNAHXv2xfrehK44PWzoAC3", watchingKey3.serializeDip14PubB58(MAINNET));
        assertEquals("dpms2Ny3QsV82Hbg1Ltr5cXDu1pBARrwsNxTKANfQUWVzNZnYPMw9ZRsFhM8YkS2RbqfLN1yYkaVAsqteuAaVWGtaJCp374xEfxP5rzws6GVc7ULjYep7EaJ8kG81yJPWxinRksWbFsHZTwkES3o2pW", watchingKey3.serializeDip14PrivB58(MAINNET));

        // Test Vector 4
        ImmutableList<ChildNumber> path4 = ImmutableList.of(
                new ExtendedChildNumber(Sha256Hash.wrap("775d3854c910b7dee436869c4724bed2fe0784e198b8a39f02bbb49d8ebcfc3b")),
                new ExtendedChildNumber(Sha256Hash.wrap("f537439f36d04a15474ff7423e4b904a14373fafb37a41db74c84f1dbb5c89a6"), true)
        );

        DeterministicKeyChain keyChain4 = DeterministicKeyChain.builder().seed(seed).accountPath(path4).build();
        DeterministicKey watchingKey4 = keyChain4.getWatchingKey();
        assertEquals("b898ad92d3a0698bc3117d3777d82676673816ce52f4fc2f1263a2f676825f90", watchingKey4.getPrivateKeyAsHex());

        assertEquals("dptp1CLkexeadp6guoi8Fbiwq6CLZm3hT1DJLwHsxWvwYSeAhjenFhcQ9HumZSftfZEr4dyQjFD7gkM5bSn6Aj7F1Jve8KTn4JsMEaj9dFyJkYs4Ga5HSUqeajxGVmzaY1pEioDmvUtZL3J1NCDCmzQ", watchingKey4.serializeDip14PubB58(PARAMS));
        assertEquals("dpts1vwRsaPMQfqwp59ELpx5UeuYtdaMCJyGTwiGtr8zgf6qWPMWnhPpg8R73hwR1xLibbdKVdh17zfwMxFEMxZzBKUgPwvuosUGDKW4ayZjs3AQB9EGRcVpDoFT8V6nkcc6KzksmZxvmDcd3MqiPEu", watchingKey4.serializeDip14PrivB58(PARAMS));

        assertEquals("dpmp1edSabGYtKnMNofVv1bNGKsKUqMX22g3gXkaxZ7YeSminhxiRdh27dwSeptWhVyic6BC57BE5BHvU2P9VvgYth9ervKfz5kM6MLekAQe39igtqP7WbaBTieq3f2cJdceofeqV2GiWjPv17WjCeQ", watchingKey4.serializeDip14PubB58(MAINNET));
        assertEquals("dpms2PE7oD1KfBXcH56c1EpVutaXohtAmLS1oYAyttKbnfEPbMfSxdUSeUSn96A33u5b93q6qVf7WRcnEXrHhA9J4hhh8YnojeMG567Zht159e22oQY6VjEM6mx1gN8pXEQWQsBwL7M5wuiXgGY3tWK", watchingKey4.serializeDip14PrivB58(MAINNET));

        // we should do a round trip, but there are no deserialization methods for DIP14.
    }
}
