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

import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VarInt;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A single address within an {@link ExtNetInfo} purpose list (Dash Core {@code NetInfoEntry}).
 *
 * <p>On the wire an entry is a one byte type followed by either an ADDRV2 (BIP155) service, a domain name
 * with a port, or nothing at all for an invalid entry. Unlike the rest of the protocol, ports are big-endian.</p>
 */
public final class NetInfoEntry {
    public static final int TYPE_SERVICE = 0x01;
    public static final int TYPE_DOMAIN = 0x02;
    public static final int TYPE_INVALID = 0xff;

    /** Maximum length of an ASCII FQDN (RFC 1035), enforced by Core at the serialization layer */
    public static final int DOMAIN_MAX_LEN = 253;

    /** BIP155 network ids that Core can store in a NetInfoEntry; Tor v2 (3) is not one of them */
    public enum Network {
        IPV4(1, 4),
        IPV6(2, 16),
        TORV3(4, 32),
        I2P(5, 32),
        CJDNS(6, 16);

        public final int id;
        public final int addressLength;

        Network(int id, int addressLength) {
            this.id = id;
            this.addressLength = addressLength;
        }

        @Nullable
        public static Network fromId(int id) {
            for (Network network : values()) {
                if (network.id == id) {
                    return network;
                }
            }
            return null;
        }
    }

    private static final NetInfoEntry INVALID = new NetInfoEntry(TYPE_INVALID, null, null, 0);

    private final int type;
    @Nullable private final Network network;
    // the address bytes for a service, or the UTF-8 host bytes for a domain
    @Nullable private final byte[] address;
    private final int port;

    private NetInfoEntry(int type, @Nullable Network network, @Nullable byte[] address, int port) {
        this.type = type;
        this.network = network;
        this.address = address;
        this.port = port;
    }

    public static NetInfoEntry service(Network network, byte[] address, int port) {
        checkArgument(address.length == network.addressLength, "address length %s does not match %s",
                address.length, network);
        checkArgument(port >= 0 && port <= 0xffff, "invalid port %s", port);
        return new NetInfoEntry(TYPE_SERVICE, network, address.clone(), port);
    }

    public static NetInfoEntry service(MasternodeAddress address) {
        byte[] bytes = address.getAddr().getAddress();
        return service(bytes.length == 4 ? Network.IPV4 : Network.IPV6, bytes, address.getPort());
    }

    public static NetInfoEntry domain(String host, int port) {
        return domain(host.getBytes(StandardCharsets.UTF_8), port);
    }

    static NetInfoEntry domain(byte[] host, int port) {
        checkArgument(host.length <= DOMAIN_MAX_LEN, "domain is longer than %s bytes", DOMAIN_MAX_LEN);
        checkArgument(port >= 0 && port <= 0xffff, "invalid port %s", port);
        return new NetInfoEntry(TYPE_DOMAIN, null, host.clone(), port);
    }

    public static NetInfoEntry invalid() {
        return INVALID;
    }

    public int getType() {
        return type;
    }

    public boolean isService() {
        return type == TYPE_SERVICE;
    }

    public boolean isDomain() {
        return type == TYPE_DOMAIN;
    }

    public boolean isInvalid() {
        return type == TYPE_INVALID;
    }

    @Nullable
    public Network getNetwork() {
        return network;
    }

    /** the raw address bytes of a service entry, otherwise null */
    @Nullable
    public byte[] getAddressBytes() {
        return isService() && address != null ? address.clone() : null;
    }

    /** the host of a domain entry, otherwise null */
    @Nullable
    public String getHost() {
        return isDomain() && address != null ? new String(address, StandardCharsets.UTF_8) : null;
    }

    /** the port, or 0 for an invalid entry */
    public int getPort() {
        return port;
    }

    /**
     * Returns this entry as a {@link MasternodeAddress} when it is an IPv4 or IPv6 service, otherwise null.
     */
    @Nullable
    public MasternodeAddress toMasternodeAddress() {
        if (!isService() || (network != Network.IPV4 && network != Network.IPV6)) {
            return null;
        }
        try {
            return new MasternodeAddress(InetAddress.getByAddress(address), port);
        } catch (UnknownHostException x) {
            // only thrown for an illegal length, which the factory methods rule out
            return null;
        }
    }

    void serialize(OutputStream stream) throws IOException {
        stream.write(type);
        switch (type) {
            case TYPE_SERVICE:
                stream.write(network.id);
                stream.write(new VarInt(address.length).encode());
                stream.write(address);
                writePortBE(stream);
                break;
            case TYPE_DOMAIN:
                stream.write(new VarInt(address.length).encode());
                stream.write(address);
                writePortBE(stream);
                break;
            default:
                break;
        }
    }

    int getSerializedSize() {
        switch (type) {
            case TYPE_SERVICE:
                return 2 + VarInt.sizeOf(address.length) + address.length + 2;
            case TYPE_DOMAIN:
                return 1 + VarInt.sizeOf(address.length) + address.length + 2;
            default:
                return 1;
        }
    }

    private void writePortBE(OutputStream stream) throws IOException {
        stream.write(0xff & (port >> 8));
        stream.write(0xff & port);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetInfoEntry that = (NetInfoEntry) o;
        return type == that.type && port == that.port && network == that.network &&
                Arrays.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * type + (network != null ? network.hashCode() : 0)) + Arrays.hashCode(address)) + port;
    }

    @Override
    public String toString() {
        switch (type) {
            case TYPE_SERVICE:
                MasternodeAddress ip = toMasternodeAddress();
                return ip != null ? ip.toString() : network + ":" + Utils.HEX.encode(address) + ":" + port;
            case TYPE_DOMAIN:
                return getHost() + ":" + port;
            default:
                return "[invalid]";
        }
    }
}
