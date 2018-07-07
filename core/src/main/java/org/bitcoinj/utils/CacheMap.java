package org.bitcoinj.utils;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Map like container that keeps the N most recently added items
 */
public class CacheMap<K, V> extends ChildMessage {

    private long nMaxSize;

    private long nCurrentSize;

    private LinkedList<CacheItem<K,V>> listItems = new LinkedList<CacheItem<K,V>>();

    private LinkedHashMap<K, CacheItem<K,V>> mapIndex = new LinkedHashMap<K, CacheItem<K,V>>();

    public CacheMap() {
            this(0);
            }

    public CacheMap(long nMaxSizeIn) {
        this.nMaxSize = nMaxSizeIn;
        this.nCurrentSize = 0;
        this.listItems = new LinkedList<CacheItem<K, V>>();
        this.mapIndex = new LinkedHashMap<K, CacheItem<K, V>>();
    }

    public CacheMap(CacheMap<K, V> other) {
        this.nMaxSize = other.nMaxSize;
        this.nCurrentSize = other.nCurrentSize;
        this.listItems = new LinkedList<CacheItem<K, V>>(other.listItems);
        this.mapIndex = new LinkedHashMap<K, CacheItem<K, V>>();
        rebuildIndex();
    }

    public CacheMap(NetworkParameters params, byte [] payload, int cursor) {
        super(params, payload, cursor);
    }

    public final void clear() {
        mapIndex.clear();
        listItems.clear();
        nCurrentSize = 0;
    }

    public final void setMaxSize(long nMaxSizeIn) {
        nMaxSize = nMaxSizeIn;
    }

    public final long getMaxSize() {
        return nMaxSize;
    }

    public final long getSize() {
        return nCurrentSize;
    }

    public final void insert(K key, V value) {
        CacheItem<K, V> it = mapIndex.get(key);

        if (it != null) {
            CacheItem<K, V> item = it;
            item.value = value;
            return;
        }
        if (nCurrentSize == nMaxSize) {
            pruneLast();
        }
        CacheItem item = new CacheItem(key, value);
        listItems.addFirst(item);
        mapIndex.put(key, item);
        ++nCurrentSize;
    }

    public final boolean hasKey(K key) {
        return mapIndex.containsKey(key);
    }

    public final CacheItem<K, V> get(K key) {
        CacheItem<K, V> it = mapIndex.get(key);
        if (it == null) {
            return null;
        }

        return it;
    }

    public final void erase(K key) {
        CacheItem<K, V> it = mapIndex.get(key);
        if (it == null) {
            return;
        }
        listItems.remove(it);
        mapIndex.remove(it.key);
        --nCurrentSize;
    }

    public final LinkedList<CacheItem<K, V>> getItemList() {
        return listItems;
    }

    public final CacheMap<K, V> copyFrom(CacheMap<K, V> other) {
        nMaxSize = other.nMaxSize;
        nCurrentSize = other.nCurrentSize;
        listItems = new LinkedList<CacheItem<K, V>>(other.listItems);
        rebuildIndex();
        return this;
    }


    private void pruneLast() {
        if (nCurrentSize < 1) {
            return;
        }
        CacheItem<K, V> item = listItems.getLast();
        mapIndex.remove(item.key);
        listItems.removeLast();
        --nCurrentSize;
    }

    private void rebuildIndex() {
        mapIndex.clear();
        for(CacheItem<K, V> item : listItems) {
            mapIndex.put(item.key, item);
        }
    }

    @Override
    protected void parse() throws ProtocolException {
        nMaxSize = readInt64();
        nCurrentSize = readInt64();
        long size = readVarInt();
        mapIndex = new LinkedHashMap<K, CacheItem<K, V>>();
        listItems = new LinkedList<CacheItem<K, V>>();
        for (int i = 0; i < size; ++i) {
            CacheItem<K, V> item = new CacheItem<K, V>(params, payload, cursor);
            cursor += item.getMessageSize();
            listItems.add(item);
        }

        length = cursor - offset;
        rebuildIndex();
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.int64ToByteStreamLE(nMaxSize, stream);
        Utils.int64ToByteStreamLE(nCurrentSize, stream);
        stream.write(new VarInt(listItems.size()).encode());
        for(CacheItem<K, V> item : listItems) {
            item.bitcoinSerialize(stream);
        }
    }

    public String toString() {
        return "CacheMap("+nCurrentSize+" of {"+nMaxSize+"}}";
    }
}