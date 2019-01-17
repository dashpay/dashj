package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static java.lang.Math.max;

/**
 * Created by Hash Engineering Solutions on 2/22/2015.
 */
public class TransactionLockRequest extends Transaction {
    private static final Logger log = LoggerFactory.getLogger(TransactionLockRequest.class);
    public static final int TIMEOUT_SECONDS = 5 * 60;
    public static final Coin MIN_FEE = Coin.valueOf(100000);

    public static final int WARN_MANY_INPUTS = 100;

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
    /*public TransactionLockRequest(NetworkParameters params, byte[] payload, int offset, @Nullable Message parent, boolean parseLazy, boolean parseRetain, int length)
            throws ProtocolException {
        super(params, payload, offset, parent, parseLazy, parseRetain, length);
    }*/

    /**
     * Creates a transaction by reading payload starting from offset bytes in. Length of a transaction is fixed.
     */
    /*public TransactionLockRequest(NetworkParameters params, byte[] payload, @Nullable Message parent, boolean parseLazy, boolean parseRetain, int length)
            throws ProtocolException {
        super(params, payload, 0, parent, parseLazy, parseRetain, length);
    }*/

    public String toString(@Nullable AbstractBlockChain chain) {
        return "Transaction Lock Request:" + super.toString(chain);
    }

    public boolean isValid(int currentblockHeight)
    {
        if(getOutputs().size() < 1) return false;

        if(getInputs().size() > WARN_MANY_INPUTS) {
            log.info("instantsend--CTxLockRequest::IsValid -- WARNING: Too many inputs: tx=", toString());
        }

        //Ideally, a dashj node is always fully synced, but if it isn't
        //then this code below will not allow a lock request for this transaction.
        //Therefore, let us skip this check and rely on lock votes.

        /*if(!isFinal(currentblockHeight+1, Utils.currentTimeSeconds())) {
            log.warn("Transaction is not final: tx=%s", toString());
            return false;
        }*/

        Coin nValueIn = Coin.ZERO;
        Coin nValueOut = Coin.ZERO;

        for(TransactionOutput txout: getOutputs()) {
            nValueOut = nValueOut.add(txout.getValue());
        }

        for(TransactionInput txin: getInputs())  {

            /*CCoins coins;

            if(!GetUTXOCoins(txin.prevout, coins)) {
                LogPrint("instantsend", "CTxLockRequest::IsValid -- Failed to find UTXO %s\n", txin.prevout.ToStringShort());
                return false;
            }

            int nTxAge = chainActive.Height() - coins.nHeight + 1;
            // 1 less than the "send IX" gui requires, in case of a block propagating the network at the time
            int nConfirmationsRequired = INSTANTSEND_CONFIRMATIONS_REQUIRED - 1;

            if(nTxAge < nConfirmationsRequired) {
                LogPrint("instantsend", "CTxLockRequest::IsValid -- outpoint %s too new: nTxAge=%d, nConfirmationsRequired=%d, txid=%s\n",
                        txin.prevout.ToStringShort(), nTxAge, nConfirmationsRequired, GetHash().ToString());
                return false;
            }

            nValueIn += coins.vout[txin.prevout.n].nValue;
    */
            Coin value = txin.getValue();
            if(value != null)
                nValueIn = nValueIn.add(value);
        }

        if(nValueIn.isGreaterThan(Coin.valueOf((int)Context.get().sporkManager.getSporkValue(SporkManager.SPORK_5_INSTANTSEND_MAX_VALUE), 0))) {
            log.info("instantsend--CTxLockRequest::IsValid -- Transaction value too high: nValueOut="+nValueOut+", tx="+toString());
            return false;
        }

        Coin fee = getFee();

        if(fee != null && fee.isLessThan(getMinFee(false))) {
            log.info("instantsend--CTxLockRequest::IsValid -- did not include enough fees in transaction: fees="+nValueOut.subtract(nValueIn)+", tx="+toString());
            return false;
        }

        return true;
    }

    public Coin getMinFee(boolean forceMinFee)
    {
        if(!forceMinFee && InstantSend.canAutoLock() && isSimple())
            return Coin.ZERO;
        Coin nMinFee = params.isDIP0001ActiveAtTip() ? MIN_FEE.div(10) : MIN_FEE;
        return Coin.valueOf(max(nMinFee.getValue(), getInputs().size() * nMinFee.getValue()));
    }

    public int getMaxSignatures()
    {
        return getInputs().size() * TransactionOutPointLock.SIGNATURES_TOTAL;
    }

    public static int getMaxSignatures(int inputs) { return inputs * TransactionOutPointLock.SIGNATURES_TOTAL; }

}
