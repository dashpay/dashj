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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.quorums.LLMQParameters;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

public class TransactionReport extends Report {

    static Logger log = LoggerFactory.getLogger(TransactionReport.class);
    ArrayList<TransactionInfo> txList = new ArrayList<>();
    HashMap<Sha256Hash, TransactionInfo> txMap = new HashMap<>();



    public TransactionReport(String dashClientPath, String confPath, NetworkParameters params) {
        super("tx-report-", dashClientPath, confPath, params);
    }

    public void add(long timestamp, int blockReceived, Transaction tx) {
        TransactionInfo txInfo = new TransactionInfo(timestamp, blockReceived, tx);

        txList.add(txInfo);
        txMap.put(tx.getTxId(), txInfo);
    }



    @Override
    public void printReport() {
        try {
            FileWriter writer = new FileWriter(outputFile);
            int cycleLength = -1;
            writer.append("TxId, Block Received, Block Mined, In Mempool, IS Status, Core instantlock_internal, Block Rec. Mod, Cycle Hash, Quorum Hash:Index\n");
            for (TransactionInfo txInfo : txList) {
                if (cycleLength == -1) {
                    cycleLength = LLMQParameters.fromType(txInfo.tx.getParams().getLlmqDIP0024InstantSend()).getDkgInterval();
                }
                TransactionConfidence confidence = txInfo.tx.getConfidence();
                int blockMined = confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING ? confidence.getAppearedAtChainHeight() : -1;

                if ((txInfo.txCore == null || blockMined == -1 || blockMined == txInfo.blockRecieved) && dashClientPath.length() > 0) {
                    txInfo.txCore = runRPCCommand(String.format("getrawtransaction %s true", txInfo.tx.getTxId()));
                }
                String line = String.format("%s, %d, %d, %d, %s, %s, %d, %s, %s\n",
                        txInfo.tx.getTxId(),
                        txInfo.blockRecieved,
                        blockMined,
                        blockMined != -1 ? blockMined - txInfo.blockRecieved : 0,
                        confidence.getIXType(),
                        txInfo.txCore != null && txInfo.txCore.getBoolean("instantlock_internal"),
                        txInfo.blockRecieved % cycleLength,
                        confidence.getIXType() != TransactionConfidence.IXType.IX_NONE && confidence.getInstantSendlock() != null ? confidence.getInstantSendlock().getCycleHash() : "",
                        (confidence.getIXType() != TransactionConfidence.IXType.IX_NONE && confidence.getIXType() != TransactionConfidence.IXType.IX_REQUEST && confidence.getInstantSendlock() != null) ? (confidence.getInstantSendlock().getQuorumHash() + ":" + confidence.getInstantSendlock().getQuorumIndex()):"");
                writer.append(line);
            }
            writer.close();
        } catch (FileNotFoundException e) {
            log.error("file not found", e);
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error("io error", e);
            throw new RuntimeException(e);
        }
    }
}
