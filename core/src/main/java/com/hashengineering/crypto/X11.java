/*
 * Copyright 2014 Dash Core Group
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
package com.hashengineering.crypto;

import com.google.common.base.Preconditions;

import fr.cryptohash.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author HashEngineering
 *
 * Performs the X11 hashing algorithm
 *
 */
public class X11 {

    private static final Logger log = LoggerFactory.getLogger(X11.class);
    private static boolean native_library_loaded = false;
    private static Digest [] algorithms;

    static {
        try {
            log.info("Loading x11 native library...");
            System.loadLibrary("x11");
            native_library_loaded = true;
            log.info("Loaded x11 successfully.");
        }
        catch(UnsatisfiedLinkError x)
        {
            log.info("Loading x11 failed: " + x.getMessage());
            init();
        }
        catch(Exception e)
        {
            native_library_loaded = false;
            log.info("Loading x11 failed: " + e.getMessage());
            init();
        }
    }

    /**
     * create the hash objects only if the native library failed to load
     */
    static void init() {
        algorithms = new Digest[]{
                new BLAKE512(),
                new BMW512(),
                new Groestl512(),
                new Skein512(),
                new JH512(),
                new Keccak512(),
                new Luffa512(),
                new CubeHash512(),
                new SHAvite512(),
                new SIMD512(),
                new ECHO512()
        };
    }

    public static byte[] x11Digest(byte[] input, int offset, int length)
    {
        try {
            return native_library_loaded ? x11_native(input, offset, length) : x11(input, offset, length);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] x11Digest(byte[] input) {
        return x11Digest(input, 0, input.length);
    }

    static native byte [] x11_native(byte [] input, int offset, int length);

    public static byte [] x11(byte input[], int offset, int length) {
        Digest algorithm = algorithms[0];
        algorithm.reset();
        algorithm.update(input, offset, length);
        byte [] hash512 = algorithm.digest();
        int count = 1;
        while (count < 11) {
            algorithm = algorithms[count];
            algorithm.reset();
            algorithm.update(hash512);
            hash512 = algorithm.digest();
            count++;
        }
        byte [] hash = new byte [32];
        System.arraycopy(hash512, 0, hash, 0, 32);
        return hash;
    }
}
