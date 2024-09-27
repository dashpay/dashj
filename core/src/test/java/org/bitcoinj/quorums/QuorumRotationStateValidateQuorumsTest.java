/*
 * Copyright 2023 Dash Core Group
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

package org.bitcoinj.quorums;

import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.evolution.SimplifiedMasternodesTest;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by hashengineering on 11/26/18.
 */
@RunWith(Parameterized.class)
public class QuorumRotationStateValidateQuorumsTest {

    static Context context;
    static final MainNetParams MAINPARAMS = MainNetParams.get();
    static final TestNet3Params TESTNETPARAMS = TestNet3Params.get();

    // to check for a network having a lower protocol version until the HF
    static final TestNet3Params TESTNETPARAMS_LOWER_PROTOCOL = new TestNet3Params() {
        @Override
        public int getProtocolVersionNum(ProtocolVersion version) {
            if (version.getBitcoinProtocolVersion() == ProtocolVersion.SMNLE_VERSIONED.getBitcoinProtocolVersion()) {
                return version.getBitcoinProtocolVersion() - 1;
            }
            return super.getProtocolVersionNum(version);
        }
    };
    static PeerGroup peerGroup;
    static BlockChain blockChain;

    private final NetworkParameters params;
    private final String qrInfoFilename;
    private final String blockchainFile;
    private final int protocolVersion;
    private final int height;
    private final int formatVersion;

    public QuorumRotationStateValidateQuorumsTest(NetworkParameters params,
                                                  String qrInfoFilename,
                                                  String blockchainFile,
                                                  int protocolVersion,
                                                  int height,
                                                  int formatVersion) {
        this.params = params;
        this.qrInfoFilename = qrInfoFilename;
        this.blockchainFile = blockchainFile;
        this.protocolVersion = protocolVersion;
        this.height = height;
        this.formatVersion = formatVersion;
    }

    @Parameterized.Parameters(name = "QuorumRotationStateTest {index} (qrinfo={0}, manager={1})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                        MAINPARAMS,
                        "QRINFO_0_1739226.dat",
                        "mainnet.spvchain",
                        70220,
                        1738936,
                        SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION
                },
                {
                        TESTNETPARAMS,
                        "qrinfo-testnet-0-850806-70228-after19.2HF.dat",
                        "testnet-after19.2HF.spvchain",
                        70228,
                        850744,
                        SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION
                },
                {
                        TESTNETPARAMS,
                        "qrinfo-testnet-0-849809_70228-before19.2HF.dat",
                        "testnet-849810.spvchain",
                        70228,
                        849592,
                        SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION
                },
                {
                        TESTNETPARAMS,
                        "testnet-849000-qrinfo.dat",
                        "testnet-849000-70227.spvchain",
                        70227,
                        848728,
                        SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION
                },
                {
                        TESTNETPARAMS_LOWER_PROTOCOL,
                        "testnet-849000-qrinfo.dat",
                        "testnet-849000-70227.spvchain",
                        70227,
                        848728,
                        SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION
                },
                {
                        TESTNETPARAMS,
                        "qrinfo-testnet-0-947248-70230-after20HF.dat",
                        "testnet-with-tip-947248.spvchain",
                        70230,
                        947224,
                        SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION
                }
        });
    }

    private void initContext() throws BlockStoreException {
        if (context == null || !context.getParams().equals(params))
            context = new Context(params);

        blockChain = new BlockChain(context, new SPVBlockStore(params, new File(Objects.requireNonNull(SimplifiedMasternodesTest.class.getResource(blockchainFile)).getPath())));
        context.initDash(true, true);
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);

        context.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }

    @Test
    public void processDiffMessage() throws BlockStoreException, IOException, ExecutionException, InterruptedException, TimeoutException {
        try {
            // this is for mainnet
            URL datafile = Objects.requireNonNull(getClass().getResource(qrInfoFilename));

            initContext();

            InputStream stream = Files.newInputStream(new File(datafile.getPath()).toPath());

            byte[] buffer = new byte[(int) stream.available()];
            //noinspection ResultOfMethodCallIgnored
            stream.read(buffer);

            SimplifiedMasternodeListManager manager = new SimplifiedMasternodeListManager(context);
            context.setMasternodeListManager(manager);

            QuorumRotationInfo qrinfo = new QuorumRotationInfo(context.getParams(), buffer, protocolVersion);

            SettableFuture<Boolean> qrinfoComplete = SettableFuture.create();
            manager.processDiffMessage(null, qrinfo, false, qrinfoComplete);
            qrinfoComplete.get(120, TimeUnit.SECONDS);

            // check verification and other items
            AtomicBoolean verified = new AtomicBoolean(true);
            manager.getQuorumListAtTip(params.getLlmqDIP0024InstantSend()).forEachQuorum(true, new SimplifiedQuorumList.ForeachQuorumCallback() {
                @Override
                public void processQuorum(FinalCommitment finalCommitment) {
                    verified.set(verified.get() && finalCommitment.isVerified());
                }
            });
            assertTrue(verified.get());
            assertEquals(height, manager.getQuorumListAtTip(context.getParams().getLlmqDIP0024InstantSend()).getHeight());

            stream.close();

            assertEquals(formatVersion, manager.getFormatVersion());
        } catch (Exception x) {
            x.printStackTrace();
        } finally {
            context.close();
            blockChain.getBlockStore().close();
        }
    }
}
