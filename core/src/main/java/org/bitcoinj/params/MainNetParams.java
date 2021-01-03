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

    public static final int MAINNET_MAJORITY_DIP0001_WINDOW = 4032;
    public static final int MAINNET_MAJORITY_DIP0001_THRESHOLD = 3226;

    public MainNetParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = CoinDefinition.proofOfWorkLimit;
        dumpedPrivateKeyHeader = 204;
        addressHeader = CoinDefinition.AddressHeader;
        p2shHeader = CoinDefinition.p2shHeader;
        port = CoinDefinition.Port;
        packetMagic = CoinDefinition.PacketMagic;
        bip32HeaderP2PKHpub = 0x0488b21e; // The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderP2PKHpriv = 0x0488ade4; // The 4 byte header that serializes in base58 to "xprv"
        genesisBlock.setDifficultyTarget(CoinDefinition.genesisBlockDifficultyTarget);
        genesisBlock.setTime(CoinDefinition.genesisBlockTime);
        genesisBlock.setNonce(CoinDefinition.genesisBlockNonce);

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        id = ID_MAINNET;
        subsidyDecreaseBlockCount = CoinDefinition.subsidyDecreaseBlockCount;
        spendableCoinbaseDepth = CoinDefinition.spendableCoinbaseDepth;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals(CoinDefinition.genesisHash),
                genesisHash);

        //CoinDefinition.initCheckpoints(checkpoints);

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
        checkpoints.put( 107996, Sha256Hash.wrap("00000000000a23840ac16115407488267aa3da2b9bc843e301185b7d17e4dc40"));
        checkpoints.put( 137993, Sha256Hash.wrap("00000000000cf69ce152b1bffdeddc59188d7a80879210d6e5c9503011929c3c"));
        checkpoints.put( 167996, Sha256Hash.wrap("000000000009486020a80f7f2cc065342b0c2fb59af5e090cd813dba68ab0fed"));
        checkpoints.put( 207992, Sha256Hash.wrap("00000000000d85c22be098f74576ef00b7aa00c05777e966aff68a270f1e01a5"));
        checkpoints.put( 312645, Sha256Hash.wrap("0000000000059dcb71ad35a9e40526c44e7aae6c99169a9e7017b7d84b1c2daf"));
        checkpoints.put( 407452, Sha256Hash.wrap("000000000003c6a87e73623b9d70af7cd908ae22fee466063e4ffc20be1d2dbc"));
        checkpoints.put( 523412, Sha256Hash.wrap("000000000000e54f036576a10597e0e42cc22a5159ce572f999c33975e121d4d"));
        checkpoints.put( 523930, Sha256Hash.wrap("0000000000000bccdb11c2b1cfb0ecab452abf267d89b7f46eaf2d54ce6e652c"));
        checkpoints.put(1028181, Sha256Hash.wrap("000000000000004534fd030e18578a987b443b9289a5e2de9fe18505f5fb0295"));
/*

        dnsSeeds = new String[] {
                "seed.bitcoin.sipa.be",         // Pieter Wuille
                "dnsseed.bluematt.me",          // Matt Corallo
                "dnsseed.bitcoin.dashjr.org",   // Luke Dashjr
                "seed.bitcoinstats.com",        // Chris Decker
                "seed.bitnodes.io",             // Addy Yeow
                "bitseed.xf2.org",              // Jeff Garzik
                "seed.bitcoin.jonasschnelli.ch",// Jonas Schnelli
                "bitcoin.bloqseeds.net",        // Bloq
        };
        httpSeeds = new HttpDiscovery.Details[] {
                // Andreas Schildbach
                new HttpDiscovery.Details(
                        ECKey.fromPublicOnly(Utils.HEX.decode("0238746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")),
                        URI.create("http://httpseed.bitcoin.schildbach.de/peers")
                )
        };                  */

        addrSeeds = new int[] {
                0x50630905,
                0x6dbf8405,
                0xd3bf8405,
                0xd5bf8405,
                0xd8bf8405,
                0x25738605,
                0x40abbd05,
                0x2117c805,
                0x6035c805,
                0x9560dd12,
                0x6f00af17,
                0x7000af17,
                0x7100af17,
                0x7200af17,
                0x1580b617,
                0x1780b617,
                0x1880b617,
                0x1980b617,
                0x4ca0e317,
                0x17a3e317,
                0x34a3e317,
                0x3d72661b,
                0x52491f1f,
                0x6360c422,
                0x4582c722,
                0x6165e122,
                0xa5c0a723,
                0xe3f2c423,
                0x01bbc523,
                0x15e36125,
                0x91a17825,
                0xd2c79d25,
                0xdcc0dd25,
                0x2ac2dd25,
                0x2bc2dd25,
                0x2c067128,
                0x2e4de52b,
                0xb9ce202d,
                0x06404c2d,
                0xc36d1c2e,
                0xb19e252e,
                0x39a6a32e,
                0x48e5342f,
                0xe4314b2f,
                0x9c0e5a2f,
                0x5e42622f,
                0xcf60682f,
                0x5166f42f,
                0x8f2a0f33,
                0x24692633,
                0xfb802633,
                0xd520ff33,
                0x16704f34,
                0xbb87bb34,
                0xbb24eb34,
                0x0825f234,
                0xc3e74636,
                0xa291ac36,
                0xd6038a3e,
                0x07088a3e,
                0x67088a3e,
                0x320d923e,
                0x55fd8e3f,
                0x30da2240,
                0x729d8c40,
                0xe29f8c40,
                0x56f21742,
                0x59f21742,
                0x5af21742,
                0x5bf21742,
                0x550bac42,
                0x461bac42,
                0x5a0d3345,
                0x650d3345,
                0x46143345,
                0x47143345,
                0xb4e4cf4a,
                0xf8047f4b,
                0x94db4a4c,
                0x92e2514d,
                0x46cf294e,
                0x0013534e,
                0x46a9854e,
                0x961b7850,
                0xdccda951,
                0x54e5a951,
                0xf502ab51,
                0x34e37652,
                0x671da552,
                0x8015d352,
                0x8315d352,
                0x8815d352,
                0x8b15d352,
                0x5260ea54,
                0x5660ea54,
                0x5760ea54,
                0xd7c71955,
                0x1dfeb855,
                0xb5feb855,
                0xceaad955,
                0xf3abd955,
                0x4101ff55,
                0xd404ff55,
                0x343f6a57,
                0x37fd7557,
                0xf1dc2459,
                0x0e002859,
                0x45722859,
                0x4ab5ee59,
                0x52efdb5b,
                0x53efdb5b,
                0x1c393f5c,
                0x78393f5c,
                0x50cd5a5d,
                0xd0d5685d,
                0x99d89e5d,
                0xa6ae9c5e,
                0x3eefb05e,
                0xdfaab15e,
                0xe1e0b15e,
                0x73e1b15e,
                0xd2e1b15e,
                0x21e8b15e,
                0x3dfab15e,
                0xa28b2b5f,
                0x2fe2b55f,
                0x6132b75f,
                0x6233b75f,
                0x8d33b75f,
                0x8635b75f,
                0x390bd85f,
                0x2f93d85f,
                0xa4607e60,
                0x00602565,
                0x60602565,
                0x51a9c468,
                0xa406df68,
                0x238c066b,
                0x378c066b,
                0x96af066b,
                0x89769b6b,
                0xd465bf6b,
                0x16e03d6c,
                0x21e03d6c,
                0x4c190a6e,
                0x76569f73,
                0xd6376a7a,
                0xdaad727a,
                0xa640c17b,
                0x45fbb982,
                0x71fbb982,
                0x16668285,
                0x17c1638b,
                0x2a324294,
                0x6d0aec97,
                0xf0397f9a,
                0xfe397f9a,
                0x1b3b7f9a,
                0x083c7f9a,
                0x5e9b459f,
                0x0c20599f,
                0xa613649f,
                0xa713649f,
                0x8314cb9f,
                0x90a72ca3,
                0xeda72ca3,
                0xbfa82ca3,
                0x610f58a7,
                0xcb60eba8,
                0xcd60eba8,
                0x2f63eba8,
                0x3ca24baa,
                0xdba24baa,
                0x6ca34baa,
                0x2ab151ac,
                0x947956ac,
                0x0d4068ac,
                0x62056eac,
                0xa9066eac,
                0x32f1d4ad,
                0xcf147ab0,
                0x1da03eb2,
                0x1e32d1b2,
                0x072aeeb2,
                0x25ae16b9,
                0xfa7e1ab9,
                0x5b651cb9,
                0x85651cb9,
                0xd94023b9,
                0x754323b9,
                0x7dd22bb9,
                0x71c23ab9,
                0xeae03ab9,
                0xdd6840b9,
                0xde6840b9,
                0xdf6840b9,
                0x2e7a6ab9,
                0x1e5577b9,
                0x692585b9,
                0x9aed8bb9,
                0x6a1a8db9,
                0x90d48eb9,
                0x7db29cb9,
                0xd8b29cb9,
                0xdab29cb9,
                0xdeb29cb9,
                0x15a8a5b9,
                0x16a8a5b9,
                0x17a8a5b9,
                0x19a8a5b9,
                0x9008a8b9,
                0x8361b7b9,
                0x0d28b9b9,
                0xe575cbb9,
                0xfa2cd4b9,
                0x0125d5b9,
                0x0625d5b9,
                0x6301d9b9,
                0x6401d9b9,
                0x3070f3b9,
                0x5070f3b9,
                0xbb70f3b9,
                0xdd70f3b9,
                0x12bdfdb9,
                0x42bdfdb9,
                0x46bdfdb9,
                0x50bdfdb9,
                0x5845a6bc,
                0xab4ae3bc,
                0xc14ae3bc,
                0xb4b804be,
                0xfa080abe,
                0xe48251c0,
                0x68b6a1c0,
                0x6cb6a1c0,
                0x6db6a1c0,
                0x298fe3c0,
                0xc4e4e3c0,
                0x11e6fac0,
                0x55bb1dc1,
                0x48e0eac1,
                0x64e0eac1,
                0x67e0eac1,
                0x91e0eac1,
                0x641463c2,
                0x6d699ac3,
                0x7d699ac3,
                0x746514c6,
                0x4b8017c6,
                0x0e4a35c6,
                0x8ebe3dc6,
                0xc36ec9c7,
                0xac807ac8,
                0x7b6247ca,
                0x7c6247ca,
                0x7d6247ca,
                0x7e6247ca,
                0x6af310cc,
                0x62f510cc,
                0x5758b1d1,
                0xcb5bb1d1,
                0x106018d4,
                0x1a6018d4,
                0xfdeb2fd4,
                0x2fc9e3d4,
                0xf025edd4,
                0x5fc540d5,
                0x5d5088d5,
                0x9f5088d5,
                0x3ed96bd8,
                0xa191bdd8,
                0xb293bdd8,
                0x35023dd9,
                0x6e859cdd,
                0x1e3be7de
        };

        strSporkAddress = "Xgtyuk76vhuFW2iT7UAiHgNdWXCf3J34wh";
        budgetPaymentsStartBlock = 328008;
        budgetPaymentsCycleBlocks = 16616;
        budgetPaymentsWindowBlocks = 100;

        DIP0001Window = MAINNET_MAJORITY_DIP0001_WINDOW;
        DIP0001Upgrade = MAINNET_MAJORITY_DIP0001_THRESHOLD;
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

        BIP65Height = 619382; // 00000000000076d8fcea02ec0963de4abfd01e771fec0863f960c2c64fe6f357
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
