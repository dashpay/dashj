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
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.quorums.LLMQParameters;
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

import static org.junit.Assert.assertEquals;

/**
 * Created by hashengineering on 11/26/18.
 */
@RunWith(Parameterized.class)
public class QuorumStateValidateQuorumsTest {

    static Context context;
    static final MainNetParams MAINPARAMS = MainNetParams.get();
    static final TestNet3Params TESTNETPARAMS = TestNet3Params.get();
    static PeerGroup peerGroup;
    static BlockChain blockChain;

    private final NetworkParameters params;
    private final String mnlistdiffFilename;
    private final String blockchainFile;
    private final int protocolVersion;
    private final int height;

    public QuorumStateValidateQuorumsTest(NetworkParameters params, String qrInfoFilename, String blockchainFile, int protocolVersion, int height) {
        this.params = params;
        this.mnlistdiffFilename = qrInfoFilename;
        this.blockchainFile = blockchainFile;
        this.protocolVersion = protocolVersion;
        this.height = height;
    }

    @Parameterized.Parameters(name = "QuorumStateTest {index} (qrinfo={1}, manager={2})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
//                {
//                        MAINPARAMS,
//                        "mnlistdiff-mainnet-0-1888465-70227.dat",
//                        "testnet-after19.2HF.spvchain",
//                        70228,
//                        850744
//                },
                {
                    TESTNETPARAMS,
                        "mnlistdiff-testnet-0-850798-70228-after19.2HF.dat",
                        "testnet-after19.2HF.spvchain",
                        70228,
                        850798
                },
                {
                        TESTNETPARAMS,
                        "mnlistdiff-testnet-0-849810-70228-before19.2HF.dat",
                        "testnet-849810.spvchain",
                        70228,
                        849810
                }
        });
    }

    private void initContext() throws BlockStoreException {
        if (context == null || !context.getParams().equals(params))
            context = new Context(params);

        blockChain = new BlockChain(context, new SPVBlockStore(params, new File(Objects.requireNonNull(SimplifiedMasternodesTest.class.getResource(blockchainFile)).getPath())));

        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        context.initDash(true, true);

        context.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }

    @Test
    public void processDiffMessage() throws BlockStoreException, IOException {
        try {
            initContext();
            // this is for mainnet
            URL datafile = Objects.requireNonNull(getClass().getResource(mnlistdiffFilename));

            InputStream stream = Files.newInputStream(new File(datafile.getPath()).toPath());

            byte[] buffer = new byte[(int) stream.available()];
            //noinspection ResultOfMethodCallIgnored
            stream.read(buffer);

            SimplifiedMasternodeListManager manager = new SimplifiedMasternodeListManager(context);
            context.setMasternodeListManager(manager);
            context.setDebugMode(true);

            SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(context.getParams(), buffer, protocolVersion);

            manager.processDiffMessage(null, mnlistdiff, false);

            assertEquals(height, manager.getQuorumListAtTip(LLMQParameters.LLMQType.LLMQ_400_60).getHeight());
            stream.close();
        } finally {
            context.setDebugMode(false);
            blockChain.getBlockStore().close();
        }
    }
}
