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

import com.google.common.collect.Lists;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class QuorumSnapshot extends Message {

    private ArrayList<Boolean> activeQuorumMembers;
    private int skipListMode;
    private ArrayList<Integer> skipList;

    public QuorumSnapshot(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public QuorumSnapshot(int activeQuorumMembersCount) {
        activeQuorumMembers = new ArrayList<>(activeQuorumMembersCount);
        for (int i = 0; i < activeQuorumMembersCount; ++i) {
            activeQuorumMembers.add(false);
        }
        skipList = new ArrayList<>();
        skipListMode = SnapshotSkipMode.MODE_INVALID.getValue();
    }

    public QuorumSnapshot(List<Boolean> activeQuorumMembers, int skipListMode, List<Integer> skipList) {
        this.activeQuorumMembers = new ArrayList<>(activeQuorumMembers);
        this.skipListMode = skipListMode;
        this.skipList = new ArrayList<>(skipList);
    }

    @Override
    protected void parse() throws ProtocolException {
        skipListMode = (int)readUint32();
        activeQuorumMembers = readBooleanArrayList();
        skipList = readIntArrayList();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(skipListMode, stream);
        Utils.booleanArrayListToStream(activeQuorumMembers, stream);
        Utils.intArrayListToStream(skipList, stream);
    }

    public ArrayList<Boolean> getActiveQuorumMembers() {
        return activeQuorumMembers;
    }

    public void setActiveQuorumMember(int index, boolean isActive) {
        activeQuorumMembers.set(index, isActive);
    }

    public long getSkipListMode() {
        return skipListMode;
    }

    public List<Integer> getSkipList() {
        return skipList;
    }

    public void setSkipListMode(SnapshotSkipMode skipListMode) {
        this.skipListMode = skipListMode.getValue();
    }

    @Override
    public String toString() {
        return "QuorumSnapshot{" +
                "activeQuorumMembers=" + activeQuorumMembers +
                ", skipListMode=" + SnapshotSkipMode.fromValue(skipListMode) +
                ", skipList=" + skipList +
                '}';
    }

    public void setSkipList(ArrayList<Integer> skipList) {
        this.skipList = skipList;
    }
}
