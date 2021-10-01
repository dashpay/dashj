package org.bitcoinj.net.discovery;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 3.  SeedPeers and MasternodeSeedPeers from NetworkParameters
 */
public class ThreeMethodPeerDiscovery implements PeerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(ThreeMethodPeerDiscovery.class);

    private NetworkParameters params;
    private SimplifiedMasternodeListManager masternodeListManager;
    private PeerDiscovery normalPeerDiscovery;
    private MasternodeSeedPeers masternodeSeedDiscovery;
    private SeedPeers seedPeerDiscovery;

    /**
     * Instantiates a new Three method peer discovery.
     *
     * @param params                the network params
     */
    public ThreeMethodPeerDiscovery(NetworkParameters params) {
        this(params, 0, null);
    }

    /**
     * Instantiates a new Three method peer discovery.
     *
     * @param params                the network params
     * @param masternodeListManager the masternode list manager
     */
    public ThreeMethodPeerDiscovery(NetworkParameters params, SimplifiedMasternodeListManager masternodeListManager) {
        this(params, 0, masternodeListManager);
    }

    /**
     * Instantiates a new Three method peer discovery.
     *
     * @param params                the network params
     * @param requiredServices      required services
     * @param masternodeListManager the masternode list manager
     */
    public ThreeMethodPeerDiscovery(NetworkParameters params, long requiredServices, SimplifiedMasternodeListManager masternodeListManager) {
        this.params = params;
        this.masternodeListManager = masternodeListManager;
        normalPeerDiscovery = MultiplexingDiscovery.forServices(params, requiredServices);
        masternodeSeedDiscovery = new MasternodeSeedPeers(params);
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
            log.info("DNS seeders have failed to return enough peers.  Now attempting to access the DML");
            try {
                if (masternodeListManager != null && masternodeListManager.getListAtChainTip().size() > 0) {
                    MasternodePeerDiscovery discovery = new MasternodePeerDiscovery(masternodeListManager.getListAtChainTip());
                    peers.addAll(Arrays.asList(discovery.getPeers(services, timeoutValue, timeoutUnit)));
                }
            } catch (PeerDiscoveryException x) {
                //swallow and continue with another method of connection
            }

            if (peers.size() < 10) {
                log.info("The DML does not have enough nodes.  Now attempting to access the default ML");
                if (params.getDefaultMasternodeList().length > 0) {
                    peers.addAll(Arrays.asList(masternodeSeedDiscovery.getPeers(services, timeoutValue, timeoutUnit)));
                }
            }

            if(peers.size() < 10) {
                log.info("The ML seed list does not have enough nodes.  Now attempting to access the seed list");
                if (params.getAddrSeeds() != null) {
                    peers.addAll(Arrays.asList(seedPeerDiscovery.getPeers(services, timeoutValue, timeoutUnit)));
                }
            }
        }
        log.info("peer discovery found " + peers.size() + " items");
        return peers.toArray(new InetSocketAddress[0]);
    }

    @Override
    public void shutdown() {
        normalPeerDiscovery.shutdown();
    }
}
