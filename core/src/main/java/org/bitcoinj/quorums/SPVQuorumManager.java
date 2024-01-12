package org.bitcoinj.quorums;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class SPVQuorumManager extends QuorumManager {

    private static final Logger log = LoggerFactory.getLogger(SPVQuorumManager.class);
    SimplifiedMasternodeListManager masternodeListManager;

    public SPVQuorumManager(Context context, SimplifiedMasternodeListManager masternodeListManager) {
        super(context);
        this.masternodeListManager = masternodeListManager;
    }

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
        return scanQuorums(llmqType, blockChain.getChainHead(), maxCount);
    }

    @Override
    public ArrayList<Quorum> scanQuorums(final LLMQParameters.LLMQType llmqType, StoredBlock start, final long maxCount) {
        Preconditions.checkNotNull(start, "The start block must not be null");
        if(start != null && start.getHeight() > masternodeListManager.getQuorumListAtTip(llmqType).getHeight())
            log.warn("quorum list is old, the quorums may not match");
        final ArrayList<Quorum> result = new ArrayList<Quorum>();
        SimplifiedQuorumList list = masternodeListManager.getQuorumListForBlock(start.getHeader().getHash(), llmqType);
        if (list == null) {
            // if the list isn't found, use the most recent list
            list = masternodeListManager.getQuorumListAtTip(llmqType);
            log.warn("quorum list for " + start.getHeight() + " not found, using most recent quorum list: " + list.getHeight());
            if (list == null)
                return result;  // return empty list
        }
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

    boolean isQuorumActive(LLMQParameters.LLMQType llmqType, Sha256Hash quorumHash) {

        final LLMQParameters llmqParameters = context.getParams().getLlmqs().get(llmqType);

        // sig shares and recovered sigs are only accepted from recent/active quorums
        // we allow one more active quorum as specified in consensus, as otherwise there is a small window where things could
        // fail while we are on the brink of a new quorum
        ArrayList<Quorum> quorums = scanQuorums(llmqType, (int)llmqParameters.signingActiveQuorumCount + 1);
        for (Quorum q : quorums) {
            if (q.getCommitment().quorumHash.equals(quorumHash)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
    }
}
