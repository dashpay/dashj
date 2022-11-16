/*
 * Copyright 2019 Dash Core Group.
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

package org.bitcoinj.crypto;


import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.utils.Pair;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class BLSBatchVerifier<SourceId, MessageId>
{
    private class Message {
        MessageId msgId;
        Sha256Hash msgHash;
        BLSSignature sig;
        BLSPublicKey pubKey;
        Message(MessageId msgId, Sha256Hash msgHash, BLSSignature sig, BLSPublicKey pubKey) {
          this.msgId = msgId;
          this.msgHash = msgHash;
          this.sig = sig;
          this.pubKey = pubKey;
        }
    }

    boolean secureVerification;
    boolean perMessageFallback;
    int subBatchSize;

    HashMap<MessageId, Message> messages;
    HashMap<SourceId, Vector<Pair<MessageId, Message>>> messagesBySource;


    HashSet<SourceId> badSources;
    HashSet<MessageId> badMessages;

    public BLSBatchVerifier(boolean _secureVerification, boolean _perMessageFallback, int _subBatchSize) {
        this.secureVerification = (_secureVerification);
        perMessageFallback = (_perMessageFallback);
        subBatchSize = (_subBatchSize);
        messages = new HashMap<MessageId, Message>();
        messagesBySource = new HashMap<SourceId, Vector<Pair<MessageId, Message>>>();
        badSources = new HashSet<SourceId>();
        badMessages = new HashSet<MessageId>();
    }

    public BLSBatchVerifier(boolean secureVerification, boolean perMessageFallback) {
        this(secureVerification, perMessageFallback, 0);
    }

    public HashSet<SourceId> getBadSources() {
        return badSources;
    }

    public HashSet<MessageId> getBadMessages() {
        return badMessages;
    }

    public void pushMessage(SourceId sourceId, MessageId msgId, Sha256Hash msgHash, BLSSignature sig, BLSPublicKey pubKey)
    {
        checkArgument(sig.isValid() && pubKey.isValid());

        Message newMessage = new Message(msgId, msgHash, sig, pubKey);
        messages.put(msgId, newMessage);
        if(messagesBySource.containsKey(sourceId)) {
            messagesBySource.get(sourceId).add(new Pair<>(msgId, newMessage));
        } else {
            Vector<Pair<MessageId, Message>> vector = new Vector<Pair<MessageId, Message>>();
            vector.add(new Pair<>(msgId, newMessage));
            messagesBySource.put(sourceId, vector);
        }
        if (subBatchSize != 0 && messages.size() >= subBatchSize) {
            verify();
            clearMessages();
        }
    }

    void clearMessages()
    {
        messages.clear();
        messagesBySource.clear();
    }

    public void verify()
    {
        HashMap<Sha256Hash, Vector<Pair<MessageId, Message>>> byMessageHash = new HashMap<>();

        for (Map.Entry<MessageId, Message> entry : messages.entrySet()) {

            if(byMessageHash.containsKey(entry.getValue().msgHash)) {
                byMessageHash.get(entry.getValue().msgHash).add(new Pair<>(entry.getValue().msgId, entry.getValue()));
            } else {
                Vector<Pair<MessageId, Message>> vector = new Vector<>();
                vector.add(new Pair<>(entry.getValue().msgId, entry.getValue()));
                byMessageHash.put(entry.getValue().msgHash, vector);
            }
        }

        if (verifyBatch(byMessageHash)) {
            // full batch is valid
            return;
        }

        // revert to per-source verification
        for (Map.Entry<SourceId, Vector<Pair<MessageId, Message>>> p : messagesBySource.entrySet()) {
            boolean batchValid = false;

            // no need to verify it again if there was just one source
            if (messagesBySource.size() != 1) {
                byMessageHash.clear();
                for (Pair<MessageId, Message> it : p.getValue()) {
                    if (byMessageHash.containsKey(it.getSecond().msgHash)) {
                        byMessageHash.get(it.getSecond().msgHash).add(it);
                    } else {
                        Vector<Pair<MessageId, Message>> vector = new Vector<>();
                        vector.add(it);
                        byMessageHash.put(it.getSecond().msgHash, vector);
                    }
                }
                batchValid = verifyBatch(byMessageHash);
            }
            if (!batchValid) {
                badSources.add(p.getKey());

                if (perMessageFallback) {
                    // revert to per-message verification
                    if (p.getValue().size() == 1) {
                        // no need to re-verify a single message
                        badMessages.add(p.getValue().get(0).getSecond().msgId);
                    } else {
                        for (Pair<MessageId, Message> msgIt : p.getValue()) {
                            if (badMessages.contains(msgIt.getFirst())) {
                                // same message might be invalid from different source, so no need to re-verify it
                                continue;
                            }

                            Message msg = msgIt.getSecond();
                            if (!msg.sig.verifyInsecure(msg.pubKey, msg.msgHash)) {
                                badMessages.add(msg.msgId);
                            }
                        }
                    }
                }
            }
        }
    }

    // All Verify methods take ownership of the passed byMessageHash map and thus might modify the map. This is to avoid
    // unnecessary copies

    private boolean verifyBatch(HashMap<Sha256Hash, Vector<Pair<MessageId, Message>>> byMessageHash)
    {
        if (secureVerification) {
            return verifyBatchSecure(byMessageHash);
        } else {
            return verifyBatchInsecure(byMessageHash);
        }
    }

    private boolean verifyBatchInsecure(HashMap<Sha256Hash, Vector<Pair<MessageId, Message>>> byMessageHash)
    {
        BLSSignature aggSig = new BLSSignature();
        ArrayList<Sha256Hash> msgHashes = new ArrayList<>(messages.size());
        ArrayList<BLSPublicKey> pubKeys = new ArrayList<>(messages.size());
        HashSet<MessageId> dups = new HashSet<>();


        for (Map.Entry<Sha256Hash, Vector<Pair<MessageId, Message>>> p : byMessageHash.entrySet()) {
            Sha256Hash msgHash = p.getKey();

            BLSPublicKey aggPubKey = new BLSPublicKey();

            for (Pair<MessageId, Message> msgIt : p.getValue()) {
                Message msg = msgIt.getSecond();

                if (!dups.add(msg.msgId)) {
                    continue;
                }

                if (!aggSig.isValid()) {
                    aggSig = new BLSSignature(msg.sig);
                } else {
                    aggSig.aggregateInsecure(msg.sig);
                }

                if (!aggPubKey.isValid()) {
                    aggPubKey = new BLSPublicKey(msg.pubKey);
                } else {
                    aggPubKey.aggregateInsecure(msg.pubKey);
                }
            }

            if (!aggPubKey.isValid()) {
                // only duplicates for this msgHash
                continue;
            }

            msgHashes.add(msgHash);
            pubKeys.add(aggPubKey);
        }

        if (msgHashes.isEmpty()) {
            return true;
        }

        return aggSig.verifyInsecureAggregated(pubKeys, msgHashes);
    }

    private boolean verifyBatchSecure(HashMap<Sha256Hash, Vector<Pair<MessageId, Message>>> byMessageHash)
    {
        // Loop until the byMessageHash map is empty, which means that all messages were verified
        // The secure form of verification will only aggregate one message for the same message hash, even if multiple
        // exist (signed with different keys). This avoids the rogue public key attack.
        // This is slower than the insecure form as it requires more pairings
        while (!byMessageHash.isEmpty()) {
            if (!verifyBatchSecureStep(byMessageHash)) {
                return false;
            }
        }
        return true;
    }

    boolean verifyBatchSecureStep(HashMap<Sha256Hash, Vector<Pair<MessageId, Message>>> byMessageHash)
    {
        BLSSignature aggSig = new BLSSignature();
        ArrayList<Sha256Hash> msgHashes = new ArrayList<Sha256Hash>(messages.size());
        ArrayList<BLSPublicKey> pubKeys = new ArrayList<BLSPublicKey>(messages.size());
        HashSet<MessageId> dups = new HashSet<MessageId>();

        Iterator<Map.Entry<Sha256Hash, Vector<Pair<MessageId, Message>>>> it = byMessageHash.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Sha256Hash, Vector<Pair<MessageId, Message>>> entry = it.next();
            Sha256Hash msgHash = entry.getKey();
            Vector<Pair<MessageId, Message>> messageIts = entry.getValue();
            Message msg = messageIts.lastElement().getSecond();

            if (dups.add(msg.msgId)) {
                msgHashes.add(msgHash);
                pubKeys.add(msg.pubKey);

                if (!aggSig.isValid()) {
                    aggSig = new BLSSignature(msg.sig);
                } else {
                    aggSig.aggregateInsecure(msg.sig);
                }
            }

            messageIts.removeElementAt(messageIts.size()-1);
            if (messageIts.isEmpty()) {
                it.remove();
            }
        }

        checkState(!msgHashes.isEmpty());

        return aggSig.verifyInsecureAggregated(pubKeys, msgHashes);
    }

    public int getUniqueSourceCount() {
        return messagesBySource.size();
    }

}