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

package org.bitcoinj.evolution;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.quorums.QuorumRotationInfoTest;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by hashengineering on 6/19/2023.
 */
@RunWith(Parameterized.class)
public class LoadBootstrapFilesTest {

    private static Context context;
    private static DashSystem system;
    private static final MainNetParams MAINPARAMS = MainNetParams.get();
    private static final TestNet3Params TESTNETPARAMS = TestNet3Params.get();
    private static PeerGroup peerGroup;
    private static BlockChain blockChain;

    private final NetworkParameters params;
    private final String mnlistdiffFilename;
    private final String qrinfoFilename;
    private final int fileFormatVersion;
    private final int mnlistdiffHeight;
    private final int qrinfoHeight;

    public LoadBootstrapFilesTest(NetworkParameters params,
                                  String mnlistdiffFilename, String qrInfoFilename,
                                  int protocolVersion, int mnlistdiffHeight, int qrinfoHeight) {
        this.params = params;
        this.mnlistdiffFilename = mnlistdiffFilename;
        this.qrinfoFilename = qrInfoFilename;
        this.fileFormatVersion = protocolVersion;
        this.mnlistdiffHeight = mnlistdiffHeight;
        this.qrinfoHeight = qrinfoHeight;
    }

    @Parameterized.Parameters(name = "QuorumStateTest {index} (version={3}, qrinfo={1}, manager={2})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                        MAINPARAMS,
                        "ML1088640.dat",
                        null,
                        SimplifiedMasternodeListManager.LLMQ_FORMAT_VERSION,
                        1088640,
                        -1
                },
                {
                        MAINPARAMS,
                        "mnlistdiff-mainnet-0-1888465-70227.dat",
                        "qrinfo-mainnet-0-1888473_70227.dat",
                        SimplifiedMasternodeListManager.BLS_SCHEME_FORMAT_VERSION,
                        1888465,
                        1888408
                },
                {
                        MAINPARAMS,
                        "mnlistdiff-mainnet-0-2028691-70230.dat",
                        "qrinfo-mainnet-0-2028764-70230.dat",
                        SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION,
                        2028691,
                        2028664
                }
        });
    }

    private void initContext() throws BlockStoreException {
        context = new Context(params);
        system = new DashSystem(context);
        if (blockChain == null) {
            blockChain = new BlockChain(context, new MemoryBlockStore(params));
        }

        system.initDash(true, true);
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);

        system.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }

    @Test
    public void loadAndTestBootstrap() throws BlockStoreException {
        URL mnlistdiffFile = Objects.requireNonNull(getClass().getResource(mnlistdiffFilename));
        URL qrinfoFile = (qrinfoFilename != null) ? Objects.requireNonNull(QuorumRotationInfoTest.class.getResource(qrinfoFilename)) : null;

        initContext();

        SimplifiedMasternodeListManager manager = new SimplifiedMasternodeListManager(context);
        system.setMasternodeListManager(manager);
        manager.setBootstrap(mnlistdiffFile.getPath(), (qrinfoFile != null) ? qrinfoFile.getPath() : null, fileFormatVersion);

        manager.resetMNList(true, true);

        try {
            manager.waitForBootstrapLoaded();
            assertEquals(mnlistdiffHeight, manager.getMasternodeList().getHeight());
            if (qrinfoFilename != null)
                assertEquals(qrinfoHeight, manager.quorumRotationState.getMasternodeList().getHeight());
        } catch (InterruptedException | ExecutionException x) {
            fail("unable to load bootstrap file");
        }
    }
}
