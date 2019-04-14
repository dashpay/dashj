/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

import com.google.common.base.Stopwatch;
import org.bitcoinj.core.*;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.Utils.HEX;

/**
 * Parameters for a named devnet, a separate instance of Dash that has relaxed rules suitable for development
 * and testing of applications and new Dash versions.  The name of the devnet is used to generate the
 * second block of the blockchain.
 */
public class DevNetParams extends AbstractBitcoinNetParams {
    private static final Logger log = LoggerFactory.getLogger(DevNetParams.class);

    public static final int DEVNET_MAJORITY_DIP0001_WINDOW = 4032;
    public static final int DEVNET_MAJORITY_DIP0001_THRESHOLD = 3226;

    private static final BigInteger MAX_TARGET = Utils.decodeCompactBits(0x207fffff);
    BigInteger maxUint256 = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);


    private static int DEFAULT_PROTOCOL_VERSION = 70211;
    private int protocolVersion;


    public DevNetParams(String devNetName, String sporkAddress, int defaultPort, String [] dnsSeeds) {
        this(devNetName, sporkAddress, defaultPort, dnsSeeds, false, DEFAULT_PROTOCOL_VERSION);
    }

    public DevNetParams(String devNetName, String sporkAddress, int defaultPort, String [] dnsSeeds, boolean supportsEvolution) {
        this(devNetName, sporkAddress, defaultPort, dnsSeeds, supportsEvolution, DEFAULT_PROTOCOL_VERSION);
    }

    public DevNetParams(String devNetName, String sporkAddress, int defaultPort, String [] dnsSeeds, boolean supportsEvolution, int protocolVersion) {
        super();
        this.devNetName = "devnet-" + devNetName;
        id = ID_DEVNET + "." + devNetName;

        packetMagic = 0xe2caffce;
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;

        maxTarget = MAX_TARGET;
        port = defaultPort;
        addressHeader = CoinDefinition.testnetAddressHeader;
        p2shHeader = CoinDefinition.testnetp2shHeader;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1417713337L);
        genesisBlock.setDifficultyTarget(0x207fffff);
        genesisBlock.setNonce(1096447);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210240;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000008ca1832a4baf228eb1553c03d3a2c8e02399550dd6ea8d65cec3ef23d2e"));

        devnetGenesisBlock = findDevnetGenesis(this, this.devNetName, getGenesisBlock(), Coin.valueOf(50, 0));
        alertSigningKey = HEX.decode(CoinDefinition.TESTNET_SATOSHI_KEY);

        this.dnsSeeds = dnsSeeds;

        checkpoints.put(    1, devnetGenesisBlock.getHash());

        addrSeeds = null;
        bip32HeaderPub = 0x043587cf;
        bip32HeaderPriv = 0x04358394;

        strSporkAddress = sporkAddress;
        budgetPaymentsStartBlock = 4100;
        budgetPaymentsCycleBlocks = 50;
        budgetPaymentsWindowBlocks = 10;

        majorityEnforceBlockUpgrade = TestNet2Params.TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TestNet2Params.TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TestNet2Params.TESTNET_MAJORITY_WINDOW;

        DIP0001Window = DEVNET_MAJORITY_DIP0001_WINDOW;
        DIP0001Upgrade = DEVNET_MAJORITY_DIP0001_THRESHOLD;
        DIP0001BlockHeight = 2;

        fulfilledRequestExpireTime = 5*60;
        masternodeMinimumConfirmations = 1;
        superblockStartBlock = 4200;
        superblockCycle = 24;
        nGovernanceMinQuorum = 1;
        nGovernanceFilterElements = 500;

        powDGWHeight = 4001;
        powKGWHeight = 4001;
        powAllowMinimumDifficulty = true;
        powNoRetargeting = false;
        this.supportsEvolution = supportsEvolution;

        instantSendConfirmationsRequired = 2;
        instantSendKeepLock = 6;

        this.protocolVersion = protocolVersion;

        //LLMQ parameters
        llmqs = new HashMap<LLMQParameters.LLMQType, LLMQParameters>(3);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_50_60, LLMQParameters.llmq50_60);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_400_60, LLMQParameters.llmq400_60);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_400_85, LLMQParameters.llmq400_85);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_50_60;
    }

    //support more than one DevNet
    private static HashMap<String, DevNetParams> instances;

    //get the devnet by name or create it if it doesn't exist.
    public static synchronized DevNetParams get(String devNetName, String sporkAddress, int defaultPort, String [] dnsSeeds) {
        if (instances == null) {
            instances = new HashMap<String, DevNetParams>(1);
        }

        if(!instances.containsKey("devnet-" + devNetName)) {
            DevNetParams instance = new DevNetParams(devNetName, sporkAddress, defaultPort, dnsSeeds);
            instances.put("devnet-" + devNetName, instance);
            return instance;
        } else {
            return instances.get("devnet-" + devNetName);
        }
    }

    public static synchronized DevNetParams get(String devNetName) {
        if(!instances.containsKey("devnet-" + devNetName)) {
            return null;
        } else {
            return instances.get("devnet-" + devNetName);
        }
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_DEVNET;
    }

    @Override
    public void checkDifficultyTransitions_BTC(final StoredBlock storedPrev, final Block nextBlock,
                                               final BlockStore blockStore) throws VerificationException, BlockStoreException {

        if(powNoRetargeting)
            return;

        Block prev = storedPrev.getHeader();

        // Is this supposed to be a difficulty transition point?
        if (!isDifficultyTransitionPoint(storedPrev)) {

            if(powAllowMinimumDifficulty) {

                // On non-difficulty transition points, easy
                // blocks are allowed if there has been a span of 5 minutes without one.
                final long timeDelta = nextBlock.getTimeSeconds() - prev.getTimeSeconds();
                // There is an integer underflow bug in bitcoin-qt that means mindiff blocks are accepted when time
                // goes backwards.
                if (timeDelta <= NetworkParameters.TARGET_SPACING * 2) {
                    // Walk backwards until we find a block that doesn't have the easiest proof of work, then check
                    // that difficulty is equal to that one.
                    StoredBlock cursor = storedPrev;
                    while (!cursor.getHeader().equals(getGenesisBlock()) &&
                            cursor.getHeight() % getInterval() != 0 &&
                            cursor.getHeader().getDifficultyTargetAsInteger().equals(getMaxTarget()))
                        cursor = cursor.getPrev(blockStore);
                    BigInteger cursorTarget = cursor.getHeader().getDifficultyTargetAsInteger();
                    BigInteger newTarget = nextBlock.getDifficultyTargetAsInteger();
                    if (!cursorTarget.equals(newTarget))
                        throw new VerificationException("Testnet block transition that is not allowed: " +
                                Long.toHexString(cursor.getHeader().getDifficultyTarget()) + " vs " +
                                Long.toHexString(nextBlock.getDifficultyTarget()));
                } else {
                    verifyDifficulty(storedPrev, nextBlock, getMaxTarget());
                }
                return;
            }
            // No ... so check the difficulty didn't actually change.
            if (nextBlock.getDifficultyTarget() != prev.getDifficultyTarget())
                throw new VerificationException("Unexpected change in difficulty at height " + storedPrev.getHeight() +
                        ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
                        Long.toHexString(prev.getDifficultyTarget()));
            return;
        }

        // We need to find a block far back in the chain. It's OK that this is expensive because it only occurs every
        // two weeks after the initial block chain download.
        final Stopwatch watch = Stopwatch.createStarted();
        StoredBlock cursor = null;
        Sha256Hash hash = prev.getHash();
        for (int i = 0; i < this.getInterval(); i++) {
            cursor = blockStore.get(hash);
            if (cursor == null) {
                // This should never happen. If it does, it means we are following an incorrect or busted chain.
                throw new VerificationException(
                        "Difficulty transition point but we did not find a way back to the genesis block.");
            }
            hash = cursor.getHeader().getPrevBlockHash();
        }
        watch.stop();
        if (watch.elapsed(TimeUnit.MILLISECONDS) > 50)
            log.info("Difficulty transition traversal took {}", watch);

        Block blockIntervalAgo = cursor.getHeader();
        int timespan = (int) (prev.getTimeSeconds() - blockIntervalAgo.getTimeSeconds());
        // Limit the adjustment step.
        final int targetTimespan = this.getTargetTimespan();
        if (timespan < targetTimespan / 4)
            timespan = targetTimespan / 4;
        if (timespan > targetTimespan * 4)
            timespan = targetTimespan * 4;
        BigInteger newTarget = Utils.decodeCompactBits(prev.getDifficultyTarget());
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));

        if(newTarget.compareTo(maxUint256) > 0)
            newTarget = newTarget.and(maxUint256);

        newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));

        verifyDifficulty(storedPrev, nextBlock, newTarget);
    }

    public void DarkGravityWave(StoredBlock storedPrev, Block nextBlock,
                                final BlockStore blockStore) throws VerificationException {
        /* current difficulty formula, darkcoin - DarkGravity v3, written by Evan Duffield - evan@darkcoin.io */
        long pastBlocks = 24;

        if (storedPrev == null || storedPrev.getHeight() == 0 || storedPrev.getHeight() < pastBlocks) {
            verifyDifficulty(storedPrev, nextBlock, getMaxTarget());
            return;
        }

        if(powAllowMinimumDifficulty &&
                (devnetGenesisBlock == null && storedPrev.getChainWork().compareTo(new BigInteger(Utils.HEX.decode("000000000000000000000000000000000000000000000000003e9ccfe0e03e01"))) >= 0) ||
                devnetGenesisBlock != null)
        {
            if (storedPrev.getChainWork().compareTo(new BigInteger(Utils.HEX.decode("000000000000000000000000000000000000000000000000003ff00000000000"))) >= 0 ||
                    devnetGenesisBlock != null) {
                // recent block is more than 2 hours old
                if (nextBlock.getTimeSeconds() > storedPrev.getHeader().getTimeSeconds() + 2 * 60 * 60) {
                    verifyDifficulty(storedPrev, nextBlock, getMaxTarget());
                    return;
                }
                // recent block is more than 10 minutes old
                if (nextBlock.getTimeSeconds() > storedPrev.getHeader().getTimeSeconds() + NetworkParameters.TARGET_SPACING*4) {
                    BigInteger newTarget = storedPrev.getHeader().getDifficultyTargetAsInteger().multiply(BigInteger.valueOf(10));
                    verifyDifficulty(storedPrev, nextBlock, newTarget);
                    return;
                }
            } else {
                // old stuff
                if(nextBlock.getTimeSeconds() > storedPrev.getHeader().getTimeSeconds() + NetworkParameters.TARGET_SPACING*2) {
                    verifyDifficulty(storedPrev, nextBlock, getMaxTarget());
                    return;
                }
            }
        }
        StoredBlock cursor = storedPrev;
        BigInteger pastTargetAverage = BigInteger.ZERO;
        for(int countBlocks = 1; countBlocks <= pastBlocks; countBlocks++) {
            BigInteger target = cursor.getHeader().getDifficultyTargetAsInteger();
            if(countBlocks == 1) {
                pastTargetAverage = target;
            } else {
                BigInteger product = pastTargetAverage.multiply(BigInteger.valueOf(countBlocks));
                if(product.compareTo(maxUint256) > 0)
                    product = product.and(maxUint256);

                BigInteger numerator = product.add(target);
                if(numerator.compareTo(maxUint256) > 0)
                    numerator = numerator.and(maxUint256);

                pastTargetAverage = numerator.divide(BigInteger.valueOf(countBlocks+1));
            }
            if(countBlocks != pastBlocks) {
                try {
                    cursor = cursor.getPrev(blockStore);
                    if(cursor == null) {
                        //when using checkpoints, the previous block will not exist until 24 blocks are in the store.
                        return;
                    }
                } catch (BlockStoreException x) {
                    //when using checkpoints, the previous block will not exist until 24 blocks are in the store.
                    return;
                }
            }
        }


        BigInteger newTarget = pastTargetAverage;

        long timespan = storedPrev.getHeader().getTimeSeconds() - cursor.getHeader().getTimeSeconds();
        long targetTimespan = pastBlocks*TARGET_SPACING;

        if (timespan < targetTimespan/3)
            timespan = targetTimespan/3;
        if (timespan > targetTimespan*3)
            timespan = targetTimespan*3;

        // Retarget
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
        if(newTarget.compareTo(maxUint256) > 0)
            newTarget = newTarget.and(maxUint256);
        newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));
        verifyDifficulty(storedPrev, nextBlock, newTarget);

    }

    @Override
    public int getProtocolVersionNum(ProtocolVersion version) {
        switch(version) {
            case MINIMUM:
            case CURRENT:
            case BLOOM_FILTER:
                return protocolVersion;
        }
        return super.getProtocolVersionNum(version);
    }
}
