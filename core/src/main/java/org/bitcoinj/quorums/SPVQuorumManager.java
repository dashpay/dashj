package org.bitcoinj.quorums;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class SPVQuorumManager extends QuorumManager {

    private static final Logger log = LoggerFactory.getLogger(SPVQuorumManager.class);

    public SPVQuorumManager(Context context, SimplifiedMasternodeListManager masternodeListManager) {
        super(context);
    }

    // all these methods will lock cs_main for a short period of time
    @Override
    public Quorum getQuorum(LLMQParameters.LLMQType llmqType, Sha256Hash quorumHash) {
        return null;
    }

    @Override
    public Quorum getNewestQuorum(LLMQParameters.LLMQType llmqType) {
        return null;
    }

    @Override
    public ArrayList<Quorum> scanQuorums(final LLMQParameters.LLMQType llmqType, final long maxCount) {
        final ArrayList<Quorum> result = new ArrayList<Quorum>();
        SimplifiedQuorumList list = context.masternodeListManager.getQuorumListAtTip();
        final LLMQParameters llmqParameters = context.getParams().getLlmqs().get(llmqType);
        list.forEachQuorum(true, new SimplifiedQuorumList.ForeachQuorumCallback() {
            int found = 0;
            @Override
            public void processQuorum(FinalCommitment fqc) {
                if(fqc.llmqType == llmqType.getValue() && found < maxCount) {
                    found++;
                    Quorum quorum = new Quorum(llmqParameters, fqc);
                    result.add(quorum);
                }

            }
        });
        return result;
    }

    // this one is cs_main-free
    @Override
    public ArrayList<Quorum> scanQuorums(LLMQParameters.LLMQType llmqType, StoredBlock start, long maxCount) {
        if(start != null && start.getHeight() > context.masternodeListManager.getQuorumListAtTip().getHeight())
            log.warn("quorum list is old, the quorums may not match");
        return scanQuorums(llmqType, maxCount);
    }

    boolean isQuorumActive(LLMQParameters.LLMQType llmqType, Sha256Hash quorumHash) {

        final LLMQParameters llmqParameters = context.getParams().getLlmqs().get(llmqType);

        // sig shares and recovered sigs are only accepted from recent/active quorums
        // we allow one more active quorum as specified in consensus, as otherwise there is a small window where things could
        // fail while we are on the brink of a new quorum
        ArrayList<Quorum> quorums = scanQuorums(llmqType, (int)llmqParameters.signingActiveQuorumCount + 1);
        for (Quorum q : quorums) {
            if (q.commitment.quorumHash.equals(quorumHash)) {
                return true;
            }
        }
        return false;
    }
}
