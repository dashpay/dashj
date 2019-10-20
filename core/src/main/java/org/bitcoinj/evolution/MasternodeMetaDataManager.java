package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.governance.GovernanceObject;

import java.util.ArrayList;

public class MasternodeMetaDataManager extends AbstractManager {

    public MasternodeMetaDataManager(Context context) {
        super(context);
    }

    public boolean addGovernanceVote(TransactionOutPoint outPoint, Sha256Hash hash) {
        return false;
    }

    public void removeGovernanceObject(Sha256Hash governanceObject) {

    }

    public ArrayList<Sha256Hash> getAndClearDirtyGovernanceObjectHashes() {
        return new ArrayList<Sha256Hash>();
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
