/*
 * Copyright 2022 Dash Core Group
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
package org.bitcoinj.coinjoin;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.coinjoin.utils.MasternodeGroup;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionAck;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.ClientConnectionManager;
import org.bitcoinj.net.NioClientManager;
import org.bitcoinj.testing.InboundMessageQueuer;
import org.bitcoinj.testing.TestWithPeerGroup;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.junit.After;
import org.junit.Before;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class TestWithMasternodeGroup extends TestWithPeerGroup {

    protected MasternodeGroup masternodeGroup;
    private static final int SESSION_ID = 123456;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void beforeInitPeerGroup() {
        Context.get().initDash(true, true);
    }

    @After
    public void tearDown() {
        try {
            super.tearDown();
            blockJobs = false;
            Utils.finishMockSleep();
            if (masternodeGroup.isRunning())
                masternodeGroup.stopAsync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TestWithMasternodeGroup(ClientType clientType) {
        super(clientType);
    }

    @Override
    protected void initPeerGroup() {
        super.initPeerGroup();
        if (clientType == ClientType.NIO_CLIENT_MANAGER)
            masternodeGroup = createMasternodeGroup(new NioClientManager());
        else
            masternodeGroup = createMasternodeGroup(new BlockingClientManager());
        masternodeGroup.setPingIntervalMsec(0);  // Disable the pings as they just get in the way of most tests.
        //masternodeGroup.addWallet(wallet);
        masternodeGroup.setUseLocalhostPeerWhenPossible(false); // Prevents from connecting to bitcoin nodes on localhost.
    }

    InboundMessageQueuer lastMasternode;

    private MasternodeGroup createMasternodeGroup(final ClientConnectionManager manager) {
        return new MasternodeGroup(UNITTEST, blockChain, manager) {
            @Override
            protected ListeningScheduledExecutorService createPrivateExecutor() {
                return MoreExecutors.listeningDecorator(new ScheduledThreadPoolExecutor(1, new ContextPropagatingThreadFactory("PeerGroup test thread")) {
                    @Override
                    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
                        if (!blockJobs)
                            return super.schedule(command, delay, unit);
                        return super.schedule(new Runnable() {
                            @Override
                            public void run() {
                                Utils.rollMockClockMillis(unit.toMillis(delay));
                                command.run();
                                jobBlocks.acquireUninterruptibly();
                            }
                        }, 0 /* immediate */, unit);
                    }
                });
            }

            @Override
            public boolean addPendingMasternode(Sha256Hash proTxHash) {
                try {
                    Masternode mn = context.masternodeListManager.getListAtChainTip().getMN(proTxHash);
                    lastMasternode = connectMasternode(mn.getService().getPort() - 2000);
                    return true;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            @Override
            public boolean forPeer(MasternodeAddress service, ForPeer predicate) {
                // this test will only connect to one "masternode"
                if (lastMasternode != null) {
                    return predicate.process(lastMasternode.peer);
                }
                return false;
            }
        };
    }

    PreMessageReceivedEventListener preMessageReceivedEventListener = new PreMessageReceivedEventListener() {
        @Override
        public Message onPreMessageReceived(Peer peer, Message m) {
            if (m instanceof CoinJoinAccept) {
                peer.sendMessage(new CoinJoinStatusUpdate(m.getParams(), SESSION_ID, PoolState.POOL_STATE_QUEUE, PoolStatusUpdate.STATUS_ACCEPTED, PoolMessage.MSG_NOERR));
                return null;
            } else if (m instanceof CoinJoinEntry) {
                return null;
            } else if (m instanceof CoinJoinSignedInputs) {
                return null;
            }
            return m;
        }
    };

    protected InboundMessageQueuer connectMasternodeWithoutVersionExchange(int id) throws Exception {
        Preconditions.checkArgument(id < PEER_SERVERS);
        InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 2000 + id);
        Peer peer = peerGroup.connectTo(remoteAddress).getConnectionOpenFuture().get();
        peer.addPreMessageReceivedEventListener(preMessageReceivedEventListener);
        InboundMessageQueuer writeTarget = newPeerWriteTargetQueue.take();
        writeTarget.peer = peer;
        return writeTarget;
    }

    protected InboundMessageQueuer connectMasternode(int id) throws Exception {
        return connectMasternode(id, remoteVersionMessage);
    }

    protected InboundMessageQueuer connectMasternode(int id, VersionMessage versionMessage) throws Exception {
        checkArgument(versionMessage.hasBlockChain());
        InboundMessageQueuer writeTarget = connectMasternodeWithoutVersionExchange(id);
        // Complete handshake with the peer - send/receive version(ack)s, receive bloom filter
        writeTarget.sendMessage(versionMessage);
        writeTarget.sendMessage(new VersionAck());
        stepThroughInit(versionMessage, writeTarget);
        return writeTarget;
    }
}
