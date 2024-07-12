package org.bitcoinj.evolution;

import org.bitcoinj.core.*;

import java.util.ArrayList;
import java.util.List;

public class MasternodeMetaDataManager extends AbstractManager {

    public MasternodeMetaDataManager(Context context) {
        super(context);
    }

    public boolean addGovernanceVote(TransactionOutPoint outPoint, Sha256Hash hash) {
        return false;
    }

    public void removeGovernanceObject(Sha256Hash governanceObject) {

    }

    public List<Sha256Hash> getAndClearDirtyGovernanceObjectHashes() {
        return new ArrayList<>();
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
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    protected void parse() throws ProtocolException {

    }
}
