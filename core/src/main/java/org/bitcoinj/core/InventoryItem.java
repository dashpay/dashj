/*
 * Copyright 2011 Google Inc.
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

import com.google.common.base.Objects;

public class InventoryItem {
    
    /**
     * 4 byte uint32 type field + 32 byte hash
     */
    static final int MESSAGE_LENGTH = 36;
    
    public enum Type {
        Error,  //0
        Transaction,
        Block,
        FilteredBlock,
        // Dash message types
        // NOTE: declare non-implmented here, we must keep this enum consistent and backwards compatible
        TransactionLockRequest, //Dash InstantSend - obsolete
        TransactionLockVote,         //Dash InstantSend - obsolete
        Spork,
        MasternodePaymentVote,
        MasternodePaymentBlock, // reusing, was MasterNodeScanningError
        BudgetVote,          // depreciated since 12.1
        BudgetProposal,      // depreciated since 12.1
        BudgetFinalized,     // depreciated since 12.1
        BudgetFinalizedVote, // depreciated since 12.1
        MasternodeQuorum,    // not implemented
        MasternodeAnnounce,
        MasternodePing,
        DarkSendTransaction,
        GovernanceObject,
        GovernanceObjectVote,
        MasternodeVerify,
        CompactBlock,
        QuorumFinalCommitment,
        QuorumDummyCommitment,
        QuorumContribution,
        QuorumComplaint,
        QuorumJustifciation,
        QuorumPrematureCommitment,
        QuorumDebugStatus,
        QuorumRecoveredSignature,
        ChainLockSignature,
        InstantSendLock,
        InstantSendDeterministicLock,
        None,
    }

    public final Type type;
    public final Sha256Hash hash;

    public InventoryItem(Type type, Sha256Hash hash) {
        this.type = type;
        this.hash = hash;
    }

    @Override
    public String toString() {
        return type + ": " + hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryItem other = (InventoryItem) o;
        return type == other.type && hash.equals(other.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, hash);
    }
}
