package org.bitcoinj.utils;

import org.bitcoinj.core.*;
import org.bitcoinj.governance.GovernanceVote;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Eric on 6/2/2018.
 */
public class CacheItem<K, V> extends ChildMessage {

    public CacheItem() {
    }

    public CacheItem(K keyIn, V valueIn) {
        //super(params);
        this.key = keyIn;
        this.value = valueIn;
    }
    public CacheItem(NetworkParameters params, byte [] payload, int cursor) {
        super(params, payload, cursor);
    }

    public K key;
    public V value;

    @Override
    protected void parse() throws ProtocolException {
        key = parseMessage(key, params, payload, cursor);

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        writeMessage(key, stream);
        writeMessage(value, stream);
    }

    public String toString() {
        return "";
    }

    public <T> T parseMessage(T value, NetworkParameters params, byte [] payload, int cursor) {
        if(value instanceof Sha256Hash)
            return (T)readHash();
        else if(value instanceof GovernanceVote) {
            GovernanceVote vote = new GovernanceVote(params, payload, cursor);
            cursor += vote.getMessageSize();
            return (T)vote;
        }
        else return null;
    }


    public <T> void writeMessage(T value, OutputStream stream) throws IOException {

        if(value instanceof Sha256Hash)
            stream.write(((Sha256Hash)value).getReversedBytes());
        else if(value instanceof Message) {
            ((GovernanceVote)value).bitcoinSerialize(stream);
        }

    }

}
