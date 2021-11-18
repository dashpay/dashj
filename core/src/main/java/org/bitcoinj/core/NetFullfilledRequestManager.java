package org.bitcoinj.core;

import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Hash Engineering on 3/16/2016.
 */
public class NetFullfilledRequestManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(NetFullfilledRequestManager.class);

    HashMap<PeerAddress, HashMap<String, Long>> mapFulfilledRequests;

    // critical section to protect the inner data structures
    ReentrantLock lock = Threading.lock("NetFullfilledRequestManager");


    public NetFullfilledRequestManager(Context context) {
        super(context);
        mapFulfilledRequests = new HashMap<PeerAddress, HashMap<String, Long>>(100);
    }

    public NetFullfilledRequestManager(NetworkParameters params, byte[] payload, int cursor) {
        super(params, payload, cursor);
        context = Context.get();
    }

    public int calculateMessageSizeInBytes() {
        lock.lock();
        int messageSize = 0;
        try {
            int size = (int) readVarInt();
            messageSize += VarInt.sizeOf(size);
            mapFulfilledRequests = new HashMap<PeerAddress, HashMap<String, Long>>(size);
            for (int i = 0; i < size; ++i) {
                messageSize += PeerAddress.MESSAGE_SIZE;
                int size2 = (int) readVarInt();
                messageSize += VarInt.sizeOf(size);
                for (int j = 0; j < size2; ++j) {
                    cursor = messageSize;
                    String message = readStr();
                    messageSize += message.length() + 8;

                }
            }
            cursor = offset;
            return messageSize;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void parse() throws ProtocolException {

        int size = (int) readVarInt();
        mapFulfilledRequests = new HashMap<PeerAddress, HashMap<String, Long>>(size);
        for (int i = 0; i < size; ++i) {
            PeerAddress address = new PeerAddress(params, payload, offset, 0);
            int size2 = (int) readVarInt();
            HashMap<String, Long> addressData = new HashMap<String, Long>(size2);
            for (int j = 0; j < size2; ++j) {
                String message = readStr();
                long time = readInt64();
                addressData.put(message, time);
            }
            mapFulfilledRequests.put(address, addressData);
        }

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        lock.lock();
        try {
            stream.write(new VarInt(mapFulfilledRequests.size()).encode());
            for (Map.Entry<PeerAddress, HashMap<String, Long>> entry : mapFulfilledRequests.entrySet()) {
                entry.getKey().bitcoinSerializeToStream(stream);
                HashMap<String, Long> messages = entry.getValue();
                for (Map.Entry<String, Long> msg : messages.entrySet()) {
                    stream.write(msg.getKey().getBytes());
                    Utils.int64ToByteStreamLE(msg.getValue(), stream);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void addFulfilledRequest(PeerAddress addr, String strRequest) {
        lock.lock();
        try {
            if (mapFulfilledRequests.containsKey(addr)) {
                HashMap<String, Long> entry = mapFulfilledRequests.get(addr);
                entry.put(strRequest, Utils.currentTimeSeconds() + context.getParams().getFulfilledRequestExpireTime());
            } else {
                HashMap<String, Long> entry = new HashMap<String, Long>();
                entry.put(strRequest, Utils.currentTimeSeconds() + context.getParams().getFulfilledRequestExpireTime());
                mapFulfilledRequests.put(addr, entry);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean hasFulfilledRequest(PeerAddress addr, String strRequest) {
        lock.lock();
        try {
            HashMap<String, Long> entry = mapFulfilledRequests.get(addr);
            if (entry != null) {
                if (entry.containsKey(strRequest)) {
                    return entry.get(strRequest) > Utils.currentTimeSeconds();
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void removeFulfilledRequest(PeerAddress addr, String strRequest) {
        lock.lock();
        try {
            HashMap<String, Long> entry = mapFulfilledRequests.get(addr);
            if (entry != null) {
                mapFulfilledRequests.remove(addr);
            }
        } finally {
            lock.unlock();
        }
    }

    public void removeAllFulfilledRequests(PeerAddress addr) {
        lock.lock();
        try {
            HashMap<String, Long> entry = mapFulfilledRequests.get(addr);
            if (entry != null) {
                mapFulfilledRequests.remove(addr);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void checkAndRemove() {
        log.info("checkAndRemove: {}", this);
        lock.lock();
        try {
            long now = Utils.currentTimeSeconds();
            Iterator<Map.Entry<PeerAddress, HashMap<String, Long>>> it = mapFulfilledRequests.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<PeerAddress, HashMap<String, Long>> entry = it.next();
                Iterator<Map.Entry<String, Long>> it_entry = entry.getValue().entrySet().iterator();
                while (it_entry.hasNext()) {
                    Map.Entry<String, Long> msgTime = it_entry.next();
                    if (now > msgTime.getValue())
                        it_entry.remove();
                }
                if (entry.getValue().size() == 0)
                    it.remove();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        mapFulfilledRequests.clear();
    }

    public String toString() {
        return "Nodes with fulfilled requests: " + mapFulfilledRequests.size();
    }

    @Override
    public AbstractManager createEmpty() {
        return new NetFullfilledRequestManager(null);
    }

    @Override
    public void close() {

    }

    public void doMaintenance() {
        checkAndRemove();
    }
}
