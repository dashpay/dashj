/**
 * Copyright 2014 Hash Engineering Solutions
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
package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class TransactionLock {

    private static final Logger log = LoggerFactory.getLogger(Transaction.class);
    public int blockHeight;
    public Sha256Hash txHash;
    public ArrayList<TransactionLockVote> vecConsensusVotes;
    public int expiration;
    public int timeout;


    DarkCoinSystem system;

    public TransactionLock()
    {
        vecConsensusVotes = new ArrayList<TransactionLockVote>();
    }
    public TransactionLock(int blockHeight, int expiration, int timeout, Sha256Hash txHash)
    {
        this.blockHeight = blockHeight;
        this.txHash = txHash;
        this.expiration = expiration;
        this.timeout = timeout;
        vecConsensusVotes = new ArrayList<TransactionLockVote>();
    }



    public String toString() {
        return "TransactionLock";
    }
    public Sha256Hash getHash()
    {
        return txHash;
    }


    public int countSignatures()
    {
        return vecConsensusVotes.size();
    }

    public void addSignature(TransactionLockVote cv)
    {
        vecConsensusVotes.add(cv);
    }


}
