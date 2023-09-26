package org.bitcoinj.core;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class BlockQueue implements Collection<StoredBlock> {
    private final LinkedHashMap<Sha256Hash, StoredBlock> map;
    private final ArrayDeque<StoredBlock> queue;

    public BlockQueue() {
        queue = new ArrayDeque<>(16);
        map = new LinkedHashMap<>();
    }

    public void clear() {
        queue.clear();
        map.clear();
    }

    @Override
    public Spliterator<StoredBlock> spliterator() {
        return queue.spliterator();
    }

    @Override
    public Stream<StoredBlock> stream() {
        return queue.stream();
    }

    @Override
    public Stream<StoredBlock> parallelStream() {
        return queue.parallelStream();
    }

    public StoredBlock pop() {
        StoredBlock thisBlock = queue.removeFirst();
        map.remove(thisBlock.getHeader().getHash());
        return thisBlock;
    }

    public StoredBlock peek() {
        return queue.getFirst();
    }
    public boolean contains(StoredBlock block) {
        return map.containsKey(block.getHeader().getHash());
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof StoredBlock)
            return contains((StoredBlock) o);
        return false;
    }

    public int size() {
        return queue.size();
    }

    static class BlockQueueIterator implements java.util.Iterator<StoredBlock> {

        Iterator<StoredBlock> iterator;
        BlockQueue blockQueue;
        StoredBlock currentBlock;
        public BlockQueueIterator(BlockQueue blockQueue) {
            iterator = blockQueue.queue.iterator();
            this.blockQueue = blockQueue;
        }
        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public StoredBlock next() {
            currentBlock = iterator.next();
            return currentBlock;
        }

        @Override
        public void remove() {
            if (currentBlock != null) {
                iterator.remove();
                blockQueue.map.remove(currentBlock.getHeader().getHash());
                currentBlock = null;
            }
        }

        @Override
        public void forEachRemaining(Consumer<? super StoredBlock> consumer) {
            iterator.forEachRemaining(consumer);
        }
    }

    @Override @Nonnull
    public Iterator<StoredBlock> iterator() {
        return new BlockQueueIterator(this);
    }

    @Override
    public void forEach(Consumer<? super StoredBlock> consumer) {
        queue.forEach(consumer);
    }

    @Override @Nonnull
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override @Nonnull
    public <T> T[] toArray(T[] ts) {
        return queue.toArray(ts);
    }

    @Override
    public boolean add(StoredBlock block) {
        boolean result = queue.add(block);
        result = result && map.put(block.getHeader().getHash(), block) == null;
        return result;
    }

    // the following methods are not currently implemented
    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> collection) {
        return false;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends StoredBlock> collection) {
        queue.addAll(collection);
        collection.forEach(block -> map.put(block.getHeader().getHash(), block));
        return false;
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> collection) {
        return false;
    }

    @Override
    public boolean removeIf(Predicate<? super StoredBlock> predicate) {
        return Collection.super.removeIf(predicate);
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> collection) {
        return false;
    }
}
