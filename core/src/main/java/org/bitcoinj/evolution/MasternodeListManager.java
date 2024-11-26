package org.bitcoinj.evolution;

import org.bitcoinj.core.AbstractManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.quorums.LLMQParameters;

import java.util.List;

public abstract class MasternodeListManager extends AbstractManager implements QuorumStateManager {

    public MasternodeListManager(Context context) {
        super(context);
    }

    public MasternodeListManager(NetworkParameters params, byte[] payload, int cursor) {
        super(params, payload, cursor);
    }

    public abstract List<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash);

    public abstract SimplifiedMasternodeList getListAtChainTip();
}
