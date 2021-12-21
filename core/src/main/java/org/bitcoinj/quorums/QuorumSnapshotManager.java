/*
 * Copyright 2021 Dash Core Group
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

package org.bitcoinj.quorums;

import com.google.common.collect.Maps;
import org.bitcoinj.core.AbstractManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class QuorumSnapshotManager extends AbstractManager {

    private static final Logger log = LoggerFactory.getLogger(InstantSendManager.class);
    ReentrantLock lock = Threading.lock("QuorumSnapshotManager");

    private HashMap<Sha256Hash, QuorumSnapshot> quorumSnapshotCache;

    public QuorumSnapshotManager(Context context) {
        super(context);
        quorumSnapshotCache = Maps.newHashMap();
    }

    @Override
    protected void parse() throws ProtocolException {

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public void checkAndRemove() {

    }

    @Override
    public void clear() {

    }

    @Override
    public AbstractManager createEmpty() {
        return new QuorumSnapshotManager(Context.get());
    }

    @Override
    public void close() {

    }

    static Sha256Hash getSnapshotHash(LLMQParameters.LLMQType type, StoredBlock block) {
        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream();
            Utils.uint32ToByteStreamLE(type.getValue(), stream);
            stream.write(block.getHeader().getHash().getReversedBytes());
            return Sha256Hash.twiceOf(stream.toByteArray());
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @GuardedBy("lock")
    public QuorumSnapshot getSnapshotForBlock(LLMQParameters.LLMQType type, StoredBlock block) {
        Sha256Hash snapshotHash = getSnapshotHash(type, block);
        return quorumSnapshotCache.get(snapshotHash);
    }

    @GuardedBy("lock")
    public void storeSnapshotForBlock(LLMQParameters.LLMQType type, StoredBlock block, QuorumSnapshot snapshot) {
        Sha256Hash snapshotHash = getSnapshotHash(type, block);
        quorumSnapshotCache.put(snapshotHash, snapshot);
    }
}
