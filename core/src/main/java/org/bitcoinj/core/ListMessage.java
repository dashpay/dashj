/*
 * Copyright 2011 Google Inc.
 * Copyright 2015 Andreas Schildbach
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.MoreObjects;

/**
 * <p>Abstract superclass of classes with list based payload, ie InventoryMessage and GetDataMessage.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public abstract class ListMessage extends Message {

    private long arrayLen;
    // For some reason the compiler complains if this is inside InventoryItem
    protected List<InventoryItem> items;

    public static final long MAX_INVENTORY_ITEMS = 50000;

    public ListMessage(NetworkParameters params, byte[] bytes) throws ProtocolException {
        super(params, bytes, 0);
    }

    public ListMessage(NetworkParameters params, byte[] payload, MessageSerializer serializer, int length)
            throws ProtocolException {
        super(params, payload, 0, serializer, length);
    }

    public ListMessage(NetworkParameters params) {
        super(params);
        items = new ArrayList<>();
        length = 1; //length of 0 varint;
    }

    public List<InventoryItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(InventoryItem item) {
        unCache();
        length -= VarInt.sizeOf(items.size());
        items.add(item);
        length += VarInt.sizeOf(items.size()) + InventoryItem.MESSAGE_LENGTH;
    }

    public void removeItem(int index) {
        unCache();
        length -= VarInt.sizeOf(items.size());
        items.remove(index);
        length += VarInt.sizeOf(items.size()) - InventoryItem.MESSAGE_LENGTH;
    }

    @Override
    protected void parse() throws ProtocolException {
        arrayLen = readVarInt();
        if (arrayLen > MAX_INVENTORY_ITEMS)
            throw new ProtocolException("Too many items in INV message: " + arrayLen);
        length = (int) (cursor - offset + (arrayLen * InventoryItem.MESSAGE_LENGTH));

        // An inv is vector<CInv> where CInv is int+hash. The int is either 1 or 2 for tx or block.
        items = new ArrayList<>((int) arrayLen);
        for (int i = 0; i < arrayLen; i++) {
            if (cursor + InventoryItem.MESSAGE_LENGTH > payload.length) {
                throw new ProtocolException("Ran off the end of the INV");
            }
            int typeCode = (int) readUint32();
            InventoryItem.Type type;
            // See ppszTypeName in net.h
            switch (typeCode) {
                case 0:
                    type = InventoryItem.Type.Error;
                    break;
                case 1:
                    type = InventoryItem.Type.Transaction;
                    break;
                case 2:
                    type = InventoryItem.Type.Block;
                    break;
                case 3:
                    type = InventoryItem.Type.FilteredBlock;
                    break;
                case 4:
                    type = InventoryItem.Type.TransactionLockRequest;
                    break;
                case 5:
                    type = InventoryItem.Type.TransactionLockVote;
                    break;
                case 6:
                    type = InventoryItem.Type.Spork;
                    break;
                case 7:
                    type = InventoryItem.Type.MasternodePaymentVote;
                    break;
                case 8:
                    type = InventoryItem.Type.MasternodePaymentBlock;
                    break;
                case 9:
                    type = InventoryItem.Type.BudgetVote;
                    break;
                case 10:
                    type = InventoryItem.Type.BudgetProposal;
                    break;
                case 11:
                    type = InventoryItem.Type.BudgetFinalized;
                    break;
                case 12:
                    type = InventoryItem.Type.BudgetFinalizedVote;
                    break;
                case 13:
                    type = InventoryItem.Type.MasternodeQuorum;
                    break;
                case 14:
                    type = InventoryItem.Type.MasternodeAnnounce;
                    break;
                case 15:
                    type = InventoryItem.Type.MasternodePing;
                    break;
                case 16:
                    type = InventoryItem.Type.DarkSendTransaction;
                    break;
                case 17:
                    type = InventoryItem.Type.GovernanceObject;
                    break;
                case 18:
                    type = InventoryItem.Type.GovernanceObjectVote;
                    break;
                case 19:
                    type = InventoryItem.Type.MasternodeVerify;
                    break;
                case 20:
                    type = InventoryItem.Type.CompactBlock;
                    break;
                case 21:
                    type = InventoryItem.Type.QuorumFinalCommitment;
                    break;
                case 22:
                    type = InventoryItem.Type.QuorumDummyCommitment;
                    break;
                case 23:
                    type = InventoryItem.Type.QuorumContribution;
                    break;
                case 24:
                    type = InventoryItem.Type.QuorumComplaint;
                    break;
                case 25:
                    type = InventoryItem.Type.QuorumJustifciation;
                    break;
                case 26:
                    type = InventoryItem.Type.QuorumPrematureCommitment;
                    break;
                case 27:
                    type = InventoryItem.Type.QuorumDebugStatus;
                    break;
                case 28:
                    type = InventoryItem.Type.QuorumRecoveredSignature;
                    break;
                case 29:
                    type = InventoryItem.Type.ChainLockSignature;
                    break;
                case 30:
                    type = InventoryItem.Type.InstantSendLock;
                    break;
                case 31:
                    type = InventoryItem.Type.InstantSendDeterministicLock;
                    break;
                default:
                    type = InventoryItem.Type.None;
                    break;
            }
            InventoryItem item = new InventoryItem(type, readHash());
            items.add(item);
        }
        payload = null;
    }

    @Override
    public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(items.size()).encode());
        for (InventoryItem i : items) {
            // Write out the type code.
            Utils.uint32ToByteStreamLE(i.type.ordinal(), stream);
            // And now the hash.
            stream.write(i.hash.getReversedBytes());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return items.equals(((ListMessage)o).items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        helper.addValue(items);
        return helper.toString();
    }
}
