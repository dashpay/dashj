package org.bitcoinj.core;

import javax.annotation.Nullable;

/**
 * Created by Hash Engineering Solutions on 2/22/2015.
 */
public class TransactionLockRequest extends Transaction {

    public TransactionLockRequest(NetworkParameters params)
    {
        super(params);
    }
    /**
     * Creates a transaction from the given serialized bytes, eg, from a block or a tx network message.
     */
    public TransactionLockRequest(NetworkParameters params, byte[] payloadBytes) throws ProtocolException {
        super(params, payloadBytes, 0);
    }

    /**
     * Creates a transaction by reading payload starting from offset bytes in. Length of a transaction is fixed.
     */
    public TransactionLockRequest(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
        // inputs/outputs will be created in parse()
    }

    /**
     * Creates a transaction by reading payload starting from offset bytes in. Length of a transaction is fixed.
     * @param params NetworkParameters object.
     * @param payload Bitcoin protocol formatted byte array containing message content.
     * @param offset The location of the first payload byte within the array.
     * @param parseLazy Whether to perform a full parse immediately or delay until a read is requested.
     * @param parseRetain Whether to retain the backing byte array for quick reserialization.
     * If true and the backing byte array is invalidated due to modification of a field then
     * the cached bytes may be repopulated and retained if the message is serialized again in the future.
     * @param length The length of message if known.  Usually this is provided when deserializing of the wire
     * as the length will be provided as part of the header.  If unknown then set to Message.UNKNOWN_LENGTH
     * @throws ProtocolException
     */
    public TransactionLockRequest(NetworkParameters params, byte[] payload, int offset, @Nullable Message parent, boolean parseLazy, boolean parseRetain, int length)
            throws ProtocolException {
        super(params, payload, offset, parent, parseLazy, parseRetain, length);
    }

    /**
     * Creates a transaction by reading payload starting from offset bytes in. Length of a transaction is fixed.
     */
    public TransactionLockRequest(NetworkParameters params, byte[] payload, @Nullable Message parent, boolean parseLazy, boolean parseRetain, int length)
            throws ProtocolException {
        super(params, payload, 0, parent, parseLazy, parseRetain, length);
    }

    public String toString(@Nullable AbstractBlockChain chain) {
        return "Transaction Lock Request:\n" + super.toString(chain);
    }

    public void verify() throws VerificationException {
        super.verify();
        maybeParse();

        Coin valueOut = Coin.valueOf(0);
        Coin valueIn = Coin.valueOf(0);

        for (TransactionOutput output : getOutputs()) {
            valueOut = valueOut.add(output.getValue());
        }

        if(valueOut.compareTo(Coin.valueOf(1000, 0)) > 0)
            throw new VerificationException("InstantX transaction of more than 1000");

        Coin fee = getFee();
        if(fee != null) {
            if (fee.compareTo(Coin.valueOf(0, 1)) < 0)
                throw new VerificationException("InstantX transaction with fee less than 0.01");
        }


    }
}
