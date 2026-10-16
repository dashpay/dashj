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
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.VarInt;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The variable length, multi-address network info that replaces the fixed 18 byte service address in
 * masternode list entries and ProTx payloads of version 3 ({@code ProTxVersion::ExtAddr}) and higher.
 *
 * <pre>
 * uint8   version                  # an unknown version (0 or &gt; 1) carries no further bytes
 * varint  purpose_count
 * repeat purpose_count:
 *   uint8   purpose                # {@link NetInfoPurpose}
 *   varint  entry_count
 *   repeat entry_count: {@link NetInfoEntry}
 * </pre>
 *
 * Purposes are kept in wire order so that a parsed object re-serializes byte for byte.
 */
public class ExtNetInfo extends Message {
    public static final int CURRENT_VERSION = 1;

    private int version;
    // not initialized here, because parse() runs from the superclass constructor before field initializers
    private LinkedHashMap<NetInfoPurpose, List<NetInfoEntry>> entries;

    public ExtNetInfo(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    /**
     * @param entries the address lists by purpose, serialized in iteration order (Core uses purpose order)
     */
    public ExtNetInfo(NetworkParameters params, int version, Map<NetInfoPurpose, List<NetInfoEntry>> entries) {
        super(params);
        this.version = version;
        this.entries = new LinkedHashMap<>();
        if (isKnownVersion()) {
            for (Map.Entry<NetInfoPurpose, List<NetInfoEntry>> entry : entries.entrySet()) {
                this.entries.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
        }
        length = calculateSize();
    }

    public static ExtNetInfo fromCoreP2P(NetworkParameters params, MasternodeAddress service) {
        Map<NetInfoPurpose, List<NetInfoEntry>> entries = new LinkedHashMap<>();
        entries.put(NetInfoPurpose.CORE_P2P, Collections.singletonList(NetInfoEntry.service(service)));
        return new ExtNetInfo(params, CURRENT_VERSION, entries);
    }

    private boolean isKnownVersion() {
        return version != 0 && version <= CURRENT_VERSION;
    }

    @Override
    protected void parse() throws ProtocolException {
        entries = new LinkedHashMap<>();
        version = readBytes(1)[0] & 0xff;
        if (!isKnownVersion()) {
            // Core stops here for unknown versions, so there is nothing more to read
            length = cursor - offset;
            return;
        }
        long purposeCount = readVarInt();
        // every purpose takes at least 2 bytes; check before looping on an attacker supplied count
        checkCount(purposeCount, 2);
        for (long i = 0; i < purposeCount; i++) {
            int purposeValue = readBytes(1)[0] & 0xff;
            NetInfoPurpose purpose = NetInfoPurpose.fromValue(purposeValue);
            if (purpose == null) {
                throw new ProtocolException("ExtNetInfo has an unknown purpose: " + purposeValue);
            }
            if (entries.containsKey(purpose)) {
                throw new ProtocolException("ExtNetInfo has a duplicate purpose: " + purpose);
            }
            long entryCount = readVarInt();
            // every entry takes at least 1 byte
            checkCount(entryCount, 1);
            List<NetInfoEntry> list = new ArrayList<>();
            for (long j = 0; j < entryCount; j++) {
                list.add(readEntry());
            }
            entries.put(purpose, Collections.unmodifiableList(list));
        }
        length = cursor - offset;
    }

    private void checkCount(long count, int minBytesEach) throws ProtocolException {
        if (count < 0 || count > (payload.length - cursor) / minBytesEach) {
            throw new ProtocolException("ExtNetInfo count is larger than the remaining payload: " + count);
        }
    }

    private NetInfoEntry readEntry() throws ProtocolException {
        int type = readBytes(1)[0] & 0xff;
        switch (type) {
            case NetInfoEntry.TYPE_SERVICE: {
                int networkId = readBytes(1)[0] & 0xff;
                NetInfoEntry.Network network = NetInfoEntry.Network.fromId(networkId);
                if (network == null) {
                    throw new ProtocolException("ExtNetInfo has an unsupported BIP155 network id: " + networkId);
                }
                long addressLength = readVarInt();
                if (addressLength != network.addressLength) {
                    throw new ProtocolException("ExtNetInfo " + network + " address has length " + addressLength +
                            ", expected " + network.addressLength);
                }
                byte[] address = readBytes(network.addressLength);
                return NetInfoEntry.service(network, address, readPortBE());
            }
            case NetInfoEntry.TYPE_DOMAIN: {
                long hostLength = readVarInt();
                if (hostLength < 0 || hostLength > NetInfoEntry.DOMAIN_MAX_LEN) {
                    throw new ProtocolException("ExtNetInfo domain is too long: " + hostLength);
                }
                byte[] host = readBytes((int) hostLength);
                return NetInfoEntry.domain(host, readPortBE());
            }
            case NetInfoEntry.TYPE_INVALID:
                return NetInfoEntry.invalid();
            default:
                throw new ProtocolException("ExtNetInfo has an unknown entry type: " + type);
        }
    }

    private int readPortBE() throws ProtocolException {
        byte[] port = readBytes(2);
        return ((port[0] & 0xff) << 8) | (port[1] & 0xff);
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(version);
        if (!isKnownVersion()) {
            return;
        }
        stream.write(new VarInt(entries.size()).encode());
        for (Map.Entry<NetInfoPurpose, List<NetInfoEntry>> entry : entries.entrySet()) {
            stream.write(entry.getKey().value);
            stream.write(new VarInt(entry.getValue().size()).encode());
            for (NetInfoEntry netInfoEntry : entry.getValue()) {
                netInfoEntry.serialize(stream);
            }
        }
    }

    private int calculateSize() {
        if (!isKnownVersion()) {
            return 1;
        }
        int size = 1 + VarInt.sizeOf(entries.size());
        for (Map.Entry<NetInfoPurpose, List<NetInfoEntry>> entry : entries.entrySet()) {
            size += 1 + VarInt.sizeOf(entry.getValue().size());
            for (NetInfoEntry netInfoEntry : entry.getValue()) {
                size += netInfoEntry.getSerializedSize();
            }
        }
        return size;
    }

    public int getVersion() {
        return version;
    }

    /** the address lists by purpose, in wire order */
    public Map<NetInfoPurpose, List<NetInfoEntry>> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public List<NetInfoEntry> getEntries(NetInfoPurpose purpose) {
        List<NetInfoEntry> list = entries.get(purpose);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Returns the primary Core P2P address, which is the first {@link NetInfoPurpose#CORE_P2P} entry,
     * the same entry that Core's {@code GetPrimary()} returns. Null if there is none or if it is
     * not an IPv4 or IPv6 address.
     */
    @Nullable
    public MasternodeAddress getPrimaryService() {
        List<NetInfoEntry> coreP2P = getEntries(NetInfoPurpose.CORE_P2P);
        return coreP2P.isEmpty() ? null : coreP2P.get(0).toMasternodeAddress();
    }

    /** the port of the first Platform P2P entry, or 0 if there is none */
    public int getPlatformP2PPort() {
        return firstPort(NetInfoPurpose.PLATFORM_P2P);
    }

    /** the port of the first Platform HTTPS entry, or 0 if there is none */
    public int getPlatformHTTPPort() {
        return firstPort(NetInfoPurpose.PLATFORM_HTTPS);
    }

    private int firstPort(NetInfoPurpose purpose) {
        for (NetInfoEntry entry : getEntries(purpose)) {
            if (!entry.isInvalid()) {
                return entry.getPort();
            }
        }
        return 0;
    }

    public ExtNetInfo duplicate() {
        return new ExtNetInfo(params, version, entries);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtNetInfo that = (ExtNetInfo) o;
        return version == that.version && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return 31 * version + entries.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("ExtNetInfo(version=").append(version);
        for (Map.Entry<NetInfoPurpose, List<NetInfoEntry>> entry : entries.entrySet()) {
            s.append(", ").append(entry.getKey()).append('=').append(entry.getValue());
        }
        return s.append(')').toString();
    }

    /** Serializes this object, ignoring any cached payload bytes */
    byte[] serializeFresh() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(calculateSize());
            bitcoinSerializeToStream(bos);
            return bos.toByteArray();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
}
