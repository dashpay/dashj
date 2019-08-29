package org.dashj.net.discovery;

import org.dashj.core.NetworkParameters;
import org.dashj.evolution.SimplifiedMasternodeListManager;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This peer discovery class uses three methods to get peers.
 *
 * 1.  DnsSeeds from NetworkParameters
 * 2.  Masternode list peers (using the current tip)
 * 3.  SeedPeers from NetworkParameters
 */
public class ThreeMethodPeerDiscovery implements PeerDiscovery {

    private NetworkParameters params;
    private SimplifiedMasternodeListManager masternodeListManager;
    private PeerDiscovery normalPeerDiscovery;
    private SeedPeers seedPeerDiscovery;

    /**
     * Instantiates a new Three method peer discovery.
     *
     * @param params                the network params
     * @param masternodeListManager the masternode list manager
     */
    public ThreeMethodPeerDiscovery(NetworkParameters params, SimplifiedMasternodeListManager masternodeListManager) {
        this.params = params;
        this.masternodeListManager = masternodeListManager;
        normalPeerDiscovery = MultiplexingDiscovery.forServices(params, 0);
        seedPeerDiscovery = new SeedPeers(params);
    }

    @Override
    public InetSocketAddress[] getPeers(final long services, final long timeoutValue,
                                        final TimeUnit timeoutUnit) throws PeerDiscoveryException {
        final List<InetSocketAddress> peers = new LinkedList<InetSocketAddress>();

        try {
            peers.addAll(
                    Arrays.asList(normalPeerDiscovery.getPeers(services, timeoutValue, timeoutUnit)));
        } catch (PeerDiscoveryException x) {
            //swallow and continue with another method of connection.
        }
        if(peers.size() < 10) {
            try {
                MasternodePeerDiscovery discovery = new MasternodePeerDiscovery(masternodeListManager.getListAtChainTip());
                peers.addAll(Arrays.asList(discovery.getPeers(services, timeoutValue, timeoutUnit)));
            } catch (PeerDiscoveryException x) {
                //swallow and continue with another method of connection
            }

            if(peers.size() < 10) {
                if (params.getAddrSeeds() != null) {
                    peers.addAll(Arrays.asList(seedPeerDiscovery.getPeers(services, timeoutValue, timeoutUnit)));
                }
            }
        }
        return peers.toArray(new InetSocketAddress[0]);
    }

    @Override
    public void shutdown() {
        normalPeerDiscovery.shutdown();
    }
}
