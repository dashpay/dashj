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
                0xddd53802,
                0xbe430205,
                0x3a490205,
                0x22ed0905,
                0xf36d4f05,
                0xe12c6505,
                0x12cab505,
                0x48fdbd05,
                0x09f48b12,
                0x94819d12,
                0x2af65117,
                0x17a3e317,
                0x4ce8e417,
                0xe1d5fd17,
                0x24610a1f,
                0x6863941f,
                0xf263941f,
                0x3204b21f,
                0x765b5222,
                0xf2aa5322,
                0x45336922,
                0xde367f22,
                0x4c497f22,
                0xf2edd122,
                0xa826d723,
                0x38e31225,
                0x15e36125,
                0x7785c22a,
                0x2e4de52b,
                0x1818212d,
                0x6b40382d,
                0x689f472d,
                0xf1274c2d,
                0x43424d2d,
                0x472e4f2d,
                0x2d75552d,
                0x53a2562d,
                0x55a2562d,
                0x9aa2562d,
                0xbdbd1e2e,
                0xd5bd1e2e,
                0xd6bd1e2e,
                0xfbbd1e2e,
                0xf228242e,
                0x1cf1fe2e,
                0x1476382f,
                0xe8985b2f,
                0x6a7b622f,
                0x5baf1132,
                0xb70e7432,
                0xce600f33,
                0x2a750f33,
                0xe6b32633,
                0xd2bf5333,
                0xd5479033,
                0xeda99e33,
                0x3c8dca34,
                0x60b9a436,
                0x2bdada36,
                0x8cbeab3e,
                0x8ebeab3e,
                0xce41fb40,
                0x45f3f442,
                0xe614ca43,
                0xd76b3d45,
                0xd96b3d45,
                0xf26b3d45,
                0x4589764a,
                0xb3d4dc4d,
                0x0013534e,
                0xf8885550,
                0x8bddd350,
                0xe784f050,
                0xa50d4751,
                0xf502ab51,
                0x53e6ca52,
                0x1715d352,
                0xb315d352,
                0xf015d352,
                0x6919d352,
                0x34743454,
                0xccb3f254,
                0x5bf81155,
                0x59a5ce55,
                0x5aa5ce55,
                0x23f1d155,
                0xbef1d155,
                0x04f2d155,
                0x62f2d155,
                0xceaad955,
                0x2c0d2859,
                0x36432d59,
                0x728cbe5d,
                0xc9eeb05e,
                0x8d33b75f,
                0x9233b75f,
                0x2735b75f,
                0x22c4d35f,
                0xe12dd75f,
                0x786ed75f,
                0xf55fa067,
                0xfd5fa067,
                0x5a18a16b,
                0x46f73d6c,
                0xa640c17b,
                0x081c448a,
                0xf0c7098b,
                0xaaaaee8c,
                0x6b355f8d,
                0x65cdca8e,
                0xa78e7e90,
                0xc278ca90,
                0x421c8391,
                0x441c8391,
                0x741c8391,
                0x751c8391,
                0x82a3ef91,
                0xceafb992,
                0x91414398,
                0x4aa2659e,
                0x1ca8659e,
                0xcd3347a7,
                0x3e4f56a7,
                0x8a3677a8,
                0xf155eba8,
                0x315deba8,
                0xbe68eba8,
                0x6b7856ac,
                0x82dad4ad,
                0xc9e922ae,
                0xcae922ae,
                0xcbe922ae,
                0xdc115eb0,
                0x914166b0,
                0xc6397bb0,
                0xc8397bb0,
                0xcb397bb0,
                0xcd397bb0,
                0x2b88dfb0,
                0x81793fb2,
                0x7e5b9db2,
                0xb05b9db2,
                0xb35b9db2,
                0x4dbf44b4,
                0xe03405b9,
                0xaa973eb9,
                0xae973eb9,
                0xde6840b9,
                0xdf6840b9,
                0x3eca52b9,
                0xec1675b9,
                0xd43a7ab9,
                0x781b8db9,
                0x7b1b8db9,
                0x613e8db9,
                0x90d48eb9,
                0x75aba5b9,
                0x289eafb9,
                0x8c3bb1b9,
                0xa328b9b9,
                0x7153e4b9,
                0x9c53e4b9,
                0x700af3b9,
                0x730af3b9,
                0x6af128bc,
                0x28e67fbc,
                0xf3ed7fbc,
                0x1906a9c0,
                0x58381dc1,
                0x603b1dc1,
                0xc3e81ac2,
                0x884bb6c2,
                0xdb5f62c3,
                0x11d2b5c3,
                0x40d3b5c3,
                0xe31b39c6,
                0xad807ac8,
                0x70c63dca,
                0xd3f83dca,
                0x263f81d4,
                0xce7941d5,
                0x5f93bdd8,
                0xb293bdd8,
                0x5e97bdd8,
                0xcf97bdd8
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
