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
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.coinjoin.CoinJoinConstants;
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

    static class BlockStats {
        Sha256Hash blockHash;
        int blockHeight;
        long blockTime;
        int mix10;
        int mix1;
        int mixTenth;
        int mixHundredth;
        int mixThousandth;

        public BlockStats(Sha256Hash blockHash, int blockHeight, long blockTime, int mix10, int mix1, int mixTenth, int mixHundredth, int mixThousandth) {
            this.blockHash = blockHash;
            this.blockHeight = blockHeight;
            this.blockTime = blockTime;
            this.mix10 = mix10;
            this.mix1 = mix1;
            this.mixTenth = mixTenth;
            this.mixHundredth = mixHundredth;
            this.mixThousandth = mixThousandth;
        }
    }
    ArrayList<BlockStats> stats = new ArrayList<>();

    public CoinJoinReport(String dashClientPath, String confPath, NetworkParameters params) {
        super("coinjoin-report-", dashClientPath, confPath, params);
    }

    public void add(StoredBlock storedBlock, List<CoinJoinBroadcastTx> listDSTX) {
        BlockInfo blockInfo = new BlockInfo(storedBlock);
        blockList.add(blockInfo);
        blockMap.put(storedBlock.getHeader().getHash(), blockInfo);
        blockTxMap.put(storedBlock.getHeader().getHash(), new ArrayList<>(listDSTX));
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
            writer.append("Block Id, Block Number, Block Time, 10, 1, 0.1, 0.01, 0.001, 15x0.01, 12x0.01+30x0.001\n");

            stats = new ArrayList<>();
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

                stats.add(new BlockStats(block.getHash(),
                        blockInfo.storedBlock.getHeight(),
                        blockInfo.storedBlock.getHeader().getTime().getTime(),
                        countDenom[0],
                        countDenom[1],
                        countDenom[2],
                        countDenom[3],
                        countDenom[4]));

                long mix15xTenths = calculateMix15xHundredths(stats);
                if (mix15xTenths != -1L) {
                    mix15xTenths /= 60000;
                }
                long mix12xHundredthsAnd30Thousandths = calculateMixWithHundredthsAndThousandths(stats, 12, 30);
                if (mix12xHundredthsAnd30Thousandths != -1L) {
                    mix12xHundredthsAnd30Thousandths /= 60000;
                }

                String line = String.format("%s, %d, %s, %d, %d, %d, %d, %d, %f, %f\n",
                        block.getHash(),
                        blockInfo.storedBlock.getHeight(),
                        blockInfo.storedBlock.getHeader().getTime(),
                        countDenom[0],
                        countDenom[1],
                        countDenom[2],
                        countDenom[3],
                        countDenom[4],
                        mix15xTenths != -1 ? (double) mix15xTenths / 60 : -1,
                        mix12xHundredthsAnd30Thousandths != -1 ? (double) mix12xHundredthsAnd30Thousandths / 60: -1
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

    private long calculateMix15xHundredths(ArrayList<BlockStats> stats) {
        try {
            int count = stats.size();
            int lastIndex = -1;
            int mixingTx = 0;
            for (int i = count - 1; i >= 0; i--) {
                if (stats.get(i).mixHundredth != 0) {
                    if (lastIndex == -1)
                        lastIndex = i;
                    // limit estimate to max sessions per block
                    mixingTx += Math.min(stats.get(i).mixHundredth, CoinJoinClientOptions.getSessions());
                    if (mixingTx >= 15) {
                        return stats.get(lastIndex).blockTime - stats.get(i - 1).blockTime;
                    }
                }
            }
            return -1;
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }
    private long calculateMixWithHundredthsAndThousandths(ArrayList<BlockStats> stats, int hundredths, int thousandths) {
        try {
            int count = stats.size();
            int lastIndex = -1;
            int mixingTx = 0;
            long mixingHundredths = -1;
            for (int i = count - 1; i >= 0; i--) {
                if (stats.get(i).mixHundredth != 0) {
                    if (lastIndex == -1)
                        lastIndex = i;
                    // limit estimate to max sessions per block
                    mixingTx += Math.min(stats.get(i).mixHundredth, CoinJoinClientOptions.getSessions());
                    if (mixingTx >= hundredths) {
                        mixingHundredths = stats.get(lastIndex).blockTime - stats.get(i - 1).blockTime;
                        break;
                    }
                }
            }
            lastIndex = -1;
            mixingTx = 0;
            long mixingThousandths = -1;
            for (int i = count - 1; i >= 0; i--) {
                if (stats.get(i).mixThousandth != 0) {
                    if (lastIndex == -1)
                        lastIndex = i;
                    // limit estimate to max sessions per block
                    mixingTx += Math.min(stats.get(i).mixThousandth, CoinJoinClientOptions.getSessions());
                    if (mixingTx >= thousandths) {
                        mixingThousandths = stats.get(lastIndex).blockTime - stats.get(i - 1).blockTime;
                        break;
                    }
                }
            }
            return mixingHundredths != -1 && mixingThousandths != -1  ?
                    Math.max(mixingHundredths, mixingThousandths) :
                    -1;
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }
}
