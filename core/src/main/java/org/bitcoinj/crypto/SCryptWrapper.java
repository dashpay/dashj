/*
 * Copyright 2024 Dash Core Group
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.crypto;

import java.security.GeneralSecurityException;

/**
 * A wrapper around two SCrypt implementations that allows switching between them at runtime:
 * <ul>
 *   <li>{@link Implementation#LAMBDAWORKS} - {@code com.lambdaworks.crypto.SCrypt}</li>
 *   <li>{@link Implementation#BOUNCY_CASTLE} - {@code org.bouncycastle.crypto.generators.SCrypt}</li>
 * </ul>
 *
 * <p>The active implementation can be set globally via {@link #setImplementation(Implementation)},
 * or overridden per-call by passing an {@link Implementation} argument to {@link #scrypt}.</p>
 */
public class SCryptWrapper {

    public enum Implementation {
        /** com.lambdaworks.crypto.SCrypt (default) */
        LAMBDAWORKS,
        /** org.bouncycastle.crypto.generators.SCrypt */
        BOUNCY_CASTLE
    }

    private static volatile Implementation activeImplementation = Implementation.LAMBDAWORKS;

    /** Sets the global SCrypt implementation used by {@link #scrypt(byte[], byte[], int, int, int, int)}. */
    public static void setImplementation(Implementation impl) {
        activeImplementation = impl;
    }

    /** Returns the currently active global SCrypt implementation. */
    public static Implementation getImplementation() {
        return activeImplementation;
    }

    /**
     * Derives a key using the globally active SCrypt implementation.
     *
     * @param passwd password bytes
     * @param salt   salt bytes
     * @param N      CPU/memory cost parameter
     * @param r      block size parameter
     * @param p      parallelization parameter
     * @param dkLen  intended length of the derived key, in bytes
     * @return derived key bytes
     * @throws GeneralSecurityException if the lambdaworks implementation reports an error
     */
    public static byte[] scrypt(byte[] passwd, byte[] salt, int N, int r, int p, int dkLen)
            throws GeneralSecurityException {
        return scrypt(passwd, salt, N, r, p, dkLen, activeImplementation);
    }

    /**
     * Derives a key using the specified SCrypt implementation.
     *
     * @param passwd password bytes
     * @param salt   salt bytes
     * @param N      CPU/memory cost parameter
     * @param r      block size parameter
     * @param p      parallelization parameter
     * @param dkLen  intended length of the derived key, in bytes
     * @param impl   the SCrypt implementation to use
     * @return derived key bytes
     * @throws GeneralSecurityException if the lambdaworks implementation reports an error
     */
    public static byte[] scrypt(byte[] passwd, byte[] salt, int N, int r, int p, int dkLen, Implementation impl)
            throws GeneralSecurityException {
        switch (impl) {
            case LAMBDAWORKS:
                return com.lambdaworks.crypto.SCrypt.scrypt(passwd, salt, N, r, p, dkLen);
            case BOUNCY_CASTLE:
                return org.bouncycastle.crypto.generators.SCrypt.generate(passwd, salt, N, r, p, dkLen);
            default:
                throw new IllegalStateException("Unknown SCrypt implementation: " + impl);
        }
    }
}