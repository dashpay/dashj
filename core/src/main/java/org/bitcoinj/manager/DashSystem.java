package org.bitcoinj.manager;

import org.bitcoinj.coinjoin.utils.CoinJoinManager;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.DualBlockChain;
import org.bitcoinj.core.MasternodePayments;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.NetFullfilledRequestManager;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.SporkManager;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.evolution.MasternodeMetaDataManager;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.governance.GovernanceManager;
import org.bitcoinj.governance.GovernanceTriggerManager;
import org.bitcoinj.quorums.ChainLocksHandler;
import org.bitcoinj.quorums.InstantSendDatabase;
import org.bitcoinj.quorums.InstantSendManager;
import org.bitcoinj.quorums.LLMQBackgroundThread;
import org.bitcoinj.quorums.QuorumManager;
import org.bitcoinj.quorums.QuorumSnapshotManager;
import org.bitcoinj.quorums.RecoveredSignaturesDatabase;
import org.bitcoinj.quorums.SPVInstantSendDatabase;
import org.bitcoinj.quorums.SPVQuorumManager;
import org.bitcoinj.quorums.SPVRecoveredSignaturesDatabase;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.store.FlatDB;
import org.bitcoinj.store.HashStore;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DashSystem {
    private static final Logger log = LoggerFactory.getLogger(DashSystem.class);

    private boolean liteMode = true;
    private boolean allowInstantX = true; //allow InstantX in litemode
    public PeerGroup peerGroup;
    public AbstractBlockChain blockChain;
    @Nullable
    public AbstractBlockChain headerChain;
    public SporkManager sporkManager;
    public MasternodePayments masternodePayments;
    public MasternodeSync masternodeSync;
    public HashStore hashStore;
    public GovernanceManager governanceManager;
    public GovernanceTriggerManager triggerManager;
    public NetFullfilledRequestManager netFullfilledRequestManager;
    public SimplifiedMasternodeListManager masternodeListManager;
    private InstantSendDatabase instantSendDB;
    public InstantSendManager instantSendManager;
    public SigningManager signingManager;
    public QuorumManager quorumManager;
    public QuorumSnapshotManager quorumSnapshotManager;
    private RecoveredSignaturesDatabase recoveredSigsDB;
    public ChainLocksHandler chainLockHandler;
    private LLMQBackgroundThread llmqBackgroundThread;
    public MasternodeMetaDataManager masternodeMetaDataManager;

    public CoinJoinManager coinJoinManager;
    private final ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> scheduledMasternodeSync;
    private ScheduledFuture<?> scheduledNetFulfilled;
    private ScheduledFuture<?> scheduledGovernance;

    Context context;

    public DashSystem(Context context) {
        String id = context.getParams().getId();
//        if (systemMap.containsKey(id)) {
//            throw new IllegalArgumentException("There is already a system for " + id);
//        }
        this.context = context;
        scheduledExecutorService = Executors.newScheduledThreadPool(2, new ContextPropagatingThreadFactory("context-thread-pool"));
        systemMap.put(id, this);
    }

    public Context getContext() {
        return context;
    }

    public NetworkParameters getParams() {
        return context.getParams();
    }

    //
    // Dash Specific
    //
    private boolean initializedObjects = false;
    private boolean initializedFiles = false;

    @Deprecated
    public boolean isInitializedDash() {
        return initializedObjects;
    }

    public void initDash(boolean liteMode, boolean allowInstantX) {
        initDash(liteMode, allowInstantX, null);
    }

    public void initDash(boolean liteMode, boolean allowInstantX, @Nullable EnumSet<MasternodeSync.SYNC_FLAGS> syncFlags) {
        initDash(liteMode, allowInstantX, syncFlags, null);
    }
    public void initDash(boolean liteMode, boolean allowInstantX, @Nullable EnumSet<MasternodeSync.SYNC_FLAGS> syncFlags,
                         @Nullable EnumSet<MasternodeSync.VERIFY_FLAGS> verifyFlags) {

        this.liteMode = liteMode;
        this.allowInstantX = allowInstantX;

        //Dash Specific
        sporkManager = new SporkManager(context);

        masternodePayments = new MasternodePayments(context);
        masternodeSync = syncFlags != null ? new MasternodeSync(context, syncFlags, verifyFlags) : new MasternodeSync(context, isLiteMode(), allowInstantXinLiteMode());
        governanceManager = new GovernanceManager(context);
        triggerManager = new GovernanceTriggerManager(context, governanceManager);

        netFullfilledRequestManager = new NetFullfilledRequestManager(context);
        masternodeListManager = new SimplifiedMasternodeListManager(context);
        recoveredSigsDB = new SPVRecoveredSignaturesDatabase(context);
        quorumManager = new SPVQuorumManager(context, masternodeListManager);
        quorumSnapshotManager = new QuorumSnapshotManager(context);
        signingManager = new SigningManager(context, recoveredSigsDB, quorumManager, masternodeSync);

        instantSendDB = new SPVInstantSendDatabase(context);
        instantSendManager = new InstantSendManager(context, instantSendDB, signingManager);
        chainLockHandler = new ChainLocksHandler(context);
        llmqBackgroundThread = new LLMQBackgroundThread(context, instantSendManager, chainLockHandler, signingManager, masternodeListManager);
        masternodeMetaDataManager = new MasternodeMetaDataManager(context);
        coinJoinManager = new CoinJoinManager(context, scheduledExecutorService, masternodeListManager, masternodeMetaDataManager, masternodeSync, chainLockHandler);
        initializedObjects = true;
    }

    public void setMasternodeListManager(SimplifiedMasternodeListManager masternodeListManager) {
        this.masternodeListManager = masternodeListManager;
        DualBlockChain dualBlockChain = new DualBlockChain(headerChain, blockChain);
        masternodeListManager.setBlockChain(dualBlockChain, peerGroup, quorumManager, quorumSnapshotManager, chainLockHandler, masternodeSync);
    }

    public void closeDash() {
        //Dash Specific
        close();
        sporkManager = null;

        masternodePayments = null;
        masternodeSync = null;
        initializedObjects = false;
        governanceManager = null;
        masternodeListManager = null;
    }

    public boolean initDashSync(final String directory) {
        return initDashSync(directory, null);
    }

    /**
     * Initializes objects by loading files from the specified directory
     *
     * @param directory location of the files
     * @param filePrefix file prefix of the files, typically the network name
     * @return true if the files are loaded.  false if the files were already loaded
     */
    public boolean initDashSync(final String directory, @Nullable String filePrefix) {
        if (!initializedFiles) {
            // remove mncache.dat if it exists
            File oldMnCacheFile = new File(directory, "mncache.dat");
            if (oldMnCacheFile.exists()) {
                if (oldMnCacheFile.delete())
                    log.info("removed obsolete mncache.dat");
            }

            // load governance data
            if (getSyncFlags().contains(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE)) {
                FlatDB<GovernanceManager> gmdb;
                if (filePrefix != null) {
                    gmdb = new FlatDB<>(context, directory + File.separator + filePrefix + ".gobjects", true);
                } else {
                    gmdb = new FlatDB<>(context, directory, false);
                }
                gmdb.load(governanceManager);
            }

            // load masternode data
            FlatDB<SimplifiedMasternodeListManager> smnl;
            if (filePrefix != null) {
                smnl = new FlatDB<>(context, directory + File.separator + filePrefix + ".mnlist", true);
            } else {
                smnl = new FlatDB<>(context, directory, false);
            }
            smnl.load(masternodeListManager);
            masternodeListManager.setLoadedFromFile(true);
            masternodeListManager.onFirstSaveComplete();


            // Load chainlocks
            FlatDB<ChainLocksHandler> clh;
            if (filePrefix != null) {
                clh = new FlatDB<>(context, directory + File.separator + filePrefix + ".chainlocks", true);
            } else {
                clh = new FlatDB<>(context, directory, false);
            }
            clh.load(chainLockHandler);

            // Load Masternode Metadata
            FlatDB<MasternodeMetaDataManager> mmdm;
            if (filePrefix != null) {
                mmdm = new FlatDB<>(context, directory + File.separator + filePrefix + ".mnmetadata", true);
            } else {
                mmdm = new FlatDB<>(context, directory, false);
            }
            mmdm.load(masternodeMetaDataManager);

            signingManager.initializeSignatureLog(directory);
            initializedFiles = true;
            return true;
        }
        return false;
    }

    private void startLLMQThread() {
        if (llmqBackgroundThread == null || !llmqBackgroundThread.isAlive()) {
            llmqBackgroundThread = new LLMQBackgroundThread(context, instantSendManager, chainLockHandler, signingManager, masternodeListManager);
            log.info("starting LLMQThread");
            llmqBackgroundThread.start();
        }
    }

    private void stopLLMQThread() {
        if (llmqBackgroundThread.isAlive()) {
            log.info("stopping LLMQThread");
            llmqBackgroundThread.interrupt();
        }
    }

    public void close() {
        if (initializedObjects) {
            sporkManager.close(peerGroup);
            masternodeSync.close();
            masternodeListManager.close();
            instantSendManager.close(peerGroup);
            signingManager.close();
            chainLockHandler.close();
            quorumManager.close();
            coinJoinManager.close();
            if(masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_INSTANTSENDLOCKS))
                llmqBackgroundThread.interrupt();
            blockChain.removeNewBestBlockListener(newBestBlockListener);
            if (scheduledMasternodeSync != null)
                scheduledMasternodeSync.cancel(true);
            blockChain.close();
            if (headerChain != null)
                headerChain.close();
            peerGroup = null;
        }
    }

    public void setPeerGroupAndBlockChain(PeerGroup peerGroup, AbstractBlockChain blockChain, @Nullable AbstractBlockChain headerChain) {
        if (this.peerGroup == null/* && initializedObjects*/) {
            this.peerGroup = peerGroup;
            this.blockChain = blockChain;
            this.headerChain = headerChain;
            DualBlockChain dualBlockChain = new DualBlockChain(headerChain, blockChain);
            hashStore = new HashStore(blockChain.getBlockStore());
            blockChain.addNewBestBlockListener(newBestBlockListener);
            handleActivations(blockChain.getChainHead());
            if (initializedObjects) {
                sporkManager.setBlockChain(blockChain, peerGroup, masternodeSync);
                masternodeSync.setBlockChain(blockChain, peerGroup, netFullfilledRequestManager, governanceManager);
                masternodeListManager.setBlockChain(
                        dualBlockChain,
                        peerGroup,
                        quorumManager,
                        quorumSnapshotManager,
                        chainLockHandler,
                        masternodeSync
                );
                instantSendManager.setBlockChain(blockChain, peerGroup, chainLockHandler, masternodeSync, sporkManager, masternodeListManager);
                signingManager.setBlockChain(blockChain, headerChain, masternodeListManager);
                chainLockHandler.setBlockChain(peerGroup, blockChain, headerChain, signingManager, sporkManager, masternodeSync);
                blockChain.setChainLocksHandler(chainLockHandler);
                quorumManager.setBlockChain(blockChain);
                updatedChainHead(blockChain.getChainHead());
                governanceManager.setBlockChain(peerGroup, masternodeSync, masternodeListManager, masternodeMetaDataManager, netFullfilledRequestManager, triggerManager);
                coinJoinManager.initMasternodeGroup(blockChain);
                coinJoinManager.setBlockchain(blockChain, peerGroup);

                // trigger saving mechanisms
                governanceManager.resume();
                masternodeListManager.resume();
                chainLockHandler.resume();
            }
            context.getParams().setDIPActiveAtTip(blockChain.getBestChainHeight() >= context.getParams().getDIP0001BlockHeight());
        }
    }

    public boolean isLiteMode() {
        return liteMode;
    }

//    public void setLiteMode(boolean liteMode)
//    {
//        boolean current = this.liteMode;
//        if(current == liteMode)
//            return;
//
//        this.liteMode = liteMode;
//    }

    public boolean allowInstantXinLiteMode() {
        return allowInstantX;
    }

    public void setAllowInstantXinLiteMode(boolean allow) {
        this.allowInstantX = allow;
    }

    NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            handleActivations(block);
            boolean fInitialDownload = blockChain.getChainHead().getHeader().getTimeSeconds() < (Utils.currentTimeSeconds() - 6 * 60 * 60); // ~144 blocks behind -> 2 x fork detection time, was 24 * 60 * 60 in bitcoin
            if(masternodeSync != null)
                masternodeSync.updateBlockTip(block, fInitialDownload);
        }
    };

    private void handleActivations(StoredBlock block) {
        // 24 hours before the hard fork (v19.2) connect only to v19.2 nodes on mainnet
        if (context.getParams().isV19Active(block.getHeight())) {
            BLSScheme.setLegacyDefault(false);
        }
    }

    @Deprecated
    public void updatedChainHead(StoredBlock chainHead)
    {
        context.getParams().setDIPActiveAtTip(chainHead.getHeight() >= context.getParams().getDIP0001BlockHeight());
        if(initializedObjects) {
            masternodeListManager.updatedBlockTip(chainHead);
        }
    }

    public Set<MasternodeSync.SYNC_FLAGS> getSyncFlags() {
        if (masternodeSync != null) {
            return masternodeSync.syncFlags;
        } else {
            return MasternodeSync.SYNC_DEFAULT_SPV;
        }
    }

    public boolean hasSyncFlag(MasternodeSync.SYNC_FLAGS syncFlag) {
        return getSyncFlags().contains(syncFlag);
    }

    public void start()  {
        if(getSyncFlags().contains(MasternodeSync.SYNC_FLAGS.SYNC_INSTANTSENDLOCKS)) {
            startLLMQThread();
        }

        if (getSyncFlags().contains(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE)) {
            scheduledMasternodeSync = scheduledExecutorService.scheduleWithFixedDelay(
                    () -> masternodeSync.doMaintenance(), 1, 1, TimeUnit.SECONDS);
            scheduledNetFulfilled = scheduledExecutorService.scheduleWithFixedDelay(
                    () -> netFullfilledRequestManager.doMaintenance(), 60, 60, TimeUnit.SECONDS);

            scheduledGovernance = scheduledExecutorService.scheduleWithFixedDelay(
                    () -> governanceManager.doMaintenance(), 60, 5, TimeUnit.MINUTES);
        }
    }

    public void shutdown() {
        if(getSyncFlags().contains(MasternodeSync.SYNC_FLAGS.SYNC_INSTANTSENDLOCKS)) {
            stopLLMQThread();
        }

        if (getSyncFlags().contains(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE)) {
            scheduledMasternodeSync.cancel(false);
            scheduledMasternodeSync = null;
            scheduledNetFulfilled.cancel(false);
            scheduledNetFulfilled = null;
            scheduledGovernance.cancel(false);
            scheduledGovernance = null;
        }
        if (initializedObjects) {
            coinJoinManager.stop();
        }
    }

    private static final HashMap<String, DashSystem> systemMap = new HashMap<>(3);
    public static DashSystem get(NetworkParameters parameters) {
        return systemMap.get(parameters.getId());
    }
    public static void remove(DashSystem system) {
        systemMap.remove(system.getParams().getId());
    }
    public void remove() {
        systemMap.remove(getParams().getId());
    }

    public void triggerHeadersDownloadComplete() {
        peerGroup.triggerHeadersDownloadComplete();
    }

    public void triggerMnListDownloadComplete() {
        peerGroup.triggerMnListDownloadComplete();
    }

    public void addWallet(Wallet wallet) {
        instantSendManager.addWallet(wallet);
    }
}
