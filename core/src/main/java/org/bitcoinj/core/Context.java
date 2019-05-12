/*
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

import javax.annotation.Nullable;
import org.bitcoinj.core.listeners.BlockChainListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.evolution.EvolutionUserManager;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.governance.GovernanceManager;
import org.bitcoinj.governance.GovernanceTriggerManager;
import org.bitcoinj.governance.VoteConfidenceTable;
import org.bitcoinj.quorums.*;
import org.bitcoinj.store.FlatDB;
import org.bitcoinj.store.HashStore;
import org.slf4j.*;

import java.util.EnumSet;
import java.util.List;

import static com.google.common.base.Preconditions.*;

// TODO: Finish adding Context c'tors to all the different objects so we can start deprecating the versions that take NetworkParameters.
// TODO: Add a working directory notion to Context and make various subsystems that want to use files default to that directory (eg. Orchid, block stores, wallet, etc).
// TODO: Auto-register the block chain object here, and then use it in the (newly deprecated) TransactionConfidence.getDepthInBlocks() method: the new version should take an AbstractBlockChain specifically.
//       Also use the block chain object reference from the context in PeerGroup and remove the other constructors, as it's easy to forget to wire things up.
// TODO: Move Threading.USER_THREAD to here and leave behind just a source code stub. Allow different instantiations of the library to use different user threads.
// TODO: Keep a URI to where library internal data files can be found, to abstract over the lack of JAR files on Android.
// TODO: Stash anything else that resembles global library configuration in here and use it to clean up the rest of the API without breaking people.

/**
 * <p>The Context object holds various objects and pieces of configuration that are scoped to a specific instantiation of
 * bitcoinj for a specific network. You can get an instance of this class through calling {@link #get()}.</p>
 *
 * <p>Context is new in 0.13 and the library is currently in a transitional period: you should create a Context that
 * wraps your chosen network parameters before using the rest of the library. However if you don't, things will still
 * work as a Context will be created for you and stashed in thread local storage. The context is then propagated between
 * library created threads as needed. This automagical propagation and creation is a temporary mechanism: one day it
 * will be removed to avoid confusing edge cases that could occur if the developer does not fully understand it e.g.
 * in the case where multiple instances of the library are in use simultaneously.</p>
 */
public class Context {
    private static final Logger log = LoggerFactory.getLogger(Context.class);

    private TxConfidenceTable confidenceTable;
    private NetworkParameters params;
    private int eventHorizon = 100;
    private boolean ensureMinRequiredFee = true;
    private Coin feePerKb = Transaction.DEFAULT_TX_FEE;

    //Dash Specific
    private boolean liteMode = true;
    private boolean allowInstantX = true; //allow InstantX in litemode
    public PeerGroup peerGroup;
    public AbstractBlockChain blockChain;
    public SporkManager sporkManager;
    public MasternodeManager masternodeManager;
    public MasternodePayments masternodePayments;
    public MasternodeSync masternodeSync;
    public ActiveMasternode activeMasternode;
    public DarkSendPool darkSendPool;
    public InstantSend instantSend;
    public HashStore hashStore;
    public GovernanceManager governanceManager;
    public GovernanceTriggerManager triggerManager;
    public NetFullfilledRequestManager netFullfilledRequestManager;
    public EvolutionUserManager evoUserManager;
    public SimplifiedMasternodeListManager masternodeListManager;
    public static boolean fMasterNode = false;
    private VoteConfidenceTable voteConfidenceTable;
    private InstantSendDatabase instantSendDB;
    public InstantSendManager instantSendManager;
    public SigningManager signingManager;
    public QuorumManager quorumManager;
    private RecoveredSignaturesDatabase recoveredSigsDB;
    public ChainLocksHandler chainLockHandler;
    private LLMQBackgroundThread llmqBackgroundThread;

    /**
     * Creates a new context object. For now, this will be done for you by the framework. Eventually you will be
     * expected to do this yourself in the same manner as fetching a NetworkParameters object (at the start of your app).
     *
     * @param params The network parameters that will be associated with this context.
     */
    public Context(NetworkParameters params) {
        log.info("Creating bitcoinj {} context.", VersionMessage.BITCOINJ_VERSION);
        this.confidenceTable = new TxConfidenceTable();
        this.voteConfidenceTable = new VoteConfidenceTable();
        this.params = params;
        lastConstructed = this;
        // We may already have a context in our TLS slot. This can happen a lot during unit tests, so just ignore it.
        slot.set(this);
    }

    /**
     * Creates a new custom context object. This is mainly meant for unit tests for now.
     *
     * @param params The network parameters that will be associated with this context.
     * @param eventHorizon Number of blocks after which the library will delete data and be unable to always process reorgs (see {@link #getEventHorizon()}.
     * @param feePerKb The default fee per 1000 bytes of transaction data to pay when completing transactions. For details, see {@link SendRequest#feePerKb}.
     * @param ensureMinRequiredFee Whether to ensure the minimum required fee by default when completing transactions. For details, see {@link SendRequest#ensureMinRequiredFee}.
     */
    public Context(NetworkParameters params, int eventHorizon, Coin feePerKb, boolean ensureMinRequiredFee) {
        this(params);
        this.eventHorizon = eventHorizon;
        this.feePerKb = feePerKb;
        this.ensureMinRequiredFee = ensureMinRequiredFee;
    }

    private static volatile Context lastConstructed;
    private static boolean isStrictMode;
    private static final ThreadLocal<Context> slot = new ThreadLocal<Context>();

    /**
     * Returns the current context that is associated with the <b>calling thread</b>. BitcoinJ is an API that has thread
     * affinity: much like OpenGL it expects each thread that accesses it to have been configured with a global Context
     * object. This method returns that. Note that to help you develop, this method will <i>also</i> propagate whichever
     * context was created last onto the current thread, if it's missing. However it will print an error when doing so
     * because propagation of contexts is meant to be done manually: this is so two libraries or subsystems that
     * independently use bitcoinj (or possibly alt coin forks of it) can operate correctly.
     *
     * @throws java.lang.IllegalStateException if no context exists at all or if we are in strict mode and there is no context.
     */
    public static Context get() {
        Context tls = slot.get();
        if (tls == null) {
            if (isStrictMode) {
                log.error("Thread is missing a bitcoinj context.");
                log.error("You should use Context.propagate() or a ContextPropagatingThreadFactory.");
                throw new IllegalStateException("missing context");
            }
            if (lastConstructed == null)
                throw new IllegalStateException("You must construct a Context object before using bitcoinj!");
            slot.set(lastConstructed);
            log.error("Performing thread fixup: you are accessing bitcoinj via a thread that has not had any context set on it.");
            log.error("This error has been corrected for, but doing this makes your app less robust.");
            log.error("You should use Context.propagate() or a ContextPropagatingThreadFactory.");
            log.error("Please refer to the user guide for more information about this.");
            log.error("Thread name is {}.", Thread.currentThread().getName());
            // TODO: Actually write the user guide section about this.
            // TODO: If the above TODO makes it past the 0.13 release, kick Mike and tell him he sucks.
            return lastConstructed;
        } else {
            return tls;
        }
    }

    /**
     * Require that new threads use {@link #propagate(Context)} or {@link org.bitcoinj.utils.ContextPropagatingThreadFactory},
     * rather than using a heuristic for the desired context.
     */
    public static void enableStrictMode() {
        isStrictMode = true;
    }

    // A temporary internal shim designed to help us migrate internally in a way that doesn't wreck source compatibility.
    public static Context getOrCreate(NetworkParameters params) {
        Context context;
        try {
            context = get();
        } catch (IllegalStateException e) {
            log.warn("Implicitly creating context. This is a migration step and this message will eventually go away.");
            context = new Context(params);
            return context;
        }
        if (context.getParams() != params)
            throw new IllegalStateException("Context does not match implicit network context: " + context.getParams() + " vs " + params);
        return context;
    }

    /**
     * Sets the given context as the current thread context. You should use this if you create your own threads that
     * want to create core BitcoinJ objects. Generally, if a class can accept a Context in its constructor and might
     * be used (even indirectly) by a thread, you will want to call this first. Your task may be simplified by using
     * a {@link org.bitcoinj.utils.ContextPropagatingThreadFactory}.
     */
    public static void propagate(Context context) {
        slot.set(checkNotNull(context));
    }

    /**
     * Returns the {@link TxConfidenceTable} created by this context. The pool tracks advertised
     * and downloaded transactions so their confidence can be measured as a proportion of how many peers announced it.
     * With an un-tampered with internet connection, the more peers announce a transaction the more confidence you can
     * have that it's really valid.
     */
    public TxConfidenceTable getConfidenceTable() {
        return confidenceTable;
    }

    /**
     * Returns the {@link org.bitcoinj.core.NetworkParameters} specified when this context was (auto) created. The
     * network parameters defines various hard coded constants for a specific instance of a Bitcoin network, such as
     * main net, testnet, etc.
     */
    public NetworkParameters getParams() {
        return params;
    }

    /**
     * The event horizon is the number of blocks after which various bits of the library consider a transaction to be
     * so confirmed that it's safe to delete data. Re-orgs larger than the event horizon will not be correctly
     * processed, so the default value is high (100).
     */
    public int getEventHorizon() {
        return eventHorizon;
    }

    //
    // Dash Specific
    //
    private boolean initializedDash = false;
    public void initDash(boolean liteMode, boolean allowInstantX) {
        initDash(liteMode, allowInstantX, null);
    }

    public void initDash(boolean liteMode, boolean allowInstantX, @Nullable EnumSet<MasternodeSync.SYNC_FLAGS> syncFlags) {
        this.liteMode = liteMode;//liteMode; --TODO: currently only lite mode has been tested and works with 12.1
        this.allowInstantX = allowInstantX;

        //Dash Specific
        sporkManager = new SporkManager(this);

        masternodePayments = new MasternodePayments(this);
        masternodeSync = syncFlags != null ? new MasternodeSync(this, syncFlags) : new MasternodeSync(this);
        activeMasternode = new ActiveMasternode(this);
        darkSendPool = new DarkSendPool(this);
        instantSend = new InstantSend(this);
        masternodeManager = new MasternodeManager(this);
        initializedDash = true;
        governanceManager = new GovernanceManager(this);
        triggerManager = new GovernanceTriggerManager(this);

        netFullfilledRequestManager = new NetFullfilledRequestManager(this);
        evoUserManager = new EvolutionUserManager(this);
        masternodeListManager = new SimplifiedMasternodeListManager(this);
        recoveredSigsDB = new SPVRecoveredSignaturesDatabase(this);
        quorumManager = new SPVQuorumManager(this, masternodeListManager);
        signingManager = new SigningManager(this, recoveredSigsDB);

        instantSendDB = new SPVInstantSendDatabase(this);
        instantSendManager = new InstantSendManager(this, instantSendDB);
        chainLockHandler = new ChainLocksHandler(this);
        llmqBackgroundThread = new LLMQBackgroundThread(this);

    }

    public void closeDash() {
        //Dash Specific
        sporkManager = null;

        masternodePayments = null;
        masternodeSync = null;
        activeMasternode = null;
        darkSendPool.close();
        darkSendPool = null;
        instantSend = null;
        masternodeManager = null;
        initializedDash = false;
        governanceManager = null;
    }

    public void initDashSync(final String directory)
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Context.propagate(Context.this);
                FlatDB<MasternodeManager> mndb = new FlatDB<MasternodeManager>(directory, "mncache.dat", "magicMasternodeCache");

                boolean success = mndb.load(masternodeManager);

                FlatDB<GovernanceManager> gmdb = new FlatDB<GovernanceManager>(directory, "goverance.dat", "magicGovernanceCache");

                success = gmdb.load(governanceManager);

                FlatDB<EvolutionUserManager> evdb = new FlatDB<EvolutionUserManager>(directory, "user.dat", "magicMasternodeCache");

                success = evdb.load(evoUserManager);

                FlatDB<SimplifiedMasternodeListManager> smnl = new FlatDB<SimplifiedMasternodeListManager>(Context.this, directory, false);

                success = smnl.load(masternodeListManager);

                //other functions
                darkSendPool.startBackgroundProcessing();

                if(!llmqBackgroundThread.isAlive())
                    llmqBackgroundThread.start();

            }
        }).start();
    }

    public void close() {
        llmqBackgroundThread.interrupt();
        blockChain.removeNewBestBlockListener(newBestBlockListener);
        if(initializedDash) {
            sporkManager.close(peerGroup);
            masternodeSync.close();
            instantSend.close();
            masternodeListManager.close();
            blockChain.removeTransactionReceivedListener(evoUserManager);
            instantSendManager.close(peerGroup);
            signingManager.close();
            chainLockHandler.close();
            quorumManager.close();
        }
    }

    public void setPeerGroupAndBlockChain(PeerGroup peerGroup, AbstractBlockChain chain)
    {
        this.peerGroup = peerGroup;
        this.blockChain = chain;
        hashStore = new HashStore(chain.getBlockStore());
        chain.addNewBestBlockListener(newBestBlockListener);
        if(initializedDash) {
            sporkManager.setBlockChain(chain, peerGroup);
            masternodeManager.setBlockChain(chain);
            masternodeSync.setBlockChain(chain);
            instantSend.setBlockChain(chain);
            masternodeListManager.setBlockChain(chain, peerGroup);
            chain.addTransactionReceivedListener(evoUserManager);
            instantSendManager.setBlockChain(chain, peerGroup);
            signingManager.setBlockChain(chain);
            chainLockHandler.setBlockChain(chain);
            quorumManager.setBlockChain(chain);
            updatedChainHead(chain.getChainHead());
        }
        params.setDIPActiveAtTip(chain.getBestChainHeight() >= params.getDIP0001BlockHeight());
    }

    public boolean isLiteMode() { return liteMode; }
    public void setLiteMode(boolean liteMode)
    {
        boolean current = this.liteMode;
        if(current == liteMode)
            return;

        this.liteMode = liteMode;
        if(liteMode == false)
        {
            darkSendPool.startBackgroundProcessing();
        }
    }
    public boolean allowInstantXinLiteMode() { return allowInstantX; }
    public void setAllowInstantXinLiteMode(boolean allow) {
        this.allowInstantX = allow;
    }


    NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            boolean fInitialDownload = blockChain.getChainHead().getHeader().getTimeSeconds() < (Utils.currentTimeSeconds() - 6 * 60 * 60); // ~144 blocks behind -> 2 x fork detection time, was 24 * 60 * 60 in bitcoin
            if(masternodeSync != null)
                masternodeSync.updateBlockTip(block, fInitialDownload);
        }
    };

    BlockChainListener updateHeadListener = new BlockChainListener () {
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException
        {

        }

        public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks,
                        List<StoredBlock> newBlocks) throws VerificationException{}

        public boolean isTransactionRelevant(Transaction tx) throws ScriptException
        {
            return false;
        }

        public void receiveFromBlock(Transaction tx, StoredBlock block,
                              BlockChain.NewBlockType blockType,
                              int relativityOffset) throws VerificationException
        {

        }



        public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block,
                                           BlockChain.NewBlockType blockType,
                                           int relativityOffset) throws VerificationException
        {
            return false;
        }
    };
    /**
     * The default fee per 1000 bytes of transaction data to pay when completing transactions. For details, see {@link SendRequest#feePerKb}.
     */
    public Coin getFeePerKb() {
        return feePerKb;
    }

    /**
     * Whether to ensure the minimum required fee by default when completing transactions. For details, see {@link SendRequest#ensureMinRequiredFee}.
     */
    public boolean isEnsureMinRequiredFee() {
        return ensureMinRequiredFee;
    }

    public void updatedChainHead(StoredBlock chainHead)
    {
        params.setDIPActiveAtTip(chainHead.getHeight() >= params.getDIP0001BlockHeight());
        if(initializedDash) {
            instantSend.updatedChainHead(chainHead);


        masternodeManager.updatedBlockTip(chainHead);
        masternodeListManager.updatedBlockTip(chainHead);

        /*darkSendPool.UpdatedBlockTip(pindex);
        instantsend.UpdatedBlockTip(pindex);
        mnpayments.UpdatedBlockTip(pindex);
        governance.UpdatedBlockTip(pindex);
        masternodeSync.UpdatedBlockTip(pindex);*/
        }
    }
    public VoteConfidenceTable getVoteConfidenceTable() {
        return voteConfidenceTable;
    }
}
