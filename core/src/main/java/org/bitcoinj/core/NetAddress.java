/*
 * Copyright 2011 Google Inc.
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

package org.bitcoinj.core;

import com.google.common.net.InetAddresses;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A PeerAddress holds an IP address and port number representing the network location of
 * a peer in the Bitcoin P2P network. It exists primarily for serialization purposes.
 */
public class NetAddress extends ChildMessage {
    private static final long serialVersionUID = 7501293709324197411L;
    static final int MESSAGE_SIZE = 16;

    private InetAddress addr;

    /**
     * Construct a peer address from a serialized payload.
     */
    public NetAddress(NetworkParameters params, byte[] payload, int offset, int protocolVersion) throws ProtocolException {
        super(params, payload, offset, protocolVersion);
    }

    /**
     * Construct a peer address from a serialized payload.
     * @param params NetworkParameters object.
     * @param payload Bitcoin protocol formatted byte array containing message content.
     * @param offset The location of the first payload byte within the array.
     * @param protocolVersion Bitcoin protocol version.
     * @param serializer the serializer to use for this message.
     * @throws ProtocolException
     */
    public NetAddress(NetworkParameters params, byte[] payload, int offset, int protocolVersion, Message parent, MessageSerializer serializer) throws ProtocolException {
        super(params, payload, offset, protocolVersion, parent, serializer, UNKNOWN_LENGTH);
    }


    /**
     * Construct a peer address from a memorized or hardcoded address.
     */
    public NetAddress(InetAddress addr) {
        this.addr = checkNotNull(addr);
        length = MESSAGE_SIZE;
    }

    public NetAddress(NetworkParameters params)
    {
        super(params);
    }

    public static NetAddress localhost(NetworkParameters params) {
        return new NetAddress(InetAddresses.forString("127.0.0.1"));
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        // Java does not provide any utility to map an IPv4 address into IPv6 space, so we have to do it by hand.
        byte[] ipBytes = addr.getAddress();
        if (ipBytes.length == 4) {
            byte[] v6addr = new byte[16];
            System.arraycopy(ipBytes, 0, v6addr, 12, 4);
            v6addr[10] = (byte) 0xFF;
            v6addr[11] = (byte) 0xFF;
            ipBytes = v6addr;
        }
        stream.write(ipBytes);
    }

    @Override
    protected void parse() throws ProtocolException {
        // Format of a serialized address:
        //   16 bytes ip address
        //   2 bytes port num

        byte[] addrBytes = readBytes(16);
        try {
            addr = InetAddress.getByAddress(addrBytes);
            length = MESSAGE_SIZE;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    public InetAddress getAddr() {
        return addr;
    }

    public void setAddr(InetAddress addr) {
        unCache();
        this.addr = addr;
    }


    @Override
    public String toString() {
        String address = addr.getHostAddress();
        if(address.contains("."))
            return address;
        else return "[" + address + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetAddress other = (NetAddress) o;
        return other.addr.equals(addr) ;
        //TODO: including services and time could cause same peer to be added multiple times in collections
    }

    @Override
    public int hashCode() {
        return addr.hashCode();
    }
}
