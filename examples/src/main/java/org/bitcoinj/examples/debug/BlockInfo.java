/*
 * Copyright 2022 Dash Core Group
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

package org.bitcoinj.examples.debug;

import org.bitcoinj.core.StoredBlock;

import org.json.JSONObject;

public class BlockInfo {
    StoredBlock storedBlock;
    boolean chainLocked;
    JSONObject blockCore;

    BlockInfo(StoredBlock storedBlock) {
        this.storedBlock = storedBlock;
    }

    @Override
    public int hashCode() {
        return storedBlock.getHeader().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlockInfo that = (BlockInfo) o;

        return (!storedBlock.getHeader().getHash().equals(((BlockInfo) o).storedBlock.getHeader().getHash()));
    }

}
