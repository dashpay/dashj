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

import org.bitcoinj.coinjoin.utils.CoinJoinManager;
import org.bitcoinj.coinjoin.utils.ProTxToOutpoint;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.GetSporksMessage;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.testing.InboundMessageQueuer;
import org.bitcoinj.testing.MockTransactionBroadcaster;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(value = Parameterized.class)
public class CoinJoinSessionTest extends TestWithMasternodeGroup {
    Logger log = LoggerFactory.getLogger(CoinJoinSessionTest.class);
    Random random = new Random();
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private static final int SESSION_ID = 123456;

    // Information for a single masternode
    SimplifiedMasternodeListEntry entry;
    TransactionOutPoint masternodeOutpoint;
    BLSSecretKey operatorSecretKey;
    private Address coinbaseTo;
    private Transaction finalTx = null;
    private Transaction completeTx = null;

    @Parameterized.Parameters
    public static Collection<ClientType[]> parameters() {
        return Arrays.asList(new ClientType[]{ClientType.NIO_CLIENT_MANAGER},
                new ClientType[]{ClientType.BLOCKING_CLIENT_MANAGER});
    }

    public CoinJoinSessionTest(ClientType clientType) {
        super(clientType);
    }

    //private static final NetworkParameters UNITTEST = UnitTestParams.get();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        BriefLogFormatter.initVerbose();
        Utils.setMockClock(); // Use mock clock
        ProTxToOutpoint.initialize(UNITTEST);
        wallet.freshReceiveKey();

        coinbaseTo = Address.fromKey(UNITTEST, wallet.currentReceiveKey());
        operatorSecretKey = BLSSecretKey.fromSeed(Sha256Hash.ZERO_HASH.getBytes());
        entry = new SimplifiedMasternodeListEntry(
                UNITTEST,
                Sha256Hash.ZERO_HASH,
                Sha256Hash.ZERO_HASH,
                new MasternodeAddress("127.0.0.1", 2003),
                new KeyId(new ECKey().getPubKeyHash()),
                new BLSLazyPublicKey(operatorSecretKey.GetPublicKey()),
                true
        );

        masternodeOutpoint = new TransactionOutPoint(UNITTEST, 0, Sha256Hash.ZERO_HASH);

        Block nextBlock = blockChain.getChainHead().getHeader().createNextBlock(coinbaseTo,
                1,
                Utils.currentTimeSeconds(),
                blockChain.getBestChainHeight() + 1,
                entry.getHash(), entry.getHash());
        blockChain.add(nextBlock);

        // this will add one masternode to our masternode list
        Transaction coinbase = nextBlock.getTransactions().get(0);
        SimplifiedMasternodeListDiff diff = new SimplifiedMasternodeListDiff(UNITTEST,
                nextBlock.getPrevBlockHash(),
                nextBlock.getHash(),
                new PartialMerkleTree(
                        UNITTEST,
                        new byte[]{0},
                        new ArrayList<>(Collections.singletonList(nextBlock.getTransactions().get(0).getTxId())),
                        1),
                coinbase,
                Collections.singletonList(entry),
                Collections.emptyList()
        );
        wallet.getContext().masternodeListManager.processMasternodeListDiff(null, diff, true);

        wallet.getContext().coinJoinManager.setMasternodeGroup(masternodeGroup);
        MockTransactionBroadcaster broadcaster = new MockTransactionBroadcaster(wallet);
        wallet.setTransactionBroadcaster(broadcaster);

        // the first block needs to mature before we can spend it, so mine 100 more blocks on the blockchain
        for (int i = 0; i < 100; ++i) {
            addBlock();
        }

        globalTimeout = Timeout.seconds(30);
    }

    @Override
    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void sessionTest() throws Exception {

        wallet.addCoinJoinKeyChain(DerivationPathFactory.get(wallet.getParams()).coinJoinDerivationPath());
        CoinJoinClientOptions.reset();
        CoinJoinClientOptions.setAmount(Coin.COIN);
        CoinJoinClientOptions.setEnabled(true);
        CoinJoinClientOptions.setRounds(1);

        peerGroup.start();
        masternodeGroup.start();
        InboundMessageQueuer p1 = connectPeer(1);
        InboundMessageQueuer p2 = connectPeer(2);
        assertEquals(2, peerGroup.numConnectedPeers());

        CoinJoinManager coinJoinManager = wallet.getContext().coinJoinManager;

        coinJoinManager.coinJoinClientManagers.put(wallet.getDescription(), new CoinJoinClientManager(wallet));

        HashMap<TransactionOutPoint, ECKey> keyMap = new HashMap<>();

        // mix coins
        CoinJoinClientManager clientManager = coinJoinManager.coinJoinClientManagers.get(wallet.getDescription());
        clientManager.setBlockChain(wallet.getContext().blockChain);

        addBlock();
        clientManager.updatedSuccessBlock();

        if (!clientManager.startMixing()) {
            System.out.println("Mixing has been started already.");
            return;
        }

        boolean result = clientManager.doAutomaticDenominating();
        System.out.println("Mixing " + (result ? "started successfully" : ("start failed: " + clientManager.getStatuses() + ", will retry")));

        for (int i = 0; i < 5; ++i) {
            addBlock();
        }

        boolean breakOut = false;
        CoinJoinQueue queue = null;

        // this loop with a sleep of 1 second will simulate a node that is attempting to
        // mix 1 session (1 round)
        do {
            Thread.sleep(1000);
            wallet.getContext().coinJoinManager.doMaintenance();
            addBlock();

            // this section of nextMessage() and if/else blocks will simulate a mixing masternode
            // it will reply to the messages sent by doMainenance()
            Message m = lastMasternode.nextMessage();
            log.info("received message: {}", m);

            if (m instanceof GetSporksMessage) {
                m = lastMasternode.nextMessage();
            }

            if (m instanceof CoinJoinAccept) {
                CoinJoinAccept dsa = (CoinJoinAccept) m;
                CoinJoinStatusUpdate update = new CoinJoinStatusUpdate(m.getParams(), SESSION_ID, PoolState.POOL_STATE_QUEUE, PoolStatusUpdate.STATUS_ACCEPTED, PoolMessage.MSG_NOERR);
                coinJoinManager.processMessage(lastMasternode.peer, update);

                // send the dsq message indicating the mixing session is ready
                queue = new CoinJoinQueue(m.getParams(), dsa.getDenomination(), masternodeOutpoint, Utils.currentTimeSeconds(), true);
                queue.sign(operatorSecretKey);
                coinJoinManager.processMessage(lastMasternode.peer, queue);
            } else if (m instanceof CoinJoinEntry) {
                CoinJoinEntry entry = (CoinJoinEntry) m;
                CoinJoinStatusUpdate update = new CoinJoinStatusUpdate(m.getParams(), SESSION_ID, PoolState.POOL_STATE_ACCEPTING_ENTRIES, PoolStatusUpdate.STATUS_ACCEPTED, PoolMessage.MSG_ENTRIES_ADDED);
                coinJoinManager.processMessage(lastMasternode.peer, update);

                finalTx = new Transaction(UNITTEST);

                for (TransactionInput input : entry.getMixingInputs()) {
                    finalTx.addInput(input);
                }

                for (TransactionOutput output : entry.getMixingOutputs()) {
                    finalTx.addOutput(output);
                }

                for (int i = 0; i < 10 - entry.getMixingOutputs().size(); i++) {
                    finalTx.addOutput(CoinJoin.denominationToAmount(queue.getDenomination()), Address.fromKey(UNITTEST, new ECKey()));
                    byte[] txId = new byte[32];
                    random.nextBytes(txId);
                    TransactionOutPoint outPoint = new TransactionOutPoint(UNITTEST, random.nextInt(10), Sha256Hash.wrap(txId));
                    TransactionInput input = new TransactionInput(UNITTEST, null, new byte[]{}, outPoint);
                    finalTx.addInput(input);
                }
                CoinJoinFinalTransaction finalTxMessage = new CoinJoinFinalTransaction(m.getParams(), SESSION_ID, finalTx);
                coinJoinManager.processMessage(lastMasternode.peer, finalTxMessage);

                update = new CoinJoinStatusUpdate(m.getParams(), SESSION_ID, PoolState.POOL_STATE_SIGNING, PoolStatusUpdate.STATUS_ACCEPTED, PoolMessage.MSG_NOERR);
                coinJoinManager.processMessage(lastMasternode.peer, update);
            } else if (m instanceof CoinJoinSignedInputs) {
                CoinJoinSignedInputs signedInputs = (CoinJoinSignedInputs) m;
                CoinJoinComplete completeMessage = new CoinJoinComplete(UNITTEST, SESSION_ID, PoolMessage.MSG_SUCCESS);
                coinJoinManager.processMessage(lastMasternode.peer, completeMessage);

                // sign the rest of the inputs?
                completeTx = new Transaction(UNITTEST);
                completeTx.setVersion(finalTx.getVersion());
                completeTx.setLockTime(finalTx.getLockTime());
                for (TransactionOutput output : finalTx.getOutputs()) {
                    completeTx.addOutput(output);
                }
                for (TransactionInput input : finalTx.getInputs()) {
                    TransactionInput thisSignedInput = null;
                    for (TransactionInput signedInput : signedInputs.getInputs()) {
                        if (signedInput.getOutpoint().equals(input.getOutpoint())) {
                            thisSignedInput = signedInput;
                        }
                    }

                    if (thisSignedInput != null) {
                        completeTx.addInput(thisSignedInput);
                    } else {
                        ECKey signingKey = new ECKey();
                        Address address = Address.fromKey(UNITTEST, signingKey);
                        completeTx.addSignedInput(input, ScriptBuilder.createOutputScript(address), signingKey, Transaction.SigHash.ALL, false);
                    }
                }

                CoinJoinBroadcastTx broadcastTx = new CoinJoinBroadcastTx(UNITTEST, completeTx, masternodeOutpoint, Utils.currentTimeSeconds());
                broadcastTx.sign(operatorSecretKey);

                coinJoinManager.processMessage(lastMasternode.peer, broadcastTx);

                breakOut = true;
            }


        } while (!breakOut);


        // check that the queue for the session is valid
        assertNotNull(queue);

        // check that the mixing transaction was completed
        assertNotNull(completeTx);

        // check that the broadcastTx was processed
        CoinJoinBroadcastTx broadcastTx = CoinJoin.getDSTX(completeTx.getTxId());
        assertNotNull(broadcastTx);

        if (clientManager.isMixing()) {
            clientManager.stopMixing();
        }

    }

    // performs the function of a miner
    private void addBlock() throws PrunedException {
        //log.info("Mining a new block {} with {} txes", blockChain.getBestChainHeight() + 1, wallet.getPendingTransactions());
        Block nextBlock = blockChain.getChainHead().getHeader().createNextBlock(Address.fromKey(UNITTEST, new ECKey()), Block.BLOCK_VERSION_GENESIS, Utils.currentTimeSeconds() + blockChain.getBestChainHeight() * 2L, blockChain.getBestChainHeight() + 1);
        for (Transaction tx : wallet.getPendingTransactions()) {
            nextBlock.addTransaction(tx);
        }
        nextBlock.solve();
        blockChain.add(nextBlock);
    }
}
