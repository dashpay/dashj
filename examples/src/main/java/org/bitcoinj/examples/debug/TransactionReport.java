package org.bitcoinj.examples.debug;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class TransactionReport {

    static Logger log = LoggerFactory.getLogger(TransactionReport.class);
    ArrayList<TransactionInfo> txList = new ArrayList<>();
    HashMap<Sha256Hash, TransactionInfo> txMap = new HashMap<>();

    File outputFile = new File("tx-report.csv");

    public TransactionReport() {

    }

    public void add(long timestamp, int blockReceived, Transaction tx) {
        TransactionInfo txInfo = new TransactionInfo(timestamp, blockReceived, tx);

        txList.add(txInfo);
        txMap.put(tx.getTxId(), txInfo);
    }

    public void printReport() {
        try {
            FileWriter writer = new FileWriter(outputFile);
            writer.append("TxId, Block Received, Block Mined, IS Status, Block Rec. Mod 48\n");
            for (TransactionInfo txInfo : txList) {
                TransactionConfidence confidence = txInfo.tx.getConfidence();
                String line = String.format("%s, %d, %d, %s, %d\n", txInfo.tx.getTxId(), txInfo.blockRecieved,
                        confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING ? confidence.getAppearedAtChainHeight() : -1, confidence.getIXType(),
                        txInfo.blockRecieved % 48);
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
