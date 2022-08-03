/*
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

package org.bitcoinj.examples;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.net.discovery.SeedPeers;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Determine which of the seed nodes have open ports on mainnet
 */
public class PortOpen {
    private static boolean available(String address, int port) {
        try (Socket ignored = new Socket(address, port)) {
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }

    // https://www.geekality.net/2013/04/30/java-simple-check-to-see-if-a-server-is-listening-on-a-port/
    public static boolean serverListening(String host, int port) {
        Socket s = null;
        try {
            s = new Socket(host, port);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (Exception ignored) {
                    // ignore this exception
                }
        }
    }

    public static void main(String[] args) {
        NetworkParameters params = MainNetParams.get();

        if (args[0].equals("testnet"))
            params = TestNet3Params.get();

        SeedPeers seedPeers = new SeedPeers(params);
        try {
            int index = 0;
            int notAvailable = 0;
            for (InetSocketAddress address : seedPeers.getPeers(0, 10, TimeUnit.SECONDS)) {
                boolean available = serverListening(address.getAddress().getHostAddress(), params.getPort());
                System.out.println(address.getAddress() + " is " + (available ? "available" : "not available"));
                index++;
                notAvailable += available ? 0 : 1;
            }
            System.out.println((notAvailable * 100 / index) + "% are not available");
        } catch (PeerDiscoveryException x) {
            System.out.println("Error: " + x.getMessage());
        }
    }
}
