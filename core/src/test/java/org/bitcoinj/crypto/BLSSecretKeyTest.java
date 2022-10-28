/*
 * Copyright 2022 Dash Core Group.
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

package org.bitcoinj.crypto;

import org.dashj.bls.BLSJniLibrary;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class BLSSecretKeyTest {
    @BeforeClass
    public static void beforeClass() {
        BLSJniLibrary.init();
    }

    @Test
    public void setHexStringTest() {

        String strValidSecret = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        new BLSSecretKey(strValidSecret);

        assertThrows(IllegalArgumentException.class, () -> {
            new BLSSecretKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1g"); // non-hex
        });

        // Try few more invalid strings
        assertThrows(IllegalArgumentException.class, () -> {
            new BLSSecretKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e"); // hex but too short
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new BLSSecretKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"); // hex but too long
        });
    }


}
