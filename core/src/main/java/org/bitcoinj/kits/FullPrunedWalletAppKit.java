/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2019 Dash Core Group
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

package org.bitcoinj.kits;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates;
import org.bitcoinj.protocols.channels.StoredPaymentChannelServerStates;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.*;
import org.bitcoinj.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * <p>Utility class that wraps the boilerplate needed to set up a new full verification dashj app. Instantiate it with a directory
 * and file prefix, optionally configure a few things, then use startAsync and optionally awaitRunning. The object will
 * construct and configure a {@link FullPrunedBlockChain}, {@link FullPrunedBlockStore}, {@link Wallet} and {@link PeerGroup}. Depending
 * on the value of the blockingStartup property, startup will be considered complete once the block chain has fully
 * synchronized, so it can take a while.</p>
 *
 * <p>To add listeners and modify the objects that are constructed, you can either do that by overriding the
 * {@link #onSetupCompleted()} method (which will run on a background thread) and make your changes there,
 * or by waiting for the service to start and then accessing the objects from wherever you want. However, you cannot
 * access the objects this class creates until startup is complete.</p>
 *
 * <p>The asynchronous design of this class may seem puzzling (just use {@link #awaitRunning()} if you don't want that).
 * It is to make it easier to fit bitcoinj into GUI apps, which require a high degree of responsiveness on their main
 * thread which handles all the animation and user interaction. Even when blockingStart is false, initializing bitcoinj
 * means doing potentially blocking file IO, generating keys and other potentially intensive operations. By running it
 * on a background thread, there's no risk of accidentally causing UI lag.</p>
 *
 * <p>Note that {@link #awaitRunning()} can throw an unchecked {@link IllegalStateException}
 * if anything goes wrong during startup - you should probably handle it and use {@link Exception#getCause()} to figure
 * out what went wrong more precisely. Same thing if you just use the {@link #startAsync()} method.</p>
 */
public class FullPrunedWalletAppKit extends AbstractIdleService {
    protected static final Logger log = LoggerFactory.getLogger(FullPrunedWalletAppKit.class);

    protected final String filePrefix;
    protected final NetworkParameters params;
    protected volatile FullPrunedBlockChain vChain;
    protected volatile FullPrunedBlockStore vStore;
    protected volatile Wallet vWallet;
    protected volatile PeerGroup vPeerGroup;

    protected final File directory;
    protected volatile File vWalletFile;

    protected boolean useAutoSave = true;
    protected PeerAddress[] peerAddresses;
    protected DownloadProgressTracker downloadListener;
    protected boolean autoStop = true;
    protected InputStream checkpoints;
    protected boolean blockingStartup = true;
    protected boolean useTor = false;   // Perhaps in future we can change this to true.
    protected String userAgent, version;
    protected WalletProtobufSerializer.WalletFactory walletFactory;
    @Nullable protected DeterministicSeed restoreFromSeed;
    @Nullable protected PeerDiscovery discovery;

    protected volatile Context context;
    protected volatile DashSystem vSystem;



    /**
     * Creates a new FullPrunedWalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public FullPrunedWalletAppKit(NetworkParameters params, File directory, String filePrefix) {
        this(new Context(params), directory, filePrefix);
    }

    /**
     * Creates a new FullPrunedWalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public FullPrunedWalletAppKit(NetworkParameters params, File directory, String filePrefix, boolean liteMode) {
        this(new Context(params), directory, filePrefix, liteMode);
    }

    /**
     * Creates a new FullPrunedWalletAppKit, with the given {@link Context}. Files will be stored in the given directory.
     */
    public FullPrunedWalletAppKit(Context context, File directory, String filePrefix) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.directory = checkNotNull(directory);
        this.filePrefix = checkNotNull(filePrefix);
        this.vSystem = new DashSystem(context);
        vSystem.initDash(false, true);
        vSystem.initDashSync(directory.getAbsolutePath());
    }

    /**
     * Creates a new FullPrunedWalletAppKit, with the given {@link Context}. Files will be stored in the given directory.
     */
    public FullPrunedWalletAppKit(Context context, File directory, String filePrefix, boolean liteMode) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.directory = checkNotNull(directory);
        this.filePrefix = checkNotNull(filePrefix);
        this.vSystem = new DashSystem(context);
        vSystem.initDash(liteMode, true);
        vSystem.initDashSync(directory.getAbsolutePath());
    }

    /** Will only connect to the given addresses. Cannot be called after startup. */
    public FullPrunedWalletAppKit setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
        return this;
    }

    /** Will only connect to localhost. Cannot be called after startup. */
    public FullPrunedWalletAppKit connectToLocalHost() {
        try {
            final InetAddress localHost = InetAddress.getLocalHost();
            return setPeerNodes(new PeerAddress(params, localHost, params.getPort()));
        } catch (UnknownHostException e) {
            // Borked machine with no loopback adapter configured properly.
            throw new RuntimeException(e);
        }
    }

    /** If true, the wallet will save itself to disk automatically whenever it changes. */
    public FullPrunedWalletAppKit setAutoSave(boolean value) {
        checkState(state() == State.NEW, "Cannot call after startup");
        useAutoSave = value;
        return this;
    }

    /**
     * If you want to learn about the sync process, you can provide a listener here. For instance, a
     * {@link org.bitcoinj.core.listeners.DownloadProgressTracker} is a good choice. This has no effect unless setBlockingStartup(false) has been called
     * too, due to some missing implementation code.
     */
    public FullPrunedWalletAppKit setDownloadListener(DownloadProgressTracker listener) {
        this.downloadListener = listener;
        return this;
    }

    /** If true, will register a shutdown hook to stop the library. Defaults to true. */
    public FullPrunedWalletAppKit setAutoStop(boolean autoStop) {
        this.autoStop = autoStop;
        return this;
    }

    /**
     * If set, the file is expected to contain a checkpoints file calculated with BuildCheckpoints. It makes initial
     * block sync faster for new users - please refer to the documentation on the bitcoinj website for further details.
     */
    public FullPrunedWalletAppKit setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Closeables.closeQuietly(this.checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
        return this;
    }

    /**
     * If true (the default) then the startup of this service won't be considered complete until the network has been
     * brought up, peer connections established and the block chain synchronised. Therefore {@link #awaitRunning()} can
     * potentially take a very long time. If false, then startup is considered complete once the network activity
     * begins and peer connections/block chain sync will continue in the background.
     */
    public FullPrunedWalletAppKit setBlockingStartup(boolean blockingStartup) {
        this.blockingStartup = blockingStartup;
        return this;
    }

    /**
     * Sets the string that will appear in the subver field of the version message.
     * @param userAgent A short string that should be the name of your app, e.g. "My Wallet"
     * @param version A short string that contains the version number, e.g. "1.0-BETA"
     */
    public FullPrunedWalletAppKit setUserAgent(String userAgent, String version) {
        this.userAgent = checkNotNull(userAgent);
        this.version = checkNotNull(version);
        return this;
    }

    /**
     * If called, then an embedded Tor client library will be used to connect to the P2P network. The user does not need
     * any additional software for this: it's all pure Java. As of April 2014 <b>this mode is experimental</b>.
     */
    public FullPrunedWalletAppKit useTor() {
        this.useTor = true;
        return this;
    }

    /**
     * If a seed is set here then any existing wallet that matches the file name will be renamed to a backup name,
     * the chain file will be deleted, and the wallet object will be instantiated with the given seed instead of
     * a fresh one being created. This is intended for restoring a wallet from the original seed. To implement restore
     * you would shut down the existing appkit, if any, then recreate it with the seed given by the user, then start
     * up the new kit. The next time your app starts it should work as normal (that is, don't keep calling this each
     * time).
     */
    public FullPrunedWalletAppKit restoreWalletFromSeed(DeterministicSeed seed) {
        this.restoreFromSeed = seed;
        return this;
    }

    /**
     * Sets the peer discovery class to use. If none is provided then DNS is used, which is a reasonable default.
     */
    public FullPrunedWalletAppKit setDiscovery(@Nullable PeerDiscovery discovery) {
        this.discovery = discovery;
        return this;
    }

    /**
     * <p>Override this to return wallet extensions if any are necessary.</p>
     *
     * <p>When this is called, chain(), store(), and peerGroup() will return the created objects, however they are not
     * initialized/started.</p>
     */
    protected List<WalletExtension> provideWalletExtensions() throws Exception {
        return ImmutableList.of();
    }

    /**
     * Override this to use a {@link FullPrunedBlockStore} that isn't the default of {@link LevelDBFullPrunedBlockStore}.
     */
    protected FullPrunedBlockStore provideBlockStore(File file) throws BlockStoreException {
        return new LevelDBFullPrunedBlockStore(
                params, file.getAbsolutePath(), 1000, 100 * 1024 * 1024l,
                10 * 1024 * 1024, 100000, true, Integer.MAX_VALUE);
    }

    /**
     * This method is invoked on a background thread after all objects are initialised, but before the peer group
     * or block chain download is started. You can tweak the objects configuration here.
     */
    protected void onSetupCompleted() { }

    @Override
    protected void startUp() throws Exception {
        // Runs in a separate thread.
        Context.propagate(context);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        log.info("Starting up with directory = {}", directory);
        try {
            File chainFolder = new File(directory, "chain");
            File chainFile = new File(chainFolder, "CURRENT");
            boolean chainFileExists = chainFolder.exists() && chainFolder.listFiles().length != 0;
            vWalletFile = new File(directory, filePrefix + ".wallet");
            boolean shouldReplayWallet = (vWalletFile.exists() && !chainFileExists) || restoreFromSeed != null;
            vWallet = createOrLoadWallet(shouldReplayWallet);

            // Initiate Bitcoin network objects (block store, blockchain and peer group)
            if(!chainFolder.exists()) {
                chainFolder.mkdir();
            }
            vStore = provideBlockStore(chainFolder);
            if (!chainFileExists || restoreFromSeed != null) {
                //if (checkpoints == null && !Utils.isAndroidRuntime()) {
                //    checkpoints = CheckpointManager.openStream(params);
                //}

                if (checkpoints != null) {
                    // Initialize the chain file with a checkpoint to speed up first-run sync.
                    long time;
                    if (restoreFromSeed != null) {
                        time = restoreFromSeed.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Deleting the chain file in preparation from restore.");
                            vStore.close();
                            if (!chainFile.delete())
                                throw new IOException("Failed to delete chain file in preparation for restore.");
                            vStore = provideBlockStore(chainFolder);
                        }
                    } else {
                        time = vWallet.getEarliestKeyCreationTime();
                    }
                    if (time > 0)
                        CheckpointManager.checkpoint(params, checkpoints, vStore, time);
                    else
                        log.warn("Creating a new uncheckpointed block store due to a wallet with a creation time of zero: this will result in a very slow chain sync");
                } else if (chainFileExists) {
                    log.info("Deleting the chain file in preparation from restore.");
                    vStore.close();
                    if (!chainFile.delete())
                        throw new IOException("Failed to delete chain file in preparation for restore.");
                    vStore = provideBlockStore(chainFolder);
                }
            }
            vChain = new FullPrunedBlockChain(params, vStore);
            vPeerGroup = createPeerGroup();
            if (this.userAgent != null)
                vPeerGroup.setUserAgent(userAgent, version);

            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null) {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                vPeerGroup.setMaxConnections(peerAddresses.length);
                peerAddresses = null;
            } else if (!params.getId().equals(NetworkParameters.ID_REGTEST) && !useTor) {
                vPeerGroup.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
            }
            vChain.addWallet(vWallet);
            vPeerGroup.addWallet(vWallet);
            onSetupCompleted();

            if (blockingStartup) {
                vPeerGroup.start();
                // Make sure we shut down cleanly.
                installShutdownHook();
                completeExtensionInitiations(vPeerGroup);

                // TODO: Be able to use the provided download listener when doing a blocking startup.
                final DownloadProgressTracker listener = new DownloadProgressTracker(vSystem.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_BLOCKS_AFTER_PREPROCESSING));
                vPeerGroup.startBlockChainDownload(listener);
                listener.await();
            } else {
                Futures.addCallback(vPeerGroup.startAsync(), new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        completeExtensionInitiations(vPeerGroup);
                        final DownloadProgressTracker l = downloadListener == null ? new DownloadProgressTracker(vSystem.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_BLOCKS_AFTER_PREPROCESSING)) : downloadListener;
                        vPeerGroup.startBlockChainDownload(l);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);

                    }
                }, MoreExecutors.directExecutor());
            }
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    private Wallet createOrLoadWallet(boolean shouldReplayWallet) throws Exception {
        Wallet wallet;

        maybeMoveOldWalletOutOfTheWay();

        if (vWalletFile.exists()) {
            wallet = loadWallet(shouldReplayWallet);
        } else {
            wallet = createWallet();
            wallet.freshReceiveKey();
            for (WalletExtension e : provideWalletExtensions()) {
                wallet.addExtension(e);
            }

            // Currently the only way we can be sure that an extension is aware of its containing wallet is by
            // deserializing the extension (see WalletExtension#deserializeWalletExtension(Wallet, byte[]))
            // Hence, we first save and then load wallet to ensure any extensions are correctly initialized.
            wallet.saveToFile(vWalletFile);
            wallet = loadWallet(false);
        }

        if (useAutoSave) {
            this.setupAutoSave(wallet);
        }

        return wallet;
    }

    protected void setupAutoSave(Wallet wallet) {
        wallet.autosaveToFile(vWalletFile, 5, TimeUnit.SECONDS, null);
    }

    private Wallet loadWallet(boolean shouldReplayWallet) throws Exception {
        Wallet wallet;
        FileInputStream walletStream = new FileInputStream(vWalletFile);
        try {
            List<WalletExtension> extensions = provideWalletExtensions();
            WalletExtension[] extArray = extensions.toArray(new WalletExtension[extensions.size()]);
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer;
            if (walletFactory != null)
                serializer = new WalletProtobufSerializer(walletFactory);
            else
                serializer = new WalletProtobufSerializer();
            wallet = serializer.readWallet(params, extArray, proto);
            if (shouldReplayWallet)
                wallet.reset();
        } finally {
            walletStream.close();
        }
        return wallet;
    }

    protected Wallet createWallet() {
        KeyChainGroup kcg;
        if (restoreFromSeed != null)
            kcg = KeyChainGroup.builder(params).fromSeed(restoreFromSeed, Script.ScriptType.P2PKH).build();
        else
            kcg = KeyChainGroup.builder(params).build();
        if (walletFactory != null) {
            return walletFactory.create(params, kcg);
        } else {
            return new Wallet(params, kcg);  // default
        }
    }

    private void maybeMoveOldWalletOutOfTheWay() {
        if (restoreFromSeed == null) return;
        if (!vWalletFile.exists()) return;
        int counter = 1;
        File newName;
        do {
            newName = new File(vWalletFile.getParent(), "Backup " + counter + " for " + vWalletFile.getName());
            counter++;
        } while (newName.exists());
        log.info("Renaming old wallet file {} to {}", vWalletFile, newName);
        if (!vWalletFile.renameTo(newName)) {
            // This should not happen unless something is really messed up.
            throw new RuntimeException("Failed to rename wallet for restore");
        }
    }

    /*
     * As soon as the transaction broadcaster han been created we will pass it to the
     * payment channel extensions
     */
    private void completeExtensionInitiations(TransactionBroadcaster transactionBroadcaster) {
        StoredPaymentChannelClientStates clientStoredChannels = (StoredPaymentChannelClientStates)
                vWallet.getExtensions().get(StoredPaymentChannelClientStates.class.getName());
        if(clientStoredChannels != null) {
            clientStoredChannels.setTransactionBroadcaster(transactionBroadcaster);
        }
        StoredPaymentChannelServerStates serverStoredChannels = (StoredPaymentChannelServerStates)
                vWallet.getExtensions().get(StoredPaymentChannelServerStates.class.getName());
        if(serverStoredChannels != null) {
            serverStoredChannels.setTransactionBroadcaster(transactionBroadcaster);
        }
    }


    protected PeerGroup createPeerGroup() throws TimeoutException {
        return new PeerGroup(params, vChain);
    }

    private void installShutdownHook() {
        if (autoStop) Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                try {
                    FullPrunedWalletAppKit.this.stopAsync();
                    FullPrunedWalletAppKit.this.awaitTerminated();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    protected void shutDown() throws Exception {
        // Runs in a separate thread.
        try {
            Context.propagate(context);
            vPeerGroup.stop();
            vWallet.saveToFile(vWalletFile);
            vStore.close();
            vSystem.close();
            vSystem = null;
            vPeerGroup = null;
            vWallet = null;
            vStore = null;
            vChain = null;
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    public NetworkParameters params() {
        return params;
    }

    public FullPrunedBlockChain chain() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vChain;
    }

    public BlockStore store() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vStore;
    }

    public Wallet wallet() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vWallet;
    }

    public DashSystem system() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vSystem;
    }

    public PeerGroup peerGroup() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vPeerGroup;
    }

    public File directory() {
        return directory;
    }
}
