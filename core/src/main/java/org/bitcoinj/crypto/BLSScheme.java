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

import org.dashj.bls.BasicSchemeMPL;
import org.dashj.bls.CoreMPL;
import org.dashj.bls.LegacySchemeMPL;

/**
 * Manages the BLS Scheme used based on the legacy flag.
 */

public class BLSScheme {
    private static boolean legacyDefault = true;
    private static final LegacySchemeMPL legacySchemeMPL = new LegacySchemeMPL();
    private static final BasicSchemeMPL scheme = new BasicSchemeMPL();

    static CoreMPL get(boolean legacy) {
        return legacy ? legacySchemeMPL : scheme;
    }

    public static boolean isLegacyDefault() {
        return legacyDefault;
    }

    public static void setLegacyDefault(boolean legacyDefault) {
        BLSScheme.legacyDefault = legacyDefault;
    }
}
