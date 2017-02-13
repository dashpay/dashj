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

    long timeCreated;
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
        timeCreated = Utils.currentTimeSeconds();
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
        return "Transaction Lock Request:\n" + super.toString(chain);
    }

    public boolean isValid(boolean fRequireUnspent)
    {
        if(getOutputs().size() < 1) return false;

        if(getInputs().size() > WARN_MANY_INPUTS) {
            log.info("instantsend--CTxLockRequest::IsValid -- WARNING: Too many inputs: tx=", toString());
        }

        /*LOCK(cs_main);
        if(!CheckFinalTx(*this)) {
            LogPrint("instantsend", "CTxLockRequest::IsValid -- Transaction is not final: tx=%s", ToString());
            return false;
        }*/

        Coin nValueIn = Coin.ZERO;
        Coin nValueOut = Coin.ZERO;

        for(TransactionOutput txout: getOutputs()) {
        // InstantSend supports normal scripts and unspendable (i.e. data) scripts.
        // TODO: Look into other script types that are normal and can be included
        if(!txout.getScriptPubKey().isSentToAddress() && !txout.getScriptPubKey().isOpReturn()) {
            log.info("instantsend", "CTxLockRequest::IsValid -- Invalid Script "+ toString());
            return false;
        }
        nValueOut = nValueOut.add(txout.getValue());
    }

        for(TransactionInput txin: getInputs())  {

        /*CCoins coins;
        int nPrevoutHeight = 0;
        if(!pcoinsTip->GetCoins(txin.prevout.hash, coins) ||
                (unsigned int)txin.prevout.n>=coins.vout.size() ||
                coins.vout[txin.prevout.n].IsNull()) {
            LogPrint("instantsend", "CTxLockRequest::IsValid -- Failed to find UTXO %s\n", txin.prevout.ToStringShort());
            // Normally above should be enough, but in case we are reprocessing this because of
            // a lot of legit orphan votes we should also check already spent outpoints.
            if(fRequireUnspent) return false;
            CTransaction txOutpointCreated;
            uint256 nHashOutpointConfirmed;
            if(!GetTransaction(txin.prevout.hash, txOutpointCreated, Params().GetConsensus(), nHashOutpointConfirmed, true) || nHashOutpointConfirmed == uint256()) {
                LogPrint("instantsend", "txLockRequest::IsValid -- Failed to find outpoint %s\n", txin.prevout.ToStringShort());
                return false;
            }
            LOCK(cs_main);
            BlockMap::iterator mi = mapBlockIndex.find(nHashOutpointConfirmed);
            if(mi == mapBlockIndex.end()) {
                // not on this chain?
                LogPrint("instantsend", "txLockRequest::IsValid -- Failed to find block %s for outpoint %s\n", nHashOutpointConfirmed.ToString(), txin.prevout.ToStringShort());
                return false;
            }
            nPrevoutHeight = mi->second ? mi->second->nHeight : 0;
        }

        int nTxAge = chainActive.Height() - (nPrevoutHeight ? nPrevoutHeight : coins.nHeight) + 1;
        // 1 less than the "send IX" gui requires, in case of a block propagating the network at the time
        int nConfirmationsRequired = INSTANTSEND_CONFIRMATIONS_REQUIRED - 1;

        if(nTxAge < nConfirmationsRequired) {
            LogPrint("instantsend", "CTxLockRequest::IsValid -- outpoint %s too new: nTxAge=%d, nConfirmationsRequired=%d, txid=%s\n",
                    txin.prevout.ToStringShort(), nTxAge, nConfirmationsRequired, GetHash().ToString());
            return false;
        }

        nValueIn += coins.vout[txin.prevout.n].nValue;
        */
    }

        if(nValueOut.isGreaterThan(Coin.valueOf((int)Context.get().sporkManager.getSporkValue(SporkManager.SPORK_5_INSTANTSEND_MAX_VALUE), 0))) {
            log.info("instantsend--CTxLockRequest::IsValid -- Transaction value too high: nValueOut="+nValueOut+", tx="+toString());
            return false;
        }

        Coin fee = getFee();
        if(fee != null) {
            if (fee.compareTo(MIN_FEE) < 0) {
                log.info("instantsend", "CTxLockRequest::IsValid -- did not include enough fees in transaction: fees=" + nValueOut.subtract(nValueIn) + ", tx=" + toString());
                return false;
            }
        }
        /*if(getFee().isLessThan(getMinFee())) {
            log.info("instantsend", "CTxLockRequest::IsValid -- did not include enough fees in transaction: fees="+nValueOut.subtract(nValueIn)+", tx="+toString());
            return false;
        }*/

        return true;
    }

    public Coin getMinFee()
    {
        return Coin.valueOf(max(MIN_FEE.getValue(), MIN_FEE.getValue() * getInputs().size()));
    }

    public int getMaxSignatures()
    {
        return getInputs().size() * TransactionOutPointLock.SIGNATURES_TOTAL;
    }

    public boolean isTimedOut()
    {
        return Utils.currentTimeSeconds() - timeCreated > TIMEOUT_SECONDS;
    }
}
