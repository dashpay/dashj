/*
 * Copyright 2014 Giannis Dzegoutanis
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

package org.bitcoinj.params;

import org.bitcoinj.core.NetworkParameters;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.Set;

/**
 * Utility class that holds all the registered {@link NetworkParameters} types used for address auto discovery.
 * By default only {@link MainNetParams} and {@link TestNet3Params} are used. If you want to use {@link RegTestParams}
 * or {@link UnitTestParams} use {@code register} and then {@code unregister} the {@code TestNet3Params} as they don't
 * have their own Base58 version/type code.
 */
public class Networks {
    /** Registered networks */
    private static Set<? extends NetworkParameters> networks = ImmutableSet.of(TestNet3Params.get(), MainNetParams.get());

    public static Set<? extends NetworkParameters> get() {
        return networks;
    }

    /**
     * Register a single network type by adding it to the {@code Set}.
     *
     * @param network Network to register/add.
     */
    public static void register(NetworkParameters network) {
        register(Lists.newArrayList(network));
    }

    /**
     * Register a collection of additional network types by adding them
     * to the {@code Set}.
     *
     * @param networks Networks to register/add.
     */
    public static void register(Collection<? extends NetworkParameters> networks) {
        ImmutableSet.Builder<NetworkParameters> builder = ImmutableSet.builder();
        builder.addAll(Networks.networks);
        builder.addAll(networks);
        Networks.networks = builder.build();
    }

    /**
     * Unregister a network type.
     *
     * @param network Network type to unregister/remove.
     */
    public static void unregister(NetworkParameters network) {
        if (networks.contains(network)) {
            ImmutableSet.Builder<NetworkParameters> builder = ImmutableSet.builder();
            for (NetworkParameters parameters : networks) {
                if (parameters.equals(network))
                    continue;
                builder.add(parameters);
            }
            networks = builder.build();
        }
    }
}
