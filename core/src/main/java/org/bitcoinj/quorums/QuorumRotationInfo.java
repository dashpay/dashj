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

import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;

import java.io.IOException;
import java.io.OutputStream;

public class QuorumRotationInfo extends Message {

    private long creationHeight;
    QuorumSnapshot quorumSnapshotAtHMinusC;
    QuorumSnapshot quorumSnapshotAtHMinus2C;
    QuorumSnapshot quorumSnapshotAtHMinus3C;
    SimplifiedMasternodeListDiff mnListDiffTip;
    SimplifiedMasternodeListDiff mnListDiffAtHMinusC;
    SimplifiedMasternodeListDiff mnListDiffAtHMinus2C;
    SimplifiedMasternodeListDiff mnListDiffAtHMinus3C;

    QuorumRotationInfo(NetworkParameters params) {
        super(params);

    }

    public QuorumRotationInfo(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        creationHeight = readUint32();

        quorumSnapshotAtHMinusC = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinusC.getMessageSize();
        quorumSnapshotAtHMinus2C = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinus2C.getMessageSize();
        quorumSnapshotAtHMinus3C = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinus3C.getMessageSize();

        mnListDiffTip = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffTip.getMessageSize();
        mnListDiffAtHMinusC = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffAtHMinusC.getMessageSize();
        mnListDiffAtHMinus2C = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffAtHMinus2C.getMessageSize();
        mnListDiffAtHMinus3C = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffAtHMinus3C.getMessageSize();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(creationHeight, stream);

        quorumSnapshotAtHMinusC.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus2C.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus3C.bitcoinSerialize(stream);

        mnListDiffTip.bitcoinSerialize(stream);
        mnListDiffAtHMinusC.bitcoinSerialize(stream);
        mnListDiffAtHMinus2C.bitcoinSerialize(stream);
        mnListDiffAtHMinus3C.bitcoinSerialize(stream);
    }

    public long getCreationHeight() {
        return creationHeight;
    }

    public SimplifiedMasternodeListDiff getMnListDiffTip() {
        return mnListDiffTip;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtHMinusC() {
        return mnListDiffAtHMinusC;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtHMinus2C() {
        return mnListDiffAtHMinus2C;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtHMinus3C() {
        return mnListDiffAtHMinus3C;
    }

    public QuorumSnapshot getQuorumSnapshotAtHMinusC() {
        return quorumSnapshotAtHMinusC;
    }

    public QuorumSnapshot getQuorumSnapshotAtHMinus2C() {
        return quorumSnapshotAtHMinus2C;
    }

    public QuorumSnapshot getQuorumSnapshotAtHMinus3C() {
        return quorumSnapshotAtHMinus3C;
    }

    @Override
    public String toString() {
        return "QuorumRotationInfo{" +
                "creationHeight=" + creationHeight +
                ", quorumSnapshotAtHMinusC=" + quorumSnapshotAtHMinusC +
                ", quorumSnapshotAtHMinus2C=" + quorumSnapshotAtHMinus2C +
                ", quorumSnapshotAtHMinus3C=" + quorumSnapshotAtHMinus3C +
                ", mnListDiffTip=" + mnListDiffTip +
                ", mnListDiffAtHMinusC=" + mnListDiffAtHMinusC +
                ", mnListDiffAtHMinus2C=" + mnListDiffAtHMinus2C +
                ", mnListDiffAtHMinus3C=" + mnListDiffAtHMinus3C +
                '}';
    }

    void setCreationHeight(long creationHeight) {
        this.creationHeight = creationHeight;
    }

    void setQuorumSnapshotAtHMinusC(QuorumSnapshot quorumSnapshotAtHMinusC) {
        this.quorumSnapshotAtHMinusC = quorumSnapshotAtHMinusC;
    }

    void setQuorumSnapshotAtHMinus2C(QuorumSnapshot quorumSnapshotAtHMinusC) {
        this.quorumSnapshotAtHMinus2C = quorumSnapshotAtHMinusC;
    }

    public void setQuorumSnapshotAtHMinus3C(QuorumSnapshot quorumSnapshotAtHMinus3C) {
        this.quorumSnapshotAtHMinus3C = quorumSnapshotAtHMinus3C;
    }

    public void setMnListDiffTip(SimplifiedMasternodeListDiff mnListDiffTip) {
        this.mnListDiffTip = mnListDiffTip;
    }

    public void setMnListDiffAtHMinusC(SimplifiedMasternodeListDiff mnListDiffAtHMinusC) {
        this.mnListDiffAtHMinusC = mnListDiffAtHMinusC;
    }

    public void setMnListDiffAtHMinus2C(SimplifiedMasternodeListDiff mnListDiffAtHMinus2C) {
        this.mnListDiffAtHMinus2C = mnListDiffAtHMinus2C;
    }

    public void setMnListDiffAtHMinus3C(SimplifiedMasternodeListDiff mnListDiffAtHMinus3C) {
        this.mnListDiffAtHMinus3C = mnListDiffAtHMinus3C;
    }
}
