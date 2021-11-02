/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
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

package org.bitcoinj.params;

import org.bitcoinj.core.*;
import org.bitcoinj.quorums.LLMQParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashMap;

import static com.google.common.base.Preconditions.*;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends AbstractBitcoinNetParams {
    private static final Logger log = LoggerFactory.getLogger(MainNetParams.class);

    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public MainNetParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;

        // 00000fffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
        maxTarget = Utils.decodeCompactBits(0x1e0fffffL);
        dumpedPrivateKeyHeader = 204;
        addressHeader = 76;
        p2shHeader = 16;
        port = 9999;
        packetMagic = 0xbf0c6bbd;
        bip32HeaderP2PKHpub = 0x0488b21e; // The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderP2PKHpriv = 0x0488ade4; // The 4 byte header that serializes in base58 to "xprv"
        genesisBlock.setDifficultyTarget(0x1e0ffff0L);
        genesisBlock.setTime(1390095618L);
        genesisBlock.setNonce(28917698);

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210240;
        spendableCoinbaseDepth = 100;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("00000ffd590b1485b3caadc19b22e6379c733355108f107a430458cdf3407ab6"),
                genesisHash);

        dnsSeeds = new String[] {
                "dnsseed.dash.org"
        };

        httpSeeds = null; /*new HttpDiscovery.Details[] {*/

        // This contains (at a minimum) the blocks which are not BIP30 compliant. BIP30 changed how duplicate
        // transactions are handled. Duplicated transactions could occur in the case where a coinbase had the same
        // extraNonce and the same outputs but appeared at different heights, and greatly complicated re-org handling.
        // Having these here simplifies block connection logic considerably.
        checkpoints.put(  1500, Sha256Hash.wrap("000000aaf0300f59f49bc3e970bad15c11f961fe2347accffff19d96ec9778e3"));
        checkpoints.put(  4991, Sha256Hash.wrap("000000003b01809551952460744d5dbb8fcbd6cbae3c220267bf7fa43f837367"));
        checkpoints.put(  9918, Sha256Hash.wrap("00000000213e229f332c0ffbe34defdaa9e74de87f2d8d1f01af8d121c3c170b"));
        checkpoints.put( 16912, Sha256Hash.wrap("00000000075c0d10371d55a60634da70f197548dbbfa4123e12abfcbc5738af9"));
        checkpoints.put( 23912, Sha256Hash.wrap("0000000000335eac6703f3b1732ec8b2f89c3ba3a7889e5767b090556bb9a276"));
        checkpoints.put( 35457, Sha256Hash.wrap("0000000000b0ae211be59b048df14820475ad0dd53b9ff83b010f71a77342d9f"));
        checkpoints.put( 45479, Sha256Hash.wrap("000000000063d411655d590590e16960f15ceea4257122ac430c6fbe39fbf02d"));
        checkpoints.put( 55895, Sha256Hash.wrap("0000000000ae4c53a43639a4ca027282f69da9c67ba951768a20415b6439a2d7"));
        checkpoints.put( 68899, Sha256Hash.wrap("0000000000194ab4d3d9eeb1f2f792f21bb39ff767cb547fe977640f969d77b7"));
        checkpoints.put( 74619, Sha256Hash.wrap("000000000011d28f38f05d01650a502cc3f4d0e793fbc26e2a2ca71f07dc3842"));
        checkpoints.put( 75095, Sha256Hash.wrap("0000000000193d12f6ad352a9996ee58ef8bdc4946818a5fec5ce99c11b87f0d"));
        checkpoints.put( 88805, Sha256Hash.wrap("00000000001392f1652e9bf45cd8bc79dc60fe935277cd11538565b4a94fa85f"));
        checkpoints.put(107996, Sha256Hash.wrap("00000000000a23840ac16115407488267aa3da2b9bc843e301185b7d17e4dc40"));
        checkpoints.put(137993, Sha256Hash.wrap("00000000000cf69ce152b1bffdeddc59188d7a80879210d6e5c9503011929c3c"));
        checkpoints.put(167996, Sha256Hash.wrap("000000000009486020a80f7f2cc065342b0c2fb59af5e090cd813dba68ab0fed"));
        checkpoints.put(207992, Sha256Hash.wrap("00000000000d85c22be098f74576ef00b7aa00c05777e966aff68a270f1e01a5"));
        checkpoints.put(312645, Sha256Hash.wrap("0000000000059dcb71ad35a9e40526c44e7aae6c99169a9e7017b7d84b1c2daf"));
        checkpoints.put(407452, Sha256Hash.wrap("000000000003c6a87e73623b9d70af7cd908ae22fee466063e4ffc20be1d2dbc"));
        checkpoints.put(523412, Sha256Hash.wrap("000000000000e54f036576a10597e0e42cc22a5159ce572f999c33975e121d4d"));
        checkpoints.put(523930, Sha256Hash.wrap("0000000000000bccdb11c2b1cfb0ecab452abf267d89b7f46eaf2d54ce6e652c"));
        checkpoints.put(750000, Sha256Hash.wrap("00000000000000b4181bbbdddbae464ce11fede5d0292fb63fdede1e7c8ab21c"));
        checkpoints.put(888900, Sha256Hash.wrap("0000000000000026c29d576073ab51ebd1d3c938de02e9a44c7ee9e16f82db28"));
        checkpoints.put(967800, Sha256Hash.wrap("0000000000000024e26c7df7e46d673724d223cf4ca2b2adc21297cc095600f4"));
        checkpoints.put(1067570, Sha256Hash.wrap("000000000000001e09926bcf5fa4513d23e870a34f74e38200db99eb3f5b7a70"));
        checkpoints.put(1167570, Sha256Hash.wrap("000000000000000fb7b1e9b81700283dff0f7d87cf458e5edfdae00c669de661"));
        checkpoints.put(1364585, Sha256Hash.wrap("00000000000000022f355c52417fca9b73306958f7c0832b3a7bce006ca369ef"));
        checkpoints.put(1450000, Sha256Hash.wrap("00000000000000105cfae44a995332d8ec256850ea33a1f7b700474e3dad82bc"));

        // Dash does not have a Http Seeder
//        httpSeeds = new HttpDiscovery.Details[] {
//                // Andreas Schildbach
//                new HttpDiscovery.Details(
//                        ECKey.fromPublicOnly(Utils.HEX.decode("0238746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")),
//                        URI.create("http://httpseed.bitcoin.schildbach.de/peers")
//                )
//        };

        // updated with Dash Core 0.17.0.3 seed list
        addrSeeds = new int[] {
                0x8b86f801,
                0xddd53802,
                0xbe430205,
                0x3a490205,
                0x22ed0905,
                0x0d632d05,
                0xf36d4f05,
                0x6dbf8405,
                0x70bf8405,
                0x0586de12,
                0x1bf34714,
                0x2af65117,
                0x987b6a17,
                0x7f00a317,
                0x17a3e317,
                0x4ce8e417,
                0x51e8e417,
                0xe1d5fd17,
                0x24610a1f,
                0x52491f1f,
                0x3204b21f,
                0x765b5222,
                0x192e5322,
                0xf2aa5322,
                0x84136922,
                0xf2edd122,
                0xf61fef22,
                0x9d55ff22,
                0x38e31225,
                0x15e36125,
                0x2e4de52b,
                0x1818212d,
                0x7f79222d,
                0x6b40382d,
                0xf1274c2d,
                0x472e4f2d,
                0x53a2562d,
                0x55a2562d,
                0x329b152e,
                0xf228242e,
                0x1cf1fe2e,
                0x1476382f,
                0xe8985b2f,
                0xbecc5b2f,
                0x5e42622f,
                0x6a7b622f,
                0x0223692f,
                0x0982f42f,
                0x5baf1132,
                0xce600f33,
                0xe6b32633,
                0xd5479033,
                0xeda99e33,
                0x1b124734,
                0x3c8dca34,
                0x2bdada36,
                0xc0038a3e,
                0x55fd8e3f,
                0xfa2dfa3f,
                0xce41fb40,
                0x560cac42,
                0x44f3f442,
                0x45f3f442,
                0xd76b3d45,
                0xd86b3d45,
                0xda6b3d45,
                0xf06b3d45,
                0x4589764a,
                0x6089764a,
                0x6189764a,
                0x9fec434e,
                0x0013534e,
                0x9c1e624f,
                0x3b1f624f,
                0x321e6450,
                0x8bddd350,
                0xf502ab51,
                0x1715d352,
                0xb315d352,
                0xe215d352,
                0xf015d352,
                0xedc5df52,
                0xd7a96053,
                0xccb3f254,
                0x59a5ce55,
                0x23f1d155,
                0xbef1d155,
                0xc9f1d155,
                0x04f2d155,
                0xceaad955,
                0x4cab7758,
                0x2c0d2859,
                0x36432d59,
                0x6ceddb5b,
                0x6feddb5b,
                0x52efdb5b,
                0xc9eeb05e,
                0x6132b75f,
                0x8d33b75f,
                0x2b34b75f,
                0x2735b75f,
                0x22c4d35f,
                0xe12dd75f,
                0x786ed75f,
                0x43f0da67,
                0x232aa068,
                0x5a18a16b,
                0x7ab23d6c,
                0x46f73d6c,
                0x0a9eb276,
                0xa640c17b,
                0x16668285,
                0x081c448a,
                0xf0c7098b,
                0xaaaaee8c,
                0xa67f5b90,
                0xc278ca90,
                0x421c8391,
                0x441c8391,
                0x741c8391,
                0x751c8391,
                0x82a3ef91,
                0xe00c3b92,
                0xceafb992,
                0x6d0aec97,
                0x91414398,
                0x4aa2659e,
                0x1ca8659e,
                0xeda72ca3,
                0x1da92ca3,
                0x33ab2ca3,
                0x6460aca3,
                0xcd3347a7,
                0x8a3677a8,
                0xf155eba8,
                0x315deba8,
                0xbe68eba8,
                0xa88b3ea9,
                0xdba24baa,
                0x58b251ac,
                0x6b7856ac,
                0x82dad4ad,
                0xc8a2d6ad,
                0x45cce7ad,
                0xdc115eb0,
                0xc6397bb0,
                0xc8397bb0,
                0xcb397bb0,
                0xcd397bb0,
                0x9abd21b2,
                0x81793fb2,
                0xbb824fb2,
                0x7e5b9db2,
                0xb05b9db2,
                0xb35b9db2,
                0xb608aab2,
                0x4dbf44b4,
                0xfa7e1ab9,
                0x7dd22bb9,
                0xcfc12db9,
                0xd1c12db9,
                0xc6383eb9,
                0xde6840b9,
                0xdf6840b9,
                0x032171b9,
                0x052171b9,
                0x062171b9,
                0x6d1b8db9,
                0x613e8db9,
                0x90d48eb9,
                0x17a8a5b9,
                0x19a8a5b9,
                0x1ba8a5b9,
                0xf3a8a5b9,
                0x289eafb9,
                0x8c3bb1b9,
                0x9d62b7b9,
                0x47dcdbb9,
                0x4953e4b9,
                0x7053e4b9,
                0x7353e4b9,
                0x9d53e4b9,
                0x8796f8b9,
                0x9496f8b9,
                0x6af128bc,
                0x9262a6bc,
                0xec9502be,
                0xb4b804be,
                0x1906a9c0,
                0x884bb6c2,
                0xdb5f62c3,
                0xf4b39ac3,
                0x11d2b5c3,
                0x40d3b5c3,
                0xe31b39c6,
                0xd3f83dca,
                0xc2dc65cc,
                0x46e4b4cf,
                0xcbc849d0,
                0xcc335fd1,
                0xf63d53d4,
                0xf73d53d4,
                0x3b09edd4,
                0x684e88d5,
                0x3ed96bd8,
                0x5f93bdd8,
                0xb293bdd8,
                0x5e97bdd8,
                0xcf97bdd8,
                0x5be079dc
        };

        strSporkAddress = "Xgtyuk76vhuFW2iT7UAiHgNdWXCf3J34wh";
        budgetPaymentsStartBlock = 328008;
        budgetPaymentsCycleBlocks = 16616;
        budgetPaymentsWindowBlocks = 100;

        DIP0001BlockHeight = 782208;

        fulfilledRequestExpireTime = 60*60;
        masternodeMinimumConfirmations = 15;
        superblockStartBlock = 614820;
        superblockCycle = 16616;
        nGovernanceMinQuorum = 10;
        nGovernanceFilterElements = 20000;

        powDGWHeight = 34140;
        powKGWHeight = 15200;
        powAllowMinimumDifficulty = false;
        powNoRetargeting = false;

        instantSendConfirmationsRequired = 6;
        instantSendKeepLock = 24;

        DIP0003BlockHeight = 1028160;
        deterministicMasternodesEnabledHeight = 1047200;
        deterministicMasternodesEnabled = true;

        DIP0008BlockHeight = 1088640;

        // long living quorum params
        llmqs = new HashMap<LLMQParameters.LLMQType, LLMQParameters>(3);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_50_60, LLMQParameters.llmq50_60);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_400_60, LLMQParameters.llmq400_60);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_400_85, LLMQParameters.llmq400_85);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_100_67, LLMQParameters.llmq100_67);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_400_60;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_100_67;

        BIP34Height = 951;    // 000001f35e70f7c5705f64c6c5cc3dea9449e74d5b5c7cf74dad1bcca14a8012
        BIP65Height = 619382; // 00000000000076d8fcea02ec0963de4abfd01e771fec0863f960c2c64fe6f357
        BIP66Height = 245817;

        coinType = 5;
    }

    private static MainNetParams instance;
    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }

    @Override
    protected void verifyDifficulty(StoredBlock storedPrev, Block nextBlock, BigInteger newTarget) {

        long newTargetCompact = calculateNextDifficulty(storedPrev, nextBlock, newTarget);
        long receivedTargetCompact = nextBlock.getDifficultyTarget();
        int height = storedPrev.getHeight() + 1;

        if (/*height >= powDGWHeight &&*/ height <= 68589) {
            double n1 = convertBitsToDouble(receivedTargetCompact);
            double n2 = convertBitsToDouble(newTargetCompact);

            if (java.lang.Math.abs(n1 - n2) > n1 * 0.5 )
                throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    Long.toHexString(newTargetCompact) + " vs " + Long.toHexString(receivedTargetCompact));
        } else {
            if (newTargetCompact != receivedTargetCompact)
                throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                        Long.toHexString(newTargetCompact) + " vs " + Long.toHexString(receivedTargetCompact));
        }
    }

    static double convertBitsToDouble(long nBits) {
        long nShift = (nBits >> 24) & 0xff;

        double dDiff =
                (double)0x0000ffff / (double)(nBits & 0x00ffffff);

        while (nShift < 29)
        {
            dDiff *= 256.0;
            nShift++;
        }
        while (nShift > 29)
        {
            dDiff /= 256.0;
            nShift--;
        }

        return dDiff;
    }
}
