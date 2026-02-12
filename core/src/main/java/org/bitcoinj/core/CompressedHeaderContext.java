/*
 * Copyright 2026 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.core;

import java.util.LinkedList;

/**
 * Maintains context for compressed header encoding/decoding per DIP-0025.
 *
 * <p>This tracks:
 * <ul>
 *   <li>The last 7 distinct version values in LRU order (most recent first)</li>
 *   <li>The previous header's hash (for prevBlockHash omission)</li>
 *   <li>The previous header's timestamp (for offset calculation)</li>
 *   <li>The previous header's nBits (for same-as-previous encoding)</li>
 * </ul>
 *
 * <p>The version table uses LRU (Least Recently Used) ordering:
 * <ul>
 *   <li>New versions are added to the front</li>
 *   <li>When a version is used (compressed), it moves to the front</li>
 *   <li>When full, the oldest version (at back) is removed</li>
 * </ul>
 *
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 *
 * @see <a href="https://github.com/dashpay/dips/blob/master/dip-0025.md">DIP-0025</a>
 */
public class CompressedHeaderContext {

    /** Maximum number of distinct versions to track */
    public static final int MAX_VERSION_TABLE_SIZE = 7;

    // LRU-ordered list: most recently used at front, oldest at back
    private final LinkedList<Long> versionTable;
    private Sha256Hash previousBlockHash;
    private long previousTimestamp;
    private long previousNBits;

    /**
     * Creates a new context with empty state.
     */
    public CompressedHeaderContext() {
        versionTable = new LinkedList<>();
        previousBlockHash = Sha256Hash.ZERO_HASH;
        previousTimestamp = 0;
        previousNBits = 0;
    }

    /**
     * Save a version as the most recent one (add to front of LRU list).
     * Called when version is read from stream (not compressed).
     * Matches C++ SaveVersionAsMostRecent().
     *
     * @param version the version to save
     */
    public void saveVersionAsMostRecent(long version) {
        // Add to front
        versionTable.addFirst(version);

        // Remove oldest if over capacity
        if (versionTable.size() > MAX_VERSION_TABLE_SIZE) {
            versionTable.removeLast();
        }
    }

    /**
     * Mark an existing version as most recently used (move to front of LRU list).
     * Called when version is retrieved from table (compressed).
     * Matches C++ MarkVersionAsMostRecent().
     *
     * @param index the current index of the version in the table
     */
    public void markVersionAsMostRecent(int index) {
        if (index > 0 && index < versionTable.size()) {
            // Remove from current position and add to front
            Long version = versionTable.remove(index);
            versionTable.addFirst(version);
        }
        // If index == 0, it's already at the front, nothing to do
    }

    /**
     * Get version at the specified index in the table.
     *
     * @param index the index (0-6) to look up, where 0 is most recent
     * @return the version value at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public long getVersionAt(int index) {
        if (index < 0 || index >= versionTable.size()) {
            throw new IndexOutOfBoundsException("Version index out of range: " + index +
                ", table size: " + versionTable.size());
        }
        return versionTable.get(index);
    }

    /**
     * Get the index of a version in the table.
     *
     * @param version the version value to look up
     * @return the index (0-6) if found, or -1 if not present in the table
     */
    public int getVersionIndex(long version) {
        for (int i = 0; i < versionTable.size(); i++) {
            if (versionTable.get(i) == version) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return the number of distinct versions currently in the table
     */
    public int getVersionTableSize() {
        return versionTable.size();
    }

    /**
     * Update the previous block info after processing a header.
     * Note: Version table is updated separately via saveVersionAsMostRecent/markVersionAsMostRecent.
     *
     * @param header the block header that was just processed
     */
    public void updateAfterHeader(Block header) {
        // Update previous header values for next compression/decompression
        previousBlockHash = header.getHash();
        previousTimestamp = header.getTimeSeconds();
        previousNBits = header.getDifficultyTarget();
    }

    /**
     * @return the hash of the previous header (for prevBlockHash omission)
     */
    public Sha256Hash getPreviousBlockHash() {
        return previousBlockHash;
    }

    /**
     * @return the timestamp of the previous header (for offset calculation)
     */
    public long getPreviousTimestamp() {
        return previousTimestamp;
    }

    /**
     * @return the nBits of the previous header (for same-as-previous encoding)
     */
    public long getPreviousNBits() {
        return previousNBits;
    }

    /**
     * Reset context to initial state.
     * This should be called at the start of a new header download session.
     */
    public void reset() {
        versionTable.clear();
        previousBlockHash = Sha256Hash.ZERO_HASH;
        previousTimestamp = 0;
        previousNBits = 0;
    }

    @Override
    public String toString() {
        return "CompressedHeaderContext{" +
                "versionTableSize=" + versionTable.size() +
                ", previousBlockHash=" + previousBlockHash +
                ", previousTimestamp=" + previousTimestamp +
                ", previousNBits=" + previousNBits +
                '}';
    }
}
