/*
 * Copyright 2020 Dash Core Group
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
        addressHeader = 140;
        p2shHeader = 19;
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1417713337L);
        genesisBlock.setDifficultyTarget(0x207fffff);
        genesisBlock.setNonce(1096447);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210240;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000008ca1832a4baf228eb1553c03d3a2c8e02399550dd6ea8d65cec3ef23d2e"));

        devnetGenesisBlock = findDevnetGenesis(this, this.devNetName, getGenesisBlock(), Coin.valueOf(50, 0));
        alertSigningKey = HEX.decode("04517d8a699cb43d3938d7b24faaff7cda448ca4ea267723ba614784de661949bf632d6304316b244646dea079735b9a6fc4af804efb4752075b9fe2245e14e412");

        this.dnsSeeds = dnsSeeds;

        checkpoints.put(    1, devnetGenesisBlock.getHash());

        addrSeeds = null;
        bip32HeaderP2PKHpub = 0x043587cf;
        bip32HeaderP2PKHpriv = 0x04358394;

        strSporkAddress = sporkAddress;
        budgetPaymentsStartBlock = 4100;
        budgetPaymentsCycleBlocks = 50;
        budgetPaymentsWindowBlocks = 10;

        majorityEnforceBlockUpgrade = TestNet3Params.TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TestNet3Params.TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TestNet3Params.TESTNET_MAJORITY_WINDOW;

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
        deterministicMasternodesEnabledHeight = 2;
        deterministicMasternodesEnabled = true;

        this.protocolVersion = protocolVersion;

        //LLMQ parameters
        llmqs = new HashMap<>(4);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_DEVNET);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_50_60);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_400_60);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_400_85);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_100_67);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_100_67;

        BIP34Height = 1;
        BIP65Height = 1;
        BIP66Height = 1;
        coinType = 1;
        minSporkKeys = 1;
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
        if (instances == null) {
            instances = new HashMap<>(1);
        }

        if(!instances.containsKey("devnet-" + devNetName)) {
            return null;
        } else {
            return instances.get("devnet-" + devNetName);
        }
    }

    public static synchronized void add(DevNetParams params) {
        if (instances == null)
            instances = new HashMap<>(1);
        instances.put(params.devNetName, params);
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_DEVNET;
    }

    @Override
    public void checkDifficultyTransitions_BTC(final StoredBlock storedPrev, final Block nextBlock,
                                               final BlockStore blockStore) throws VerificationException, BlockStoreException {

    }

    public void DarkGravityWave(StoredBlock storedPrev, Block nextBlock,
                                final BlockStore blockStore) throws VerificationException {

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

    void setLLMQChainLocks(LLMQParameters.LLMQType llmqChainLocks) {
        this.llmqChainLocks = llmqChainLocks;
    }

    void updateLLMQDevnetParams(int size, int threshold) {
        LLMQParameters llmqParameters = llmqs.get(LLMQParameters.LLMQType.LLMQ_DEVNET);
        llmqParameters.setSize(size);
        llmqParameters.setMinSize(threshold);
        llmqParameters.setThreshold(threshold);
        llmqParameters.setDkgBadVotesThreshold(threshold);
    }

    void updateLLMQTestParams(int size, int threshold) {
        LLMQParameters llmqParameters = llmqs.get(LLMQParameters.LLMQType.LLMQ_TEST);
        llmqParameters.setSize(size);
        llmqParameters.setMinSize(threshold);
        llmqParameters.setThreshold(threshold);
        llmqParameters.setDkgBadVotesThreshold(threshold);
    }

}
