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

import org.bitcoinj.core.CoinDefinition;
import static org.bitcoinj.core.Utils.HEX;

import java.math.BigInteger;
import java.util.Date;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
 * and testing of applications and new Bitcoin versions.
 */
public class TestNet3Params extends AbstractBitcoinNetParams {
    public TestNet3Params() {
        super();
        id = ID_TESTNET;

        // Genesis hash is

        packetMagic = CoinDefinition.testnetPacketMagic;
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;

        maxTarget = CoinDefinition.proofOfWorkLimit;//Utils.decodeCompactBits(0x1d00ffffL);
        port = CoinDefinition.TestPort;
        addressHeader = CoinDefinition.testnetAddressHeader;
        p2shHeader = CoinDefinition.testnetp2shHeader;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        dumpedPrivateKeyHeader = 128 + CoinDefinition.testnetAddressHeader;
        genesisBlock.setTime(CoinDefinition.testnetGenesisBlockTime);
        genesisBlock.setDifficultyTarget(CoinDefinition.testnetGenesisBlockDifficultyTarget);
        genesisBlock.setNonce(CoinDefinition.testnetGenesisBlockNonce);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = CoinDefinition.subsidyDecreaseBlockCount;
        String genesisHash = genesisBlock.getHashAsString();

        if(CoinDefinition.supportsTestNet)
            checkState(genesisHash.equals(CoinDefinition.testnetGenesisHash));
        alertSigningKey = HEX.decode(CoinDefinition.TESTNET_SATOSHI_KEY);

        dnsSeeds = CoinDefinition.testnetDnsSeeds;

        addrSeeds = null;
        bip32HeaderPub = 0x3a59ea14;
        bip32HeaderPriv = 0x3a58f342;

        strSporkKey = "046f78dcf911fbd61910136f7f0f8d90578f68d0b3ac973b5040fb7afb501b5939f39b108b0569dca71488f5bbf498d92e4d1194f6f941307ffd95f75e76869f0e";

     //   bip32HeaderPub = 0x043587CF;
     //   bip32HeaderPriv = 0x04358394;

        majorityEnforceBlockUpgrade = TestNet2Params.TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TestNet2Params.TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TestNet2Params.TESTNET_MAJORITY_WINDOW;

    }

    private static TestNet3Params instance;
    public static synchronized TestNet3Params get() {
        if (instance == null) {
            instance = new TestNet3Params();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_TESTNET;
    }

    // February 16th 2012
    private static final Date testnetDiffDate = new Date(1329264000000L);

    @Override
    public void checkDifficultyTransitions(final StoredBlock storedPrev, final Block nextBlock,
        final BlockStore blockStore) throws VerificationException, BlockStoreException {
        if (!isDifficultyTransitionPoint(storedPrev) && nextBlock.getTime().after(testnetDiffDate)) {
            Block prev = storedPrev.getHeader();

            // After 15th February 2012 the rules on the testnet change to avoid people running up the difficulty
            // and then leaving, making it too hard to mine a block. On non-difficulty transition points, easy
            // blocks are allowed if there has been a span of 20 minutes without one.
            final long timeDelta = nextBlock.getTimeSeconds() - prev.getTimeSeconds();
            // There is an integer underflow bug in bitcoin-qt that means mindiff blocks are accepted when time
            // goes backwards.
            if (timeDelta >= 0 && timeDelta <= NetworkParameters.TARGET_SPACING * 2) {
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
            }
        } else {
            super.checkDifficultyTransitions(storedPrev, nextBlock, blockStore);
        }
    }
}
