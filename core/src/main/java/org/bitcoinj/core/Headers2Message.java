/*
 * Copyright 2026 Dash Core Group
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A protocol message containing compressed block headers (DIP-0025).
 * Sent in response to "getheaders2" command.
 *
 * <p>Format:</p>
 * <ul>
 *   <li>VarInt: number of headers</li>
 *   <li>For each header: compressed header data (variable size based on bitfield)</li>
 * </ul>
 *
 * <p>The first header in a batch MUST include all fields (no compression).
 * Subsequent headers can omit fields that can be derived from context.</p>
 *
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 *
 * @see <a href="https://github.com/dashpay/dips/blob/master/dip-0025.md">DIP-0025</a>
 */
public class Headers2Message extends Message {
    private static final Logger log = LoggerFactory.getLogger(Headers2Message.class);

    /** Maximum number of headers in a single message (same as HeadersMessage) */
    public static final int MAX_HEADERS = 8000;
    //public static final int MAX_HEADERS_8000 = 2000;


    private List<Block> blockHeaders;

    /**
     * Creates a Headers2Message by parsing from payload bytes.
     *
     * @param params the network parameters
     * @param payload the raw message bytes
     * @throws ProtocolException if parsing fails
     */
    public Headers2Message(NetworkParameters params, byte[] payload) throws ProtocolException {
        super(params, payload, 0);
    }

    /**
     * Creates a Headers2Message from block headers (for serialization).
     *
     * @param params the network parameters
     * @param headers the block headers to include
     * @throws ProtocolException if the message cannot be created
     */
    public Headers2Message(NetworkParameters params, Block... headers) throws ProtocolException {
        super(params);
        blockHeaders = Arrays.asList(headers);
    }

    /**
     * Creates a Headers2Message from a list of block headers (for serialization).
     *
     * @param params the network parameters
     * @param headers the block headers to include
     * @throws ProtocolException if the message cannot be created
     */
    public Headers2Message(NetworkParameters params, List<Block> headers) throws ProtocolException {
        super(params);
        blockHeaders = headers;
    }

    @Override
    public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(blockHeaders.size()).encode());

        CompressedHeaderContext context = new CompressedHeaderContext();

        for (int i = 0; i < blockHeaders.size(); i++) {
            Block header = blockHeaders.get(i);
            boolean isFirst = (i == 0);

            CompressedBlockHeader compressed = new CompressedBlockHeader(
                    params, header, context, isFirst);
            compressed.bitcoinSerializeToStream(stream);

            context.updateAfterHeader(header);
        }
    }

    @Override
    protected void parse() throws ProtocolException {
        long numHeaders = readVarInt();
        log.info("Parsing headers2 message: numHeaders={}, payloadLength={}", numHeaders, payload.length);

        if (numHeaders > MAX_HEADERS) {
            throw new ProtocolException("Too many headers: got " + numHeaders +
                    " which is larger than " + MAX_HEADERS);
        }

        blockHeaders = new ArrayList<>();
        CompressedHeaderContext context = new CompressedHeaderContext();

        for (int i = 0; i < numHeaders; i++) {
            boolean isFirst = (i == 0);

//            if (i % 500 == 0 || i == numHeaders - 1) {
//                log.info("Parsing header {}/{}, cursor={}, remaining={}",
//                        i, numHeaders, cursor, payload.length - cursor);
//            }

            CompressedBlockHeader compressed = new CompressedBlockHeader(
                    params, payload, cursor, context, isFirst);
            cursor += compressed.getMessageSize();

            Block header = compressed.toBlock();
            blockHeaders.add(header);

            context.updateAfterHeader(header);
        }

        if (length == UNKNOWN_LENGTH) {
            length = cursor - offset;
        }

        if (log.isDebugEnabled()) {
            for (Block header : blockHeaders) {
                log.debug(header.toString());
            }
        }
    }

    /**
     * Returns the list of block headers contained in this message.
     * The headers have been decompressed and are full Block objects.
     *
     * @return the list of block headers
     */
    public List<Block> getBlockHeaders() {
        return blockHeaders;
    }
}
