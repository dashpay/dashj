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
 */
package org.bitcoinj.core;

import org.bitcoinj.script.Script;

public class NoDestination extends TransactionDestination {
    public static final NoDestination INSTANCE = new NoDestination();

    public static NoDestination get() { return INSTANCE; }

    public NoDestination() {
        super(new byte[0]);
    }

    public String toString()
    {
        return "NoDestination()";
    }

    @Override
    public Address getAddress(NetworkParameters params) {
        return null;
    }

    @Override
    public Script getScript() {
        return null;
    }
}
