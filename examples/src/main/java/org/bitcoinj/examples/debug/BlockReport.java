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
package org.bitcoinj.examples.debug;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.quorums.LLMQParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class BlockReport extends Report {


    static Logger log = LoggerFactory.getLogger(TransactionReport.class);
    ArrayList<BlockInfo> blockList = new ArrayList<>();
    HashMap<Sha256Hash, BlockInfo> blockMap = new HashMap<>();

    public BlockReport(String dashClientPath, String confPath, NetworkParameters params) {
        super("block-report-", dashClientPath, confPath, params);
    }

    public void add(StoredBlock storedBlock) {
        BlockInfo blockInfo = new BlockInfo(storedBlock);
        blockList.add(blockInfo);
        blockMap.put(storedBlock.getHeader().getHash(), blockInfo);
    }

    public void setChainLock(StoredBlock storedBlock) {
        BlockInfo blockInfo = blockMap.get(storedBlock.getHeader().getHash());
        if (blockInfo != null) {
            blockInfo.chainLocked = true;
        }
    }

    @Override
    public void printReport() {
        try {
            FileWriter writer = new FileWriter(outputFile);
            writer.append("Block Id, Height, Mod, Locked, Core Locked\n");

            for (BlockInfo blockInfo : blockList) {
                Block block = blockInfo.storedBlock.getHeader();
                int cycleLength = LLMQParameters.fromType(block.getParams().getLlmqChainLocks()).getDkgInterval();

                if ((blockInfo.blockCore == null || !blockInfo.chainLocked || !blockInfo.blockCore.getBoolean("chainlock")) && dashClientPath.length() > 0) {
                    blockInfo.blockCore = runRPCCommand(String.format("getblock %s", block.getHash()));
                }
                String line = String.format("%s, %d, %d, %s, %s\n",
                        block.getHash(),
                        blockInfo.storedBlock.getHeight(),
                        blockInfo.storedBlock.getHeight() % cycleLength,
                        blockInfo.chainLocked,
                        blockInfo.blockCore != null && blockInfo.blockCore.getBoolean("chainlock")
                );
                writer.append(line);
            }
            writer.close();
        } catch (
                FileNotFoundException e) {
            log.error("file not found", e);
            throw new RuntimeException(e);
        } catch (
                IOException e) {
            log.error("io error", e);
            throw new RuntimeException(e);
        }
    }
}
