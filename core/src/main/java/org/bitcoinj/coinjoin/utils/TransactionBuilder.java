/*
 * Copyright (c) 2022 Dash Core Group
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
package org.bitcoinj.coinjoin.utils;

import com.google.common.collect.Lists;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.CoinControl;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

public class TransactionBuilder implements AutoCloseable {
    /// Wallet the transaction will be build for
    private final WalletEx wallet;
    /// See CTransactionBuilder() for initialization
    private final CoinControl coinControl = new CoinControl();
    /// Dummy since we anyway use tallyItem's destination as change destination in coincontrol.
    /// It's a member just to make sure ReturnKey can be called in destructor just in case it gets generated/kept
    /// somewhere in CWallet code.
    private ReserveDestination dummyReserveDestination;
    /// Contains all utxos available to generate this transactions. They are all from the same address.
    private CompactTallyItem tallyItem;
    /// Contains the number of bytes required for a transaction with only the inputs of tallyItems, no outputs
    private int bytesBase = 0;
    /// Contains the number of bytes required to add one output
    private int bytesOutput = 0;
    /// Call KeepKey for all keys in destructor if fKeepKeys is true, call ReturnKey for all key if its false.
    private boolean keepKeys = false;
    /// Protect vecOutputs
    private final ReentrantLock lock = Threading.lock("outputs");
    /// Contains all outputs already added to the transaction
    @GuardedBy("lock")
    private final ArrayList<TransactionBuilderOutput> vecOutputs = new ArrayList<>();
    /// Needed by CTransactionBuilderOutput::UpdateAmount to lock cs_outputs

    private Transaction transaction;

    public TransactionBuilder(WalletEx wallet, final CompactTallyItem tallyItem) {
        this.wallet = wallet;
        dummyReserveDestination = new ReserveDestination(wallet);
        this.tallyItem = tallyItem;
        // Generate a feerate which will be used to consider if the remainder is dust and will go into fees or not
        coinControl.setDiscardFeeRate(Transaction.DEFAULT_TX_FEE);
        // Generate a feerate which will be used by calculations of this class and also by CWallet::CreateTransaction
        coinControl.setFeeRate(Transaction.DEFAULT_TX_FEE);
        // Change always goes back to origin
        coinControl.setDestChange(tallyItem.txDestination);
        // Only allow tallyItems inputs for tx creation
        coinControl.setAllowOtherInputs(false);
        // Create dummy tx to calculate the exact required fees upfront for accurate amount and fee calculations
        Transaction dummyTx = new Transaction(wallet.getParams());
        // Select all tallyItem outputs in the coinControl so that CreateTransaction knows what to use
        for (InputCoin coin : tallyItem.inputCoins) {
            coinControl.select(coin.getOutPoint());
            TransactionInput input = new TransactionInput(wallet.getParams(), dummyTx, new byte[0], coin.getOutPoint());
            dummyTx.addInput(input);
        }
        // Get a comparable dummy scriptPubKey, avoid writing/flushing to the actual wallet db
        ECKey secret = new ECKey();
        Script dummyScript = ScriptBuilder.createP2PKHOutputScript(secret);
        // Calculate required bytes for the dummy signed tx with tallyItem's inputs only
        bytesBase = calculateMaximumSignedTxSize(dummyTx, wallet, false);

        // Calculate the output size
        bytesOutput = new TransactionOutput(wallet.getParams(), null, Coin.ZERO, dummyScript.getProgram()).getMessageSize();
        // Just to make sure..
        clear();
    }

    /// Check it would be possible to add a single output with the amount amount. Returns true if its possible and false if not.
    public boolean couldAddOutput(Coin amountOutput) {
        if (amountOutput.isNegative()) {
            return false;
        }
        // Adding another output can change the serialized size of the vout size hence + GetSizeOfCompactSizeDiff()
        int bytes = getBytesTotal() + bytesOutput + getSizeOfCompactSizeDiff(1);
        return getAmountLeft(getAmountInitial(), getAmountUsed().add(amountOutput), getFee(bytes)).isGreaterThanOrEqualTo(Coin.ZERO);

    }
    /// Check if it's possible to add multiple outputs as vector of amounts. Returns true if its possible to add all of them and false if not.
    public boolean couldAddOutputs(List<Coin> vecOutputAmounts) {
        Coin amountAdditional = Coin.ZERO;
        int bytesAdditional = bytesOutput * vecOutputAmounts.size();
        for (Coin amountOutput : vecOutputAmounts) {
            if (amountOutput.isNegative()) {
                return false;
            }
            amountAdditional = amountAdditional.add(amountOutput);
        }
        // Adding other outputs can change the serialized size of the vout size hence + GetSizeOfCompactSizeDiff()
        int bytes = getBytesTotal() + bytesAdditional + getSizeOfCompactSizeDiff(vecOutputAmounts.size());
        return !getAmountLeft(getAmountInitial(), getAmountUsed().add(amountAdditional), getFee(bytes)).isNegative();

    }
    /// Add an output with the amount. Returns a pointer to the output if it could be added and nullptr if not due to insufficient amount left.
    public TransactionBuilderOutput addOutput() {
        return addOutput(Coin.ZERO);
    }
    public TransactionBuilderOutput addOutput(Coin amountOutput) {
        if (couldAddOutput(amountOutput)) {
            lock.lock();
            try {
                vecOutputs.add(new TransactionBuilderOutput(this, wallet, amountOutput));
                return vecOutputs.get(vecOutputs.size() - 1);
            } finally {
                lock.unlock();
            }
        }
        return null;
    }
    /// Get amount we had available when we started
    public Coin getAmountInitial() { return tallyItem.amount; }
    /// Get the amount currently left to add more outputs. Does respect fees.
    public Coin getAmountLeft() { return getAmountInitial().subtract(getAmountUsed()).subtract(getFee(getBytesTotal())); }
    /// Check if an amounts should be considered as dust
    public boolean isDust(Coin amount) {
        return Transaction.MIN_NONDUST_OUTPUT.isGreaterThan(amount);
    }
    /// Get the total number of added outputs
    public int countOutputs() { return vecOutputs.size(); }
    /// Create and Commit the transaction to the wallet
    public boolean commit(StringBuilder strResult) {
        // Transform the outputs to the format SendRequest.to requires
        ArrayList<Recipient> vecSend;

        lock.lock();
        try {
            vecSend = Lists.newArrayListWithExpectedSize(vecOutputs.size());
            for (TransactionBuilderOutput out : vecOutputs) {
                vecSend.add(new Recipient(out.getScript(), out.getAmount(), false));
            }
        } finally {
            lock.unlock();
        }

        // TODO: can't this go into the try block below?
        SendRequest req = SendRequest.to(wallet.getParams(), vecSend);
        req.ensureMinRequiredFee = false;

        try {
            SendRequest request = SendRequest.forTx(req.tx);
            request.aesKey = wallet.getContext().coinJoinManager.requestKeyParameter(wallet);
            request.coinControl = coinControl;
            wallet.sendCoins(request);
            transaction = request.tx;
        } catch (InsufficientMoneyException x) {
            strResult.append(x);
            return false;
        }
        keepKeys = true;

        strResult.append(req.tx.getTxId());
        return true;
    }
    /// Convert to a string
    @Override
    public String toString() {
        return String.format("TransactionBuilder(Amount initial: %s, Amount left: %s, Bytes base: %d, Bytes output: %d, Bytes total: %d, Amount used: %s, Outputs: %d, Fee rate: %s, Discard fee rate: %s, Fee: %s)",
                getAmountInitial().toFriendlyString(),
                getAmountLeft().toFriendlyString(),
                bytesBase,
                bytesOutput,
                getBytesTotal(),
                getAmountInitial().toFriendlyString(),
                countOutputs(),
                coinControl.getFeeRate().toFriendlyString(),
                coinControl.getDiscardFeeRate().toFriendlyString(),
                getFee(getBytesTotal()).toFriendlyString());
    }

    /// Clear the output vector and keep/return the included keys depending on the value of fKeepKeys
    private void clear() {
        ArrayList<TransactionBuilderOutput> vecOutputsTmp;

        // Don't hold cs_outputs while clearing the outputs which might indirectly call lock cs_wallet
        lock.lock();
        try {
            vecOutputsTmp = new ArrayList<>(vecOutputs);
            vecOutputs.clear();
        } finally {
            lock.unlock();
        }

        for (TransactionBuilderOutput key : vecOutputsTmp) {
            if (keepKeys) {
                key.keepKey();
            } else {
                key.returnKey();
            }
        }
        System.out.println("returning: " + dummyReserveDestination.address);
        // Always return this key
        dummyReserveDestination.returnDestination();
    }
    /// Get the total number of bytes used already by this transaction
    @GuardedBy("lock")
    int getBytesTotal() {
        return bytesBase + vecOutputs.size() * bytesOutput + getSizeOfCompactSizeDiff(vecOutputs.size());
    }
    /// Helper to calculate static amount left by simply subtracting an used amount and a fee from a provided initial amount.
    static Coin getAmountLeft(Coin amountInitial, Coin amountUsed, Coin fee) {
        return amountInitial.subtract(amountUsed).subtract(fee);
    }
    /// Get the amount currently used by added outputs. Does not include fees.
    @GuardedBy("lock")
    Coin getAmountUsed() {
        Coin amount = Coin.ZERO;
        for (TransactionBuilderOutput txBuilderOut : vecOutputs) {
            amount = amount.add(txBuilderOut.getAmount());
        }
        return amount;
    }
    /// Get fees based on the number of bytes and the feerate set in CoinControl.
    /// NOTE: To get the total transaction fee this should only be called once with the total number of bytes for the transaction to avoid
    /// calling CFeeRate::GetFee multiple times with subtotals as this may add rounding errors with each further call.
    Coin getFee(int bytes) {
        Coin feeCalc = coinControl.getFeeRate().multiply(bytes);
        Coin requiredFee = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.multiply(bytes).div(1000);
        if (requiredFee.isGreaterThan(feeCalc)) {
            feeCalc = requiredFee;
        }
        if (feeCalc.isGreaterThan(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.multiply(10))) {
            feeCalc = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
        }
        return feeCalc;
    }
    /// Helper to get getSizeOfCompactSizeDiff(vecOutputs.size(), vecOutputs.size() + aAdd)} 
    int getSizeOfCompactSizeDiff(int add) {
        lock.lock();
        int size;
        try {
            size = vecOutputs.size();
        } finally {
            lock.unlock();
        }

        return getSizeOfCompactSizeDiff(size, size + add);
    }
    
    int getSizeOfCompactSizeDiff(int oldSize, int newSize) {
        return VarInt.sizeOf(oldSize) - VarInt.sizeOf(newSize);
    }

    static int calculateMaximumSignedTxSize(Transaction tx, Wallet wallet, boolean useMaxSig)
    {
        ArrayList<TransactionOutput> txouts = Lists.newArrayList();
        for (TransactionInput input : tx.getInputs()) {
            Transaction mi = wallet.getTransaction(input.getOutpoint().getHash());
            // Can not estimate size without knowing the input details
            if (mi == null) {
                return -1;
            }
            checkState(input.getOutpoint().getIndex() < mi.getOutputs().size());
            txouts.add(mi.getOutput(input.getOutpoint().getIndex()));
        }
        return calculateMaximumSignedTxSize(tx, wallet, txouts, useMaxSig);
    }

    // txouts needs to be in the order of tx.vin
    static int calculateMaximumSignedTxSize(Transaction tx, Wallet wallet, List<TransactionOutput> txouts, boolean useMaxSig)
    {
        SendRequest req = SendRequest.forTx(tx);
        for (TransactionOutput output : txouts) {
            ECKey key = wallet.findKeyFromPubKeyHash(ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey()), Script.ScriptType.P2PKH);
            checkState(key != null, "there must be a key for this output");
            if (key.isEncrypted()) {
                key = wallet.getContext().coinJoinManager.requestDecryptKey(key);
            }

            req.tx.addSignedInputNoOutputsCheck(output.getOutPointFor(),output.getScriptPubKey(), key, Transaction.SigHash.ALL, false);
        }
        return tx.getMessageSize();
    }

    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public void close() {
        clear();
    }
}
