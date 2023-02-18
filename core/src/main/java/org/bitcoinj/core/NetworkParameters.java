/*
 * Copyright 2011 Google Inc.
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

package org.bitcoinj.core;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import org.bitcoinj.net.discovery.*;
import org.bitcoinj.params.*;
import org.bitcoinj.script.*;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import org.bitcoinj.utils.MonetaryFormat;

import javax.annotation.*;
import java.io.*;
import java.math.*;
import java.util.*;
import org.bitcoinj.quorums.LLMQParameters;


import static org.bitcoinj.core.Coin.*;
import org.bitcoinj.utils.VersionTally;

/**
 * <p>NetworkParameters contains the data needed for working with an instantiation of a Bitcoin chain.</p>
 *
 * <p>This is an abstract class, concrete instantiations can be found in the params package. There are four:
 * one for the main network ({@link MainNetParams}), one for the public test network, and two others that are
 * intended for unit testing and local app development purposes. Although this class contains some aliases for
 * them, you are encouraged to call the static get() methods on each specific params class directly.</p>
 */
public abstract class NetworkParameters {
    /**
     * The alert signing key originally owned by Satoshi, and now passed on to Gavin along with a few others.
     */
    @Deprecated
    public static final byte[] SATOSHI_KEY = Utils.HEX.decode("048240a8748a80a286b270ba126705ced4f2ce5a7847b3610ea3c06513150dade2a8512ed5ea86320824683fc0818f0ac019214973e677acd1244f6d0571fc5103");

    /** The string returned by getId() for the main, production network where people trade things. */
    public static final String ID_MAINNET = "org.darkcoin.production";
    /** The string returned by getId() for the testnet. */

    public static final String ID_TESTNET = "org.darkcoin.test";
    /** The string returned by getId() for the devnet. */
    public static final String ID_DEVNET = "org.dash.dev";
    /** Unit test network. */
    public static final String ID_UNITTESTNET = "com.google.darkcoin.unittest";
    /** The string returned by getId() for regtest mode. */
    public static final String ID_REGTEST = "org.bitcoin.regtest";

    /** The string used by the payment protocol to represent the main net. */
    public static final String PAYMENT_PROTOCOL_ID_MAINNET = "main";
    /** The string used by the payment protocol to represent the test net. */
    public static final String PAYMENT_PROTOCOL_ID_TESTNET = "test";
    /** The string used by the payment protocol to represent the devnet. */
    public static final String PAYMENT_PROTOCOL_ID_DEVNET = "dev";
    /** The string used by the payment protocol to represent unit testing (note that this is non-standard). */
    public static final String PAYMENT_PROTOCOL_ID_UNIT_TESTS = "unittest";
    public static final String PAYMENT_PROTOCOL_ID_REGTEST = "regtest";

    // TODO: Seed nodes should be here as well.

    protected Block genesisBlock;
    protected Block devnetGenesisBlock;
    protected String devNetName;
    protected BigInteger maxTarget;
    protected int port;
    protected long packetMagic;  // Indicates message origin network and is used to seek to the next message when stream state is unknown.
    protected int addressHeader;
    protected int p2shHeader;
    protected int dumpedPrivateKeyHeader;
    protected String segwitAddressHrp;
    protected int interval;
    protected int targetTimespan;
    @Deprecated
    protected byte[] alertSigningKey;
    protected int bip32HeaderP2PKHpub;
    protected int bip32HeaderP2PKHpriv;
    protected int bip32HeaderP2WPKHpub;
    protected int bip32HeaderP2WPKHpriv;
    protected int dip14HeaderP2PKHpub;
    protected int dip14HeaderP2PKHpriv;

    /** Used to check majorities for block version upgrade */
    protected int majorityEnforceBlockUpgrade;
    protected int majorityRejectBlockOutdated;
    protected int majorityWindow;

    /** Use to check for BIP34 upgrade */
    protected int BIP34Height;

    /** Use to check for BIP65 upgrade */
    protected int BIP65Height;

    /** Use to check for BIP65 upgrade */
    protected int BIP66Height;

    /** Used to check for DIP0001 upgrade */
    protected int DIP0001BlockHeight;
    protected boolean DIP0001ActiveAtTip = false;

    /** Used to check for DIP0003 upgrade and DETERMINISTIC_MNS_ENABLED */
    protected int DIP0003BlockHeight;
    protected int deterministicMasternodesEnabledHeight;
    protected boolean deterministicMasternodesEnabled = false;

    /** Used to check for DIP0008 upgrade */
    protected int DIP0008BlockHeight;

    /** Used to check for DIP0008 upgrade */
    protected int DIP0024BlockHeight;

    protected boolean isDIP24Only = false;

    /**
     * See getId(). This may be null for old deserialized wallets. In that case we derive it heuristically
     * by looking at the port number.
     */
    protected String id;

    /**
     * The depth of blocks required for a coinbase transaction to be spendable.
     */
    protected int spendableCoinbaseDepth;
    protected int subsidyDecreaseBlockCount;
    protected int budgetPaymentsStartBlock;
    protected int budgetPaymentsCycleBlocks;
    protected int budgetPaymentsWindowBlocks;

    protected String[] dnsSeeds;
    protected int[] addrSeeds;
    protected HttpDiscovery.Details[] httpSeeds = {};
    protected Map<Integer, Sha256Hash> checkpoints = new HashMap<>();
    protected volatile transient MessageSerializer defaultSerializer = null;




    //Dash Extra Parameters
    protected String strSporkAddress;
    protected int minSporkKeys;

    protected long fulfilledRequestExpireTime;
    protected long masternodeMinimumConfirmations;

    public long getFulfilledRequestExpireTime() { return fulfilledRequestExpireTime; }
    public long getMasternodeMinimumConfirmations() { return masternodeMinimumConfirmations; }



    public String getSporkAddress() {
        return strSporkAddress;
    }

    public int getMinSporkKeys() {
        return minSporkKeys;
    }

    protected NetworkParameters() {
        alertSigningKey = SATOSHI_KEY;
        genesisBlock = createGenesis(this);
    }

    /*
     * Build the genesis block. Note that the output of its generation
     * transaction cannot be spent since it did not originally exist in the
     * database.
     *
     * CBlock(hash=00000ffd590b14, ver=1, hashPrevBlock=00000000000000, hashMerkleRoot=e0028e, nTime=1390095618, nBits=1e0ffff0, nNonce=28917698, vtx=1)
     *   CTransaction(hash=e0028e, ver=1, vin.size=1, vout.size=1, nLockTime=0)
     *     CTxIn(COutPoint(000000, -1), coinbase 04ffff001d01044c5957697265642030392f4a616e2f3230313420546865204772616e64204578706572696d656e7420476f6573204c6976653a204f76657273746f636b2e636f6d204973204e6f7720416363657074696e6720426974636f696e73)
     *     CTxOut(nValue=50.00000000, scriptPubKey=0xA9037BAC7050C479B121CF)
     *   vMerkleTree: e0028e
     *
     * reference: https://github.com/dashpay/dash/blob/master/src/chainparams.cpp#L65-L81
     */

    // A script the following message:
    //"LYWired 09/Jan/2014 The Grand Experiment Goes Live: Overstock.com Is Now Accepting Bitcoins"
    private static final String genesisTxInputScriptBytes = "04ffff001d01044c5957697265642030392f4a616e2f3230313420546865204772616e64204578706572696d656e7420476f6573204c6976653a204f76657273746f636b2e636f6d204973204e6f7720416363657074696e6720426974636f696e73";
    //
    //
    private static final String genesisTxScriptPubKeyBytes = "040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9";

    private static Block createGenesis(NetworkParameters n) {
        Block genesisBlock = new Block(n, Block.BLOCK_VERSION_GENESIS);
        Transaction t = new Transaction(n);
        try {
            byte[] bytes = Utils.HEX.decode(genesisTxInputScriptBytes);

            t.addInput(new TransactionInput(n, t, bytes));
            ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
            Script.writeBytes(scriptPubKeyBytes, Utils.HEX.decode(genesisTxScriptPubKeyBytes));

            scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
            t.addOutput(new TransactionOutput(n, t, Coin.valueOf(50, 0), scriptPubKeyBytes.toByteArray()));
        } catch (Exception e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
        genesisBlock.addTransaction(t);
        return genesisBlock;
    }

    protected static Block findDevnetGenesis(NetworkParameters n, String devNetName, Block genesisBlock, Coin reward) {
        assert (!devNetName.isEmpty());

        Block devNetGenesisBlock = createDevNetGenesisBlock(n, genesisBlock.getHash(), devNetName, genesisBlock.getTimeSeconds() + 1, 0, genesisBlock.getDifficultyTarget(), reward);
        devNetGenesisBlock.solve();

        return devNetGenesisBlock;
    }

    private static Block createDevNetGenesisBlock(NetworkParameters n, Sha256Hash prevHash, String devNetName, long time, int nonce, long diffTarget, Coin reward) {
        assert (!devNetName.isEmpty());
        Transaction t = new Transaction(n);
        Block devNetGenesis = new Block(n, 4);
        try {
            // A script containing the difficulty bits and the following message:
            //
            //   coin dependent
            ScriptBuilder builder = new ScriptBuilder();
            Script inputScript = builder.number(1).data(devNetName.getBytes()).build();
            t.addInput(new TransactionInput(n, t, inputScript.getProgram()));

            builder = new ScriptBuilder();
            Script outputScript = builder.op(ScriptOpCodes.OP_RETURN).build();
            t.addOutput(new TransactionOutput(n, t, reward, outputScript.getProgram()));
        } catch (Exception e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
        devNetGenesis.addTransaction(t);
        devNetGenesis.setPrevBlockHash(prevHash);
        devNetGenesis.setTime(time);
        devNetGenesis.setDifficultyTarget(diffTarget);
        devNetGenesis.setNonce(nonce);
        return devNetGenesis;
    }

    public static final int TARGET_TIMESPAN = (24 * 60 * 60); // 24 hours difficulty adjustment before KGW and DGW
    public static final int TARGET_SPACING = (int)(2.5 * 60); // 2.5 minutes per block
    public static final int INTERVAL = TARGET_TIMESPAN / TARGET_SPACING; // 576 blocks before diff adjustment pre KGW
    
    /**
     * Blocks with a timestamp after this should enforce BIP 16, aka "Pay to script hash". This BIP changed the
     * network rules in a soft-forking manner, that is, blocks that don't follow the rules are accepted but not
     * mined upon and thus will be quickly re-orged out as long as the majority are enforcing the rule.
     */
    public static final int BIP16_ENFORCE_TIME = 1333238400;
    
    /**
     * The maximum number of coins to be generated
     */
    public static final long MAX_COINS = 22000000;

    /**
     * The maximum money to be generated
     */

    public static final Coin MAX_MONEY = COIN.multiply(MAX_COINS);

    /**
     * A Java package style string acting as unique ID for these parameters
     */
    public String getId() {
        return id;
    }

    public abstract String getPaymentProtocolId();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return getId().equals(((NetworkParameters)o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    /** Returns the network parameters for the given string ID or NULL if not recognized. */
    @Nullable
    public static NetworkParameters fromID(String id) {
        if (id.equals(ID_MAINNET)) {
            return MainNetParams.get();
        } else if (id.equals(ID_TESTNET)) {
            return TestNet3Params.get();
        } else if (id.equals(ID_UNITTESTNET)) {
            return UnitTestParams.get();
        } else if (id.equals(ID_REGTEST)) {
            return RegTestParams.get();
        } else if (id.contains(ID_DEVNET)) {
            return DevNetParams.get(id.substring(id.lastIndexOf('.')+1));
        } else {
            return null;
        }
    }

    /** Returns the network parameters for the given string paymentProtocolID or NULL if not recognized. */
    @Nullable
    public static NetworkParameters fromPmtProtocolID(String pmtProtocolId) {
        if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_MAINNET)) {
            return MainNetParams.get();
        } else if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_TESTNET)) {
            return TestNet3Params.get();
        } else if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_UNIT_TESTS)) {
            return UnitTestParams.get();
        } else if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_REGTEST)) {
            return RegTestParams.get();
        } else {
            return null;
        }
    }

    public int getSpendableCoinbaseDepth() {
        return spendableCoinbaseDepth;
    }

    /**
     * Throws an exception if the block's difficulty is not correct.
     *
     * @throws VerificationException if the block's difficulty is not correct.
     */
    public abstract void checkDifficultyTransitions(StoredBlock storedPrev, Block next, final BlockStore blockStore) throws VerificationException, BlockStoreException;

    /**
     * Returns true if the block height is either not a checkpoint, or is a checkpoint and the hash matches.
     */
    public boolean passesCheckpoint(int height, Sha256Hash hash) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash == null || checkpointHash.equals(hash);
    }

    /**
     * Returns true if the given height has a recorded checkpoint.
     */
    public boolean isCheckpoint(int height) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash != null;
    }

    public int getSubsidyDecreaseBlockCount() {
        return subsidyDecreaseBlockCount;
    }

    public int getBudgetPaymentsStartBlock() {
        return budgetPaymentsStartBlock;
    }

    public int getBudgetPaymentsCycleBlocks() {
        return budgetPaymentsCycleBlocks;
    }

    public int getBudgetPaymentsWindowBlocks() {
        return budgetPaymentsWindowBlocks;
    }

    /** Returns DNS names that when resolved, give IP addresses of active peers. */
    public String[] getDnsSeeds() {
        return dnsSeeds;
    }

    /** Returns IP address of active peers. */
    public int[] getAddrSeeds() {
        return addrSeeds;
    }

    /** Returns discovery objects for seeds implementing the Cartographer protocol. See {@link HttpDiscovery} for more info. */
    public HttpDiscovery.Details[] getHttpSeeds() {
        return httpSeeds;
    }

    /**
     * <p>Genesis block for this chain.</p>
     *
     * <p>The first block in every chain is a well known constant shared between all Bitcoin implementations. For a
     * block to be valid, it must be eventually possible to work backwards to the genesis block by following the
     * prevBlockHash pointers in the block headers.</p>
     *
     * <p>The genesis blocks for both test and main networks contain the timestamp of when they were created,
     * and a message in the coinbase transaction. It says, <i>"The Times 03/Jan/2009 Chancellor on brink of second
     * bailout for banks"</i>.</p>
     */
    public Block getGenesisBlock() {
        return genesisBlock;
    }

    /**
     * <p>DevNet Genesis block for this chain.</p>
     *
     * <p>The second block in a devnet chain is a known constant shared between all Dash DevNet implimentations for a
     * particular devnetname. For a block to be valid, it must be eventually possible to work backwards to the devnet block
     * and to the genesis block by following the prevBlockHash pointers in the block headers.</p>
     */
    public Block getDevNetGenesisBlock() {
        return devnetGenesisBlock;
    }

    /**
     * Gets the name of the devnet.  It should never be called for non-devnets.
     *
     * @return the name of the devnet
     */
    public String getDevNetName() {
        return devNetName;
    }

    /** Default TCP port on which to connect to nodes. */
    public int getPort() {
        return port;
    }

    /** The header bytes that identify the start of a packet on this network. */
    public long getPacketMagic() {
        return packetMagic;
    }

    /**
     * First byte of a base58 encoded address. See {@link Address}. This is the same as acceptableAddressCodes[0] and
     * is the one used for "normal" addresses. Other types of address may be encountered with version codes found in
     * the acceptableAddressCodes array.
     */
    public int getAddressHeader() {
        return addressHeader;
    }

    /**
     * First byte of a base58 encoded P2SH address.  P2SH addresses are defined as part of BIP0013.
     */
    public int getP2SHHeader() {
        return p2shHeader;
    }

    /** First byte of a base58 encoded dumped private key. See {@link DumpedPrivateKey}. */
    public int getDumpedPrivateKeyHeader() {
        return dumpedPrivateKeyHeader;
    }

    /** Human readable part of bech32 encoded segwit address. */
    public String getSegwitAddressHrp() {
        return segwitAddressHrp;
    }

    /**
     * How much time in seconds is supposed to pass between "interval" blocks. If the actual elapsed time is
     * significantly different from this value, the network difficulty formula will produce a different value. Both
     * test and main Bitcoin networks use 2 weeks (1209600 seconds).
     */
    public int getTargetTimespan() {
        return targetTimespan;
    }

    /**
     * If we are running in testnet-in-a-box mode, we allow connections to nodes with 0 non-genesis blocks.
     */
    public boolean allowEmptyPeerChain() {
        return true;
    }

    /** How many blocks pass between difficulty adjustment periods. Bitcoin standardises this to be 2015. */
    public int getInterval() {
        return interval;
    }

    /** Maximum target represents the easiest allowable proof of work. */
    public BigInteger getMaxTarget() {
        return maxTarget;
    }

    /**
     * The key used to sign {@link AlertMessage}s. You can use {@link ECKey#verify(byte[], byte[], byte[])} to verify
     * signatures using it.
     */
    @Deprecated
    public byte[] getAlertSigningKey() {
        return alertSigningKey;
    }

    /** Returns the 4 byte header for BIP32 wallet P2PKH - public key part. */
    public int getBip32HeaderP2PKHpub() {
        return bip32HeaderP2PKHpub;
    }

    /** Returns the 4 byte header for BIP32 wallet P2PKH - private key part. */
    public int getBip32HeaderP2PKHpriv() {
        return bip32HeaderP2PKHpriv;
    }

    /** Returns the 4 byte header for BIP32 wallet P2WPKH - public key part. */
    public int getBip32HeaderP2WPKHpub() {
        return bip32HeaderP2WPKHpub;
    }

    /** Returns the 4 byte header for BIP32 wallet P2WPKH - private key part. */
    public int getBip32HeaderP2WPKHpriv() {
        return bip32HeaderP2WPKHpriv;
    }

    /** Returns the 4 byte header for DIP14 wallet P2PKH - public key part. */
    public int getDip14HeaderP2PKHpub() {
        return dip14HeaderP2PKHpub;
    }

    /** Returns the 4 byte header for DIP14 wallet P2PKH - private key part. */
    public int getDip14HeaderP2PKHpriv() {
        return dip14HeaderP2PKHpriv;
    }
    /**
     * Returns the number of coins that will be produced in total, on this
     * network. Where not applicable, a very large number of coins is returned
     * instead (i.e. the main coin issue for Dogecoin).
     */
    public abstract Coin getMaxMoney();

    /**
     * Any standard (ie P2PKH) output smaller than this value will
     * most likely be rejected by the network.
     */
    public abstract Coin getMinNonDustOutput();

    /**
     * The monetary object for this currency.
     */
    public abstract MonetaryFormat getMonetaryFormat();

    /**
     * Scheme part for URIs, for example "bitcoin".
     */
    public abstract String getUriScheme();

    /**
     * Returns whether this network has a maximum number of coins (finite supply) or
     * not. Always returns true for Bitcoin, but exists to be overridden for other
     * networks.
     */
    public abstract boolean hasMaxMoney();

    /**
     * Return the default serializer for this network. This is a shared serializer.
     * @return the default serializer for this network.
     */
    public final MessageSerializer getDefaultSerializer() {
        // Construct a default serializer if we don't have one
        if (null == this.defaultSerializer) {
            // Don't grab a lock unless we absolutely need it
            synchronized(this) {
                // Now we have a lock, double check there's still no serializer
                // and create one if so.
                if (null == this.defaultSerializer) {
                    // As the serializers are intended to be immutable, creating
                    // two due to a race condition should not be a problem, however
                    // to be safe we ensure only one exists for each network.
                    this.defaultSerializer = getSerializer(false);
                }
            }
        }
        return defaultSerializer;
    }

    /**
     * Construct and return a custom serializer.
     */
    public abstract BitcoinSerializer getSerializer(boolean parseRetain);

    /**
     * The number of blocks in the last {@link #getMajorityWindow()} blocks
     * at which to trigger a notice to the user to upgrade their client, where
     * the client does not understand those blocks.
     */
    public int getMajorityEnforceBlockUpgrade() {
        return majorityEnforceBlockUpgrade;
    }

    /**
     * The number of blocks in the last {@link #getMajorityWindow()} blocks
     * at which to enforce the requirement that all new blocks are of the
     * newer type (i.e. outdated blocks are rejected).
     */
    public int getMajorityRejectBlockOutdated() {
        return majorityRejectBlockOutdated;
    }

    /**
     * The sampling window from which the version numbers of blocks are taken
     * in order to determine if a new block version is now the majority.
     */
    public int getMajorityWindow() {
        return majorityWindow;
    }

    /**
     * The flags indicating which block validation tests should be applied to
     * the given block. Enables support for alternative blockchains which enable
     * tests based on different criteria.
     * 
     * @param block block to determine flags for.
     * @param height height of the block, if known, null otherwise. Returned
     * tests should be a safe subset if block height is unknown.
     */
    public EnumSet<Block.VerifyFlag> getBlockVerificationFlags(final Block block,
            final VersionTally tally, final Integer height) {
        final EnumSet<Block.VerifyFlag> flags = EnumSet.noneOf(Block.VerifyFlag.class);

        if (block.isBIP34()) {
            final Integer count = tally.getCountAtOrAbove(Block.BLOCK_VERSION_BIP34);
            if (null != count && count >= getMajorityEnforceBlockUpgrade()) {
                flags.add(Block.VerifyFlag.HEIGHT_IN_COINBASE);
            }
        }
        return flags;
    }

    /**
     * The flags indicating which script validation tests should be applied to
     * the given transaction. Enables support for alternative blockchains which enable
     * tests based on different criteria.
     *
     * @param block block the transaction belongs to.
     * @param transaction to determine flags for.
     * @param height height of the block, if known, null otherwise. Returned
     * tests should be a safe subset if block height is unknown.
     */
    public EnumSet<Script.VerifyFlag> getTransactionVerificationFlags(final Block block,
            final Transaction transaction, final VersionTally tally, final Integer height) {
        final EnumSet<Script.VerifyFlag> verifyFlags = EnumSet.noneOf(Script.VerifyFlag.class);
        if (block.getTimeSeconds() >= NetworkParameters.BIP16_ENFORCE_TIME)
            verifyFlags.add(Script.VerifyFlag.P2SH);

        // Start enforcing CHECKLOCKTIMEVERIFY, (BIP65) for block.nVersion=4
        // blocks, when 75% of the network has upgraded:
        if (block.getVersion() >= Block.BLOCK_VERSION_BIP65 &&
            tally.getCountAtOrAbove(Block.BLOCK_VERSION_BIP65) > this.getMajorityEnforceBlockUpgrade()) {
            verifyFlags.add(Script.VerifyFlag.CHECKLOCKTIMEVERIFY);
        }

        return verifyFlags;
    }

    public abstract int getProtocolVersionNum(final ProtocolVersion version);

    public static enum ProtocolVersion {
        MINIMUM(70220),
        PONG(60001),
        BLOOM_FILTER(MINIMUM.getBitcoinProtocolVersion()),
        BLOOM_FILTER_BIP111(MINIMUM.getBitcoinProtocolVersion()+1),
        @Deprecated
        DMN_LIST(70214),
        CORE17(70219),
        ISDLOCK(70220),
        BLS_LEGACY(70220),  // used internally by DashJ only
        BLS_BASIC(70225),   // used internally by DashJ only
        GOVSCRIPT(70221),
        ADDRV2(70223),
        COINJOIN_SU(70224),
        BLS_SCHEME(70225),
        CURRENT(70225);

        private final int bitcoinProtocol;

        ProtocolVersion(final int bitcoinProtocol) {
            this.bitcoinProtocol = bitcoinProtocol;
        }

        public int getBitcoinProtocolVersion() {
            return bitcoinProtocol;
        }
    }

    //DASH Specific
    public boolean isDIP0001ActiveAtTip() { return DIP0001ActiveAtTip; }
    public void setDIPActiveAtTip(boolean active) { DIP0001ActiveAtTip = active; }
    public int getDIP0001BlockHeight() { return DIP0001BlockHeight; }

    protected int superblockStartBlock;
    protected int superblockCycle; // in blocks

    protected boolean supportsV18 = true;

    /**
     * Getter for property 'nGovernanceMinQuorum'.
     *
     * @return Value for property 'nGovernanceMinQuorum'.
     */
    public int getGovernanceMinQuorum() {
        return nGovernanceMinQuorum;
    }

    /**
     * Getter for property 'nGovernanceFilterElements'.
     *
     * @return Value for property 'nGovernanceFilterElements'.
     */
    public int getGovernanceFilterElements() {
        return nGovernanceFilterElements;
    }


    protected int nGovernanceMinQuorum; // Min absolute vote count to trigger an action
    protected int nGovernanceFilterElements;

    public int getSuperblockCycle() {
        return superblockCycle;
    }

    public int getSuperblockStartBlock() {
        return superblockStartBlock;
    }

    public boolean isSupportingV18() {
        return supportsV18;
    }

    public void setSupportsV18(boolean supportsV18) {
        this.supportsV18 = supportsV18;
    }

    protected int instantSendConfirmationsRequired;
    protected int instantSendKeepLock;

    public int getInstantSendConfirmationsRequired() {
        return instantSendConfirmationsRequired;
    }

    public int getInstantSendKeepLock() {
        return instantSendKeepLock;
    }

    public int getDIP0003BlockHeight() {
        return DIP0003BlockHeight;
    }

    public int getDeterministicMasternodesEnabledHeight() {
        return deterministicMasternodesEnabledHeight;
    }

    public boolean isDeterministicMasternodesEnabled() {
        return deterministicMasternodesEnabled;
    }

    //LLMQ parameters
    protected HashMap<LLMQParameters.LLMQType, LLMQParameters> llmqs = new HashMap<>();
    protected LLMQParameters.LLMQType llmqChainLocks;
    protected LLMQParameters.LLMQType llmqForInstantSend;
    protected LLMQParameters.LLMQType llmqTypePlatform;
    protected LLMQParameters.LLMQType llmqTypeDIP0024InstantSend;
    protected LLMQParameters.LLMQType llmqTypeMnhf;

    public HashMap<LLMQParameters.LLMQType, LLMQParameters> getLlmqs() {
        return llmqs;
    }

    public LLMQParameters.LLMQType getLlmqChainLocks() {
        return llmqChainLocks;
    }

    public LLMQParameters.LLMQType getLlmqForInstantSend() {
        return llmqForInstantSend;
    }

    public LLMQParameters.LLMQType getLlmqDIP0024InstantSend() {
        return llmqTypeDIP0024InstantSend;
    }

    protected void addLLMQ(LLMQParameters.LLMQType type) {
        llmqs.put(type, LLMQParameters.fromType(type));
    }

    public int getDIP0008BlockHeight() {
        return DIP0008BlockHeight;
    }

    public int getDIP0024BlockHeight() {
        return DIP0024BlockHeight;
    }

    public boolean isDIP0024Active(StoredBlock block) {
        return block.getHeight() >= DIP0024BlockHeight;
    }

    public boolean isDIP0024Active(int height) {
        return height > DIP0024BlockHeight;
    }

    public void setDIP0024Active(int height) {
        System.out.println("DIP24 is now active");
        DIP0024BlockHeight = height;
    }

    @Deprecated
    public boolean isDIP24Only() {
        return isDIP24Only;
    }

    protected int basicBLSSchemeActivationHeight = Integer.MAX_VALUE;
    public boolean isBasicBLSSchemeActive(int height) {
        return height >= basicBLSSchemeActivationHeight;
    }
    @VisibleForTesting
    public void setBasicBLSSchemeActivationHeight(int basicBLSSchemeActivationHeight) {
        this.basicBLSSchemeActivationHeight = basicBLSSchemeActivationHeight;
    }

    public int getBIP65Height() {
        return BIP65Height;
    }

    // Coin Type
    protected int coinType;

    public int getCoinType() {
        return coinType;
    }

    public String [] getDefaultMasternodeList() {
        return new String[0];
    }

    protected List<Sha256Hash> assumeValidQuorums = new ArrayList<>();

    public List<Sha256Hash> getAssumeValidQuorums() {
        return assumeValidQuorums;
    }

    public String getNetworkName() {
        switch (id) {
            case NetworkParameters.ID_MAINNET:
                return "mainnet";
            case NetworkParameters.ID_TESTNET:
                return "testnet";
            case NetworkParameters.ID_REGTEST:
                return "regtest";
            case NetworkParameters.ID_UNITTESTNET:
                return "unittest";
            default:
                if (id.startsWith(NetworkParameters.ID_DEVNET)) {
                    return getDevNetName();
                }
                return "invalid";
        }
    }
    /** the default value for dropping peers after broadcast */
    protected boolean dropPeersAfterBroadcast = true;

    public boolean getDropPeersAfterBroadcast() {
        return dropPeersAfterBroadcast;
    }

}
