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

    public DevNetParams(String devNetName, String sporkAddress, int defaultPort, String [] dnsSeeds) {
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
            return instances.get(devNetName);
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
        BigInteger maxUint256 = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);
        BigInteger newTarget = Utils.decodeCompactBits(prev.getDifficultyTarget());
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));

        if(newTarget.compareTo(maxUint256) > 0)
            newTarget = newTarget.and(maxUint256);

        newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));

        verifyDifficulty(storedPrev, nextBlock, newTarget);
    }
}
