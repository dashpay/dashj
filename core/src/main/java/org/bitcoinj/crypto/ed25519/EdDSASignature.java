/*
 * Copyright 2023 Dash Core Group
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

package org.bitcoinj.crypto.ed25519;

import java.util.Arrays;
import java.util.Objects;

public class EdDSASignature {
    private final byte [] signature;

    public EdDSASignature(byte [] signature) {
        this.signature = signature;
    }

    public static EdDSASignature fromBytes(byte[] signature) {
        return new EdDSASignature(signature);
    }

    public byte[] getSignature() {
        return signature;
    }

    public static EdDSASignature dummy() {
        return new EdDSASignature(new byte[0]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdDSASignature other = (EdDSASignature) o;
        return Arrays.equals(signature, other.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(signature);
    }
}
