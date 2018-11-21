/*
 * Copyright 2018 Dash Core Group
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
 *
 */

package org.dashj.bls;

public abstract class BLSObject {
    protected transient long cPointer;
    protected transient boolean owner;

    protected BLSObject(long cPointer, boolean owner) {
        this.cPointer = cPointer;
        this.owner = owner;
    }

    protected static long getCPtr(BLSObject obj) {
        return (obj == null) ? 0 : obj.cPointer;
    }
    protected long getCPtr() { return cPointer; }
    protected boolean isOwner() { return owner; }

    protected void finalize() {
        handleDelete();
    }

    public synchronized void handleDelete() {
        if (cPointer != 0) {
            if (owner) {
                owner = false;
                delete();
            }
            cPointer = 0;
        }
    }

    protected abstract void delete();
}
