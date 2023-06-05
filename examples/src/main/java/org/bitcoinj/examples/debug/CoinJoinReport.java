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

import com.google.common.collect.Lists;
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinBroadcastTx;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CoinJoinReport extends Report {


    static Logger log = LoggerFactory.getLogger(TransactionReport.class);
    ArrayList<BlockInfo> blockList = new ArrayList<>();
    HashMap<Sha256Hash, BlockInfo> blockMap = new HashMap<>();
    HashMap<Sha256Hash, List<CoinJoinBroadcastTx>> blockTxMap = new HashMap<>();

    public CoinJoinReport(String dashClientPath, String confPath, NetworkParameters params) {
        super("coinjoin-report-", dashClientPath, confPath, params);
    }

    public void add(StoredBlock storedBlock, List<CoinJoinBroadcastTx> listDSTX) {
        BlockInfo blockInfo = new BlockInfo(storedBlock);
        blockList.add(blockInfo);
        blockMap.put(storedBlock.getHeader().getHash(), blockInfo);
        blockTxMap.put(storedBlock.getHeader().getHash(), Lists.newArrayList(listDSTX));
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
            writer.append("Block Id, Block Number, 10, 1, 0.1, 0.01, 0.001\n");

            for (BlockInfo blockInfo : blockList) {
                Block block = blockInfo.storedBlock.getHeader();

                if ((blockInfo.blockCore == null || !blockInfo.chainLocked || !blockInfo.blockCore.getBoolean("chainlock")) && dashClientPath.length() > 0) {
                    blockInfo.blockCore = runRPCCommand(String.format("getblock %s", block.getHash()));
                }

                int[] countDenom = new int[CoinJoin.getStandardDenominations().size()];
                for (int denomination = 0; denomination < CoinJoin.getStandardDenominations().size(); ++denomination) {
                    Coin amount = CoinJoin.getStandardDenominations().get(denomination);
                    for (CoinJoinBroadcastTx dstx : blockTxMap.get(block.getHash())) {
                        Transaction tx = dstx.getTx();
                        if (tx.getOutput(0).isDenominated()) {
                            if (tx.getOutput(0).getValue().equals(amount)) {
                                countDenom[denomination]++;
                            }
                        }
                    }
                }

                String line = String.format("%s, %d, %d, %d, %d, %d, %d\n",
                        block.getHash(),
                        blockInfo.storedBlock.getHeight(),
                        countDenom[0],
                        countDenom[1],
                        countDenom[2],
                        countDenom[3],
                        countDenom[4]
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
