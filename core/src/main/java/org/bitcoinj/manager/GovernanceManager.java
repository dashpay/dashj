package org.bitcoinj.manager;

import org.bitcoinj.core.*;
import org.bitcoinj.governance.GovernanceObject;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by HashEngineering on 5/11/2018.
 */
public class GovernanceManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(GovernanceManager.class);
    // critical section to protect the inner data structures
    ReentrantLock lock = Threading.lock("GovernanceManager");

    public static final int MAX_GOVERNANCE_OBJECT_DATA_SIZE = 16 * 1024;
    public static final int MIN_GOVERNANCE_PEER_PROTO_VERSION = 70208;
    public static final int GOVERNANCE_FILTER_PROTO_VERSION = 70208;

    public static int nSubmittedFinalBudget;
    public static final int MAX_TIME_FUTURE_DEVIATION = 60*60;
    public static final int RELIABLE_PROPAGATION_TIME = 60;

    public static final String SERIALIZATION_VERSION_STRING = "CGovernanceManager-Version-12";

    public GovernanceManager(Context context) {
        super(context);
    }

    public GovernanceManager(NetworkParameters params, byte [] payload, int cursor) {
        super(params, payload, cursor);
    }

    public int calculateMessageSizeInBytes() {
        int size = 0;
        lock.lock();
        try {
            size += 1;
            size *= 1000;


            return size;
        }
        finally {
            lock.unlock();
        }
    }
    @Override
    protected void parse() throws ProtocolException {

        String version = readStr();

        if(!version.equals(SERIALIZATION_VERSION_STRING))
            clear();

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        lock.lock();
        try {
            stream.write(new VarInt(SERIALIZATION_VERSION_STRING.length()).encode());
            stream.write(SERIALIZATION_VERSION_STRING.getBytes());
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            unCache();

        } finally {
            lock.unlock();
        }
    }

    @Override
    public AbstractManager createEmpty() {
        return new GovernanceManager(Context.get());
    }

    @Override
    public void checkAndRemove() {
        lock.lock();
        try {
        } finally {
            lock.unlock();
        }
    }
    public void processGovernanceObject(Peer peer, GovernanceObject governanceObject) {

    }

    public void processGovernanceObjectVote(Peer peer, GovernanceObject governanceObjectVote) {

    }
}
