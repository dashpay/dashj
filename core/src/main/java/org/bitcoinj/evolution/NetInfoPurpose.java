/*
 * Copyright 2026 Dash Core Group.
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

package org.bitcoinj.evolution;

import javax.annotation.Nullable;

/**
 * The purpose codes that key the address lists in an {@link ExtNetInfo} (Dash Core {@code NetInfoPurpose}).
 */
public enum NetInfoPurpose {
    CORE_P2P(0),
    PLATFORM_P2P(1),
    PLATFORM_HTTPS(2);

    public final int value;

    NetInfoPurpose(int value) {
        this.value = value;
    }

    @Nullable
    public static NetInfoPurpose fromValue(int value) {
        for (NetInfoPurpose purpose : values()) {
            if (purpose.value == value) {
                return purpose;
            }
        }
        return null;
    }
}
