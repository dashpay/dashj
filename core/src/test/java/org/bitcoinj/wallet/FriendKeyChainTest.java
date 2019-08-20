package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.ExtendedChildNumber;
import org.bitcoinj.evolution.EvolutionContact;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.utils.BriefLogFormatter;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.*;

public class FriendKeyChainTest {

    private DeterministicKeyChain chain;
    private DeterministicKeyChain bip44chain;
    private final byte[] ENTROPY = Sha256Hash.hash("don't use a string seed like this in real life".getBytes());
    private long secs = 1389353062L;

    @Before
    public void setup() {
        BriefLogFormatter.init();
        // You should use a random seed instead. The secs constant comes from the unit test file, so we can compare
        // serialized data properly.
        chain = new DeterministicKeyChain(ENTROPY, "", secs);
        chain.setLookaheadSize(10);
        assertEquals(secs, checkNotNull(chain.getSeed()).getCreationTimeSeconds());

        bip44chain = new DeterministicKeyChain(new DeterministicSeed(ENTROPY, "", secs),
                ImmutableList.of(new ChildNumber(44, true), new ChildNumber(1, true), ChildNumber.ZERO_HARDENED));
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
                ChildNumber.FIVE_HARDENED, ChildNumber.ONE_HARDENED, ChildNumber.ZERO_HARDENED,
                new ExtendedChildNumber(userAhash), new ExtendedChildNumber(userBhash));

        assertEquals(accountPath, key.getPath());

        for(ChildNumber i : accountPath)
            System.out.println(i.toString());
    }

    @Test
    public void test256BitDerivation() throws UnreadableWalletException {

        String seedPhrase = "upper renew that grow pelican pave subway relief describe enforce suit hedgehog blossom dose swallow";

        long now = Utils.currentTimeSeconds();
        DeterministicSeed seed = new DeterministicSeed(seedPhrase, null, "", now);

        byte [] bytes = new byte[32];

        Utils.uint32ToByteArrayLE(5, bytes, 0);
        Utils.uint32ToByteArrayLE(12, bytes, 8);
        Utils.uint32ToByteArrayLE(15, bytes, 16);
        Utils.uint32ToByteArrayLE(1337, bytes, 24);

        Sha256Hash hash = Sha256Hash.wrap(bytes);
        assertEquals("05000000000000000c000000000000000f000000000000003905000000000000", hash.toString());

        ChildNumber num = new ExtendedChildNumber(hash);
        ImmutableList<ChildNumber> path = ImmutableList.of(num);

        DeterministicKeyChain keyChain = new DeterministicKeyChain(seed, path);
        DeterministicKey watchingKey = keyChain.getWatchingKey();
        assertEquals("02909fb2c2cd18c8fb99277bc26ec606e381d27c2af6bd87e222304e3baf450bf7", Utils.HEX.encode(watchingKey.getPubKey()));
    }

    @Test
    public void serializeTest() throws UnreadableWalletException {
        final NetworkParameters PARAMS = UnitTestParams.get();

        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
        Wallet wallet = new Wallet(PARAMS);

        Sha256Hash userAhash = Sha256Hash.wrap("c27eb14f698b32e9bb306dba7bbbee831263dcf658abeebb39930460ead117e5");
        Sha256Hash userBhash = Sha256Hash.wrap("ee2052ff075c5ca3c16c3e20e9ac8223834475cc1324ab07889cb24ce6a62793");

        wallet.addAndActivateHDChain(bip44chain);

        wallet.addReceivingFromFriendKeyChain(bip44chain.getSeed(), bip44chain.getKeyCrypter(),
                0, userAhash, userBhash);

        Protos.Wallet protos = new WalletProtobufSerializer().walletToProto(wallet);

        Wallet walletReloaded = new WalletProtobufSerializer().readWallet(PARAMS, null, protos);

        assertTrue(walletReloaded.hasReceivingFriendKeyChains());

        DeterministicKeyChain friendChain = walletReloaded.receivingFromFriendsGroup.getActiveKeyChain();

        DeterministicKey key = friendChain.getWatchingKey();

        ImmutableList<ChildNumber> accountPath = ImmutableList.of(ChildNumber.NINE_HARDENED, ChildNumber.ONE_HARDENED,
                ChildNumber.FIVE_HARDENED, ChildNumber.ONE_HARDENED, ChildNumber.ZERO_HARDENED,
                new ExtendedChildNumber(userAhash), new ExtendedChildNumber(userBhash));

        assertEquals(accountPath, key.getPath());

    }

    @Test
    public void testFriendSendKeyChain() {
        final NetworkParameters PARAMS = UnitTestParams.get();
        Sha256Hash userAhash = Sha256Hash.wrap("c27eb14f698b32e9bb306dba7bbbee831263dcf658abeebb39930460ead117e5");
        Sha256Hash userBhash = Sha256Hash.wrap("ee2052ff075c5ca3c16c3e20e9ac8223834475cc1324ab07889cb24ce6a62793");

        EvolutionContact contact = new EvolutionContact(userAhash, 0, userBhash);

        FriendKeyChain privateChain = new FriendKeyChain(bip44chain.getSeed(),
                FriendKeyChain.FRIEND_ROOT_PATH_TESTNET, 0, userAhash, userBhash);
        FriendKeyChainGroup privateGroup = new FriendKeyChainGroup(PARAMS);
        privateGroup.addAndActivateHDChain(privateChain);
        DeterministicKey privateKey = privateGroup.freshKey(contact, FriendKeyChain.KeyChainType.RECEIVING_CHAIN);

        DeterministicKey privateChainWatchingKey = privateChain.getWatchingKey().dropPrivateBytes().dropParent();

        FriendKeyChain publicChainFromKey = new FriendKeyChain(privateChainWatchingKey);
        FriendKeyChainGroup publicGroupFromKey = new FriendKeyChainGroup(PARAMS);
        publicGroupFromKey.addAndActivateHDChain(publicChainFromKey);
        DeterministicKey publicKey = privateGroup.freshKey(contact, FriendKeyChain.KeyChainType.RECEIVING_CHAIN);

        String tpub = "tpubDKqvGM7suJ1A9Ek8mm7gE9UZGxBCG9Zo8UKNYn26Y9yqzcLh7zqmeYL4KqU72m3CcCMwp1eoJreV4W7sdzTtnXz7CWkMFYh2biwWApkWxqi";


        //Their contact info - we still need to figure out what is going one with the direction!!!!
        EvolutionContact theirContact = new EvolutionContact(contact.getFriendUserId(), contact.getUserAccount(), contact.getEvolutionUserId());
        FriendKeyChain publicChainFromB58 = new FriendKeyChain(PARAMS, tpub, contact);
        FriendKeyChainGroup publicGroupFromB58 = new FriendKeyChainGroup(PARAMS);
        publicChainFromB58.setLookaheadSize(5);
        publicGroupFromB58.addAndActivateHDChain(publicChainFromB58);
        DeterministicKey publicKeyFromB58 = publicGroupFromB58.freshKey(theirContact, FriendKeyChain.KeyChainType.SENDING_CHAIN);


        DeterministicKey publicWatchingKeyFromKey = publicChainFromKey.getWatchingKey();
        DeterministicKey publicWatchingKeyFromB58 = publicChainFromB58.getWatchingKey();

        String publicWatchingKeyFromKeyB58 = publicWatchingKeyFromKey.serializePubB58(PARAMS);
        String publicWatchingKeyFromB58B58 = publicWatchingKeyFromB58.serializePubB58(PARAMS);


        assertEquals(privateChainWatchingKey, publicWatchingKeyFromKey);
        assertEquals(privateChainWatchingKey, publicWatchingKeyFromB58);

        assertArrayEquals(privateKey.getPubKey(), publicKeyFromB58.getPubKey());
        assertArrayEquals(privateKey.getPubKey(), publicKey.getPubKey());
        assertArrayEquals(privateKey.getPubKey(), publicKeyFromB58.getPubKey());

    }
}
