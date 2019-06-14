package org.bitcoinj.evolution;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public class DeterministicMasternodeListManager extends AbstractManager {
    public static final int SNAPSHOT_LIST_PERIOD = 576; // once per day
    public static final int LISTS_CACHE_SIZE = 576;

    HashMap<Sha256Hash, DeterministicMasternodeList> mnListsCache;
    int tipHeight;
    Sha256Hash tipBlockHash;

    public DeterministicMasternodeListManager(Context context) {
        super(context);
        tipBlockHash = Sha256Hash.ZERO_HASH;
    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public AbstractManager createEmpty() {
        return new DeterministicMasternodeListManager(Context.get());
    }

    @Override
    public void checkAndRemove() {

    }

    @Override
    public void clear() {

    }

    @Override
    protected void parse() throws ProtocolException {

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
    }

    @Override
    public void close() {

    }
}
