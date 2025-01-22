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
package org.bitcoinj.examples;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;

import java.util.concurrent.ExecutionException;


public class ConvertBootStrapToManagerFile {

    static Context context;
    static PeerGroup peerGroup;
    static BlockChain blockChain;
    static DashSystem system;
    private static void initContext(NetworkParameters params) throws BlockStoreException {
        context = new Context(params);
        system = new DashSystem(context);
        if (blockChain == null) {
            blockChain = new BlockChain(context, new MemoryBlockStore(params));
        }
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        system.initDash(true, true);

        system.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("ConvertBootStrapToManagerFile network mnlistdiff qrinfo");
            System.out.println("  one or more arguments are missing!");
            return;
        }
        BriefLogFormatter.init();
        String network = args[0];
        String mnlistdiffFilename = args[1];
        String qrinfoFilename = args[2];
        int fileVersion = 5;
        if (args.length == 4) {
            fileVersion = Integer.parseInt(args[3]);
        }

        NetworkParameters params;
        switch (network) {
            case "testnet":
                params = TestNet3Params.get();
                break;
            default:
                params = MainNetParams.get();
                break;
        }
        try {
            initContext(params);
            //Context context = Context.getOrCreate(params);
            //context.initDash(true, true);
            system.initDashSync(".", "convert");
            system.masternodeListManager.setBootstrap(mnlistdiffFilename, qrinfoFilename, fileVersion);
            system.masternodeListManager.resetMNList(true, true);
            try {
                system.masternodeListManager.waitForBootstrapLoaded();
                system.masternodeListManager.save();

                Thread.sleep(5000);
                System.out.println("Save complete.");
            } catch (ExecutionException e) {
                System.out.println("Failed to load bootstrap");
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                System.out.println("Failed to load bootstrap");
                throw new RuntimeException(e);
            }
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }

    }
}
