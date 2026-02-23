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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Tests and benchmarks for {@link SCryptWrapper}.
 *
 * <p>The correctness tests verify that both implementations produce identical output for
 * the same input parameters. The speed comparison runs multiple iterations of each
 * implementation and reports average and total wall-clock time.</p>
 */
public class SCryptWrapperTest {

    private static final Logger log = LoggerFactory.getLogger(SCryptWrapperTest.class);

    // Low-cost parameters for fast unit tests (not suitable for production).
    private static final int N_FAST = 256;
    private static final int R = 8;
    private static final int P = 1;
    private static final int DK_LEN = 32;

    // Standard wallet parameters used in KeyCrypterScrypt (used in the speed comparison).
    private static final int N_STANDARD = 16384;

    private static final byte[] PASSWORD = "correct horse battery staple".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT     = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};

    private SCryptWrapper.Implementation originalImpl;

    @Before
    public void saveImplementation() {
        originalImpl = SCryptWrapper.getImplementation();
    }

    @After
    public void restoreImplementation() {
        SCryptWrapper.setImplementation(originalImpl);
    }

    // -------------------------------------------------------------------------
    // Correctness tests
    // -------------------------------------------------------------------------

    @Test
    public void bothImplementationsProduceSameOutput() throws GeneralSecurityException {
        byte[] lambdaworks = SCryptWrapper.scrypt(PASSWORD, SALT, N_FAST, R, P, DK_LEN,
                SCryptWrapper.Implementation.LAMBDAWORKS);
        byte[] bouncyCastle = SCryptWrapper.scrypt(PASSWORD, SALT, N_FAST, R, P, DK_LEN,
                SCryptWrapper.Implementation.BOUNCY_CASTLE);

        assertArrayEquals("Both implementations must produce identical derived keys", lambdaworks, bouncyCastle);
    }

    @Test
    public void globalImplementationSwitchWorks() throws GeneralSecurityException {
        SCryptWrapper.setImplementation(SCryptWrapper.Implementation.LAMBDAWORKS);
        byte[] lambdaworks = SCryptWrapper.scrypt(PASSWORD, SALT, N_FAST, R, P, DK_LEN);

        SCryptWrapper.setImplementation(SCryptWrapper.Implementation.BOUNCY_CASTLE);
        byte[] bouncyCastle = SCryptWrapper.scrypt(PASSWORD, SALT, N_FAST, R, P, DK_LEN);

        assertArrayEquals(lambdaworks, bouncyCastle);
        assertEquals(SCryptWrapper.Implementation.BOUNCY_CASTLE, SCryptWrapper.getImplementation());
    }

    @Test
    public void outputLengthMatchesRequestedDkLen() throws GeneralSecurityException {
        for (SCryptWrapper.Implementation impl : SCryptWrapper.Implementation.values()) {
            byte[] dk = SCryptWrapper.scrypt(PASSWORD, SALT, N_FAST, R, P, DK_LEN, impl);
            assertEquals("DK length mismatch for " + impl, DK_LEN, dk.length);
        }
    }

    @Test
    public void differentPasswordsProduceDifferentKeys() throws GeneralSecurityException {
        byte[] pass2 = "hunter2".getBytes(StandardCharsets.UTF_8);

        for (SCryptWrapper.Implementation impl : SCryptWrapper.Implementation.values()) {
            byte[] dk1 = SCryptWrapper.scrypt(PASSWORD, SALT, N_FAST, R, P, DK_LEN, impl);
            byte[] dk2 = SCryptWrapper.scrypt(pass2,    SALT, N_FAST, R, P, DK_LEN, impl);
            if (Arrays.equals(dk1, dk2)) {
                throw new AssertionError(impl + ": different passwords produced the same derived key");
            }
        }
    }

    @Test
    public void differentSaltsProduceDifferentKeys() throws GeneralSecurityException {
        byte[] salt2 = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD, (byte) 0xFC,
                        (byte) 0xFB, (byte) 0xFA, (byte) 0xF9, (byte) 0xF8};

        for (SCryptWrapper.Implementation impl : SCryptWrapper.Implementation.values()) {
            byte[] dk1 = SCryptWrapper.scrypt(PASSWORD, SALT,  N_FAST, R, P, DK_LEN, impl);
            byte[] dk2 = SCryptWrapper.scrypt(PASSWORD, salt2, N_FAST, R, P, DK_LEN, impl);
            if (Arrays.equals(dk1, dk2)) {
                throw new AssertionError(impl + ": different salts produced the same derived key");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Speed comparison
    // -------------------------------------------------------------------------

    /**
     * Compares the wall-clock time of both implementations at low-cost parameters.
     *
     * <p>This test always passes; timing results are printed via the logger so they
     * appear in the test output when running with a logging back-end configured.</p>
     */
    @Test
    public void speedComparisonFast() throws GeneralSecurityException {
        int iterations = 20;
        log.info("--- SCrypt speed comparison (N={}, r={}, p={}, {} iterations) ---", N_FAST, R, P, iterations);
        long lambdaMs   = benchmark(SCryptWrapper.Implementation.LAMBDAWORKS,  iterations, N_FAST);
        long bouncyCaMs = benchmark(SCryptWrapper.Implementation.BOUNCY_CASTLE, iterations, N_FAST);
        printSummary(SCryptWrapper.Implementation.LAMBDAWORKS,  lambdaMs,   iterations);
        printSummary(SCryptWrapper.Implementation.BOUNCY_CASTLE, bouncyCaMs, iterations);
        logWinner(lambdaMs, bouncyCaMs, iterations);
    }

    /**
     * Compares the wall-clock time of both implementations at standard wallet parameters.
     *
     * <p>This is intentionally slow (N=16384) to give a realistic comparison.
     * It is annotated so CI environments can skip it if needed.</p>
     */
    @Test
    public void speedComparisonStandard() throws GeneralSecurityException {
        int iterations = 5;
        log.info("--- SCrypt speed comparison (N={}, r={}, p={}, {} iterations) ---", N_STANDARD, R, P, iterations);
        long lambdaMs   = benchmark(SCryptWrapper.Implementation.LAMBDAWORKS,  iterations, N_STANDARD);
        long bouncyCaMs = benchmark(SCryptWrapper.Implementation.BOUNCY_CASTLE, iterations, N_STANDARD);
        printSummary(SCryptWrapper.Implementation.LAMBDAWORKS,  lambdaMs,   iterations);
        printSummary(SCryptWrapper.Implementation.BOUNCY_CASTLE, bouncyCaMs, iterations);
        logWinner(lambdaMs, bouncyCaMs, iterations);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Runs {@code iterations} SCrypt derivations and returns total elapsed milliseconds. */
    private long benchmark(SCryptWrapper.Implementation impl, int iterations, int n)
            throws GeneralSecurityException {
        // Warm up JIT with a single call before measuring.
        SCryptWrapper.scrypt(PASSWORD, SALT, n, R, P, DK_LEN, impl);

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            SCryptWrapper.scrypt(PASSWORD, SALT, n, R, P, DK_LEN, impl);
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private void printSummary(SCryptWrapper.Implementation impl, long totalMs, int iterations) {
        log.info("{}: total={}ms, avg={}ms per call",
                impl, totalMs, totalMs / iterations);
    }

    private void logWinner(long lambdaMs, long bouncyCaMs, int iterations) {
        if (lambdaMs < bouncyCaMs) {
            log.info("Winner: LAMBDAWORKS is faster by {}ms total ({} ms/call)",
                    bouncyCaMs - lambdaMs, (bouncyCaMs - lambdaMs) / iterations);
        } else if (bouncyCaMs < lambdaMs) {
            log.info("Winner: BOUNCY_CASTLE is faster by {}ms total ({} ms/call)",
                    lambdaMs - bouncyCaMs, (lambdaMs - bouncyCaMs) / iterations);
        } else {
            log.info("Both implementations took the same time.");
        }
    }
}