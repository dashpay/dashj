package org.bitcoinj.utils;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Map like container that keeps the N most recently added items
 */

public class CacheMultiMap<K, V> extends Message {

    protected long nMaxSize;

    private long nCurrentSize;

    private LinkedList<CacheItem<K, V>> listItems;

    private HashMap<K, HashMap<V, CacheItem<K, V>>> mapIndex;

    public CacheMultiMap() {
        this(0);
    }

    public CacheMultiMap(long nMaxSizeIn) {
        super(Context.get().getParams());
        this.nMaxSize = nMaxSizeIn;
        this.nCurrentSize = 0;
        this.listItems = new LinkedList<CacheItem<K, V>>();
        this.mapIndex = new HashMap<K, HashMap<V, CacheItem<K, V>>>();
    }

    public CacheMultiMap(CacheMap<K, V> other) {
        super(other.getParams());
        this.nMaxSize = other.getMaxSize();
        this.nCurrentSize = other.getSize();
        this.listItems = new LinkedList<CacheItem<K, V>>(other.getItemList());
        this.mapIndex = new HashMap<K, HashMap<V, CacheItem<K, V>>>();
        rebuildIndex();
    }
    public CacheMultiMap(NetworkParameters params, byte [] payload, int cursor) {
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

    public final boolean insert(K key, V value) {
        if (nCurrentSize == nMaxSize) {
            pruneLast();
        }
        HashMap<V, CacheItem<K, V>> map = mapIndex.get(key);
        if (map == null) {
            map = new HashMap<V, CacheItem<K, V>>();
            mapIndex.put(key, map);
        }

        if (map.containsValue(value)) {
            // Don't insert duplicates
            return false;
        }

        listItems.addFirst(new CacheItem<K,V>(key, value));
        CacheItem<K, V> lit = listItems.getFirst();

        map.put(value, lit);
        ++nCurrentSize;
        return true;
    }

    public final boolean hasKey(K key) {
        return mapIndex.containsKey(key);
    }

    public final V get(K key) {
        HashMap<V, CacheItem<K, V>> it = mapIndex.get(key);
        if (it == null) {
            return null;
        }
        final HashMap<V, CacheItem<K, V>> mapIt = it;
        final CacheItem<K, V> item = mapIt.get(key);
        return item.value;
    }

    public final boolean getAll(K key, ArrayList<V> vecValues) {
        assert(vecValues != null);
        HashMap<V, CacheItem<K, V>> mit = mapIndex.get(key);
        if (mit == null) {
            return false;
        }

        for (Map.Entry<V, CacheItem<K, V>> it :  mit.entrySet()) {
            final CacheItem<K, V> item = it.getValue();
            vecValues.add(item.value);
        }
        return true;
    }

    public final void getKeys(ArrayList<K> vecKeys) {
        assert(vecKeys != null);
        for (Map.Entry<K, HashMap < V, CacheItem<K, V>>> it:  mapIndex.entrySet()) {
            vecKeys.add(it.getKey());
        }
    }

    public final void erase(K key) {
        HashMap<V, CacheItem<K, V>> mit = mapIndex.get(key);
        if (mit == null) {
            return;
        }

        for (Map.Entry<V, CacheItem<K, V>> it : mit.entrySet()) {

            listItems.remove(it.getValue());
            --nCurrentSize;
        }
        mapIndex.remove(mit);
    }

    public final void erase(K key, V value) {
        HashMap<V, CacheItem<K, V>> mit = mapIndex.get(key);
        if (mit == null) {
            return;
        }

        CacheItem<K, V> it = mit.get(value);
        if (it == null) {
            return;
        }

        listItems.remove(it);
        --nCurrentSize;
        mit.remove(key);

        if (mit.size() < 1) {
            mapIndex.remove(mit);
        }
    }

    public final LinkedList<CacheItem<K,V>> getItemList() {
        return listItems;
    }

    private void pruneLast() {
        if (nCurrentSize < 1) {
            return;
        }

        Iterator<CacheItem<K,V>> lit = listItems.descendingIterator();
        CacheItem<K,V> item = lit.next();

        HashMap<V,CacheItem<K,V>> mit = mapIndex.get(item.key);

        if (mit != null){
            mit.remove(item.value);

            if (mit.size() < 1) {
                mapIndex.remove(item.key);
            }
        }

        listItems.removeLast();
        --nCurrentSize;
    }

    private void rebuildIndex() {
        mapIndex.clear();
        for(CacheItem<K, V> lit : listItems)
        {
            HashMap<V, CacheItem<K,V>> mit = mapIndex.get(lit.key);
            if (mit == null) {
                mit = new HashMap<V, CacheItem<K, V>>();
                mapIndex.put(lit.key, mit);
            }
            mit.put(lit.value, lit);
        }
    }

    @Override
    protected void parse() throws ProtocolException {
        nMaxSize = readInt64();
        nCurrentSize = readInt64();
        long size = readVarInt();
        mapIndex = new LinkedHashMap<K, HashMap<V, CacheItem<K, V>>>();
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

