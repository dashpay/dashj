package org.bitcoinj.wallet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.coinjoin.CoinJoinConstants;
import org.bitcoinj.coinjoin.CoinJoinTransactionInput;
import org.bitcoinj.coinjoin.DenominatedCoinSelector;
import org.bitcoinj.coinjoin.utils.CompactTallyItem;
import org.bitcoinj.coinjoin.utils.InputCoin;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionDestination;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProvider;
import org.bitcoinj.core.UTXOProviderException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.NetworkParameters.MAX_MONEY;

public class WalletEx extends Wallet {
    private static final Logger log = LoggerFactory.getLogger(WalletEx.class);

    //protected KeyChainGroup coinJoinKeyChainGroup;
    protected CoinJoinExtension coinjoin;

    /**
     * Creates a new, empty wallet with a randomly chosen seed and no transactions. Make sure to provide for sufficient
     * backup! Any keys will be derived from the seed. If you want to restore a wallet from disk instead, see
     * {@link #loadFromFile}.
     *
     * @param params network parameters
     * @deprecated Use {@link #createDeterministic(NetworkParameters, ScriptType)}
     */
    @Deprecated
    public WalletEx(NetworkParameters params) {
        this(params, KeyChainGroup.builder(params).fromRandom(Script.ScriptType.P2PKH).build());
    }

    /**
     * Creates a new, empty wallet with a randomly chosen seed and no transactions. Make sure to provide for sufficient
     * backup! Any keys will be derived from the seed. If you want to restore a wallet from disk instead, see
     * {@link #loadFromFile}.
     *
     * @param context
     * @deprecated Use {@link #createDeterministic(Context, ScriptType)}
     */
    @Deprecated
    public WalletEx(Context context) {
        this(context, KeyChainGroup.builder(context.getParams()).fromRandom(Script.ScriptType.P2PKH).build());
    }

    public WalletEx(NetworkParameters params, KeyChainGroup keyChainGroup) {
        this(Context.getOrCreate(params), keyChainGroup);
    }

    protected WalletEx(Context context, KeyChainGroup keyChainGroup) {
        super(context, keyChainGroup);
        coinjoin = new CoinJoinExtension(this);
        addExtension(coinjoin);
    }

    /**
     * Creates a new, empty wallet with a randomly chosen seed and no transactions. Make sure to provide for sufficient
     * backup! Any keys will be derived from the seed. If you want to restore a wallet from disk instead, see
     * {@link #loadFromFile}.
     * @param params network parameters
     * @param outputScriptType type of addresses (aka output scripts) to generate for receiving
     */
    public static WalletEx createDeterministic(NetworkParameters params, Script.ScriptType outputScriptType) {
        return createDeterministic(Context.getOrCreate(params), outputScriptType);
    }

    /**
     * Creates a new, empty wallet with a randomly chosen seed and no transactions. Make sure to provide for sufficient
     * backup! Any keys will be derived from the seed. If you want to restore a wallet from disk instead, see
     * {@link #loadFromFile}.
     * @param outputScriptType type of addresses (aka output scripts) to generate for receiving
     */
    public static WalletEx createDeterministic(Context context, Script.ScriptType outputScriptType) {
        return new WalletEx(context, KeyChainGroup.builder(context.getParams()).fromRandom(outputScriptType).build());
    }

    /**
     * @param params network parameters
     * @param seed deterministic seed
     * @param outputScriptType type of addresses (aka output scripts) to generate for receiving
     * @param structure structure for your wallet
     * @return a wallet from a deterministic seed with a default account path
     */
    public static WalletEx fromSeed(NetworkParameters params, DeterministicSeed seed, Script.ScriptType outputScriptType,
                                  KeyChainGroupStructure structure) {
        return new WalletEx(params, KeyChainGroup.builder(params, structure).fromSeed(seed, outputScriptType).build());
    }

    /**
     * @param params network parameters
     * @param seed deterministic seed
     * @param outputScriptType type of addresses (aka output scripts) to generate for receiving
     * @return a wallet from a deterministic seed with a default account path
     */
    public static WalletEx fromSeed(NetworkParameters params, DeterministicSeed seed,
                                  Script.ScriptType outputScriptType) {
        return fromSeed(params, seed, outputScriptType, KeyChainGroupStructure.DEFAULT);
    }

    /**
     * Creates a wallet that tracks payments to and from the HD key hierarchy rooted by the given watching key. This HAS
     * to be an account key as returned by {@link DeterministicKeyChain#getWatchingKey()}.
     */
    public static WalletEx fromWatchingKey(NetworkParameters params, DeterministicKey watchKey,
                                         Script.ScriptType outputScriptType) {
        DeterministicKeyChain chain = DeterministicKeyChain.builder().watch(watchKey).outputScriptType(outputScriptType)
                .build();
        return new WalletEx(params, KeyChainGroup.builder(params).addChain(chain).build());
    }

    /**
     * Creates a wallet that tracks payments to and from the HD key hierarchy rooted by the given watching key. This HAS
     * to be an account key as returned by {@link DeterministicKeyChain#getWatchingKey()}.
     * @deprecated Use {@link #fromWatchingKey(NetworkParameters, DeterministicKey, Script.ScriptType)}
     */
    @Deprecated
    public static WalletEx fromWatchingKey(NetworkParameters params, DeterministicKey watchKey) {
        return fromWatchingKey(params, watchKey, Script.ScriptType.P2PKH);
    }

    /**
     * Creates a wallet that tracks payments to and from the HD key hierarchy rooted by the given watching key. The
     * account path is specified. The key is specified in base58 notation and the creation time of the key. If you don't
     * know the creation time, you can pass {@link DeterministicHierarchy#BIP32_STANDARDISATION_TIME_SECS}.
     */
    public static WalletEx fromWatchingKeyB58(NetworkParameters params, String watchKeyB58, long creationTimeSeconds) {
        final DeterministicKey watchKey = DeterministicKey.deserializeB58((DeterministicKey)null, watchKeyB58, params);
        watchKey.setCreationTimeSeconds(creationTimeSeconds);
        return fromWatchingKey(params, watchKey, outputScriptTypeFromB58(params, watchKeyB58));
    }

    /**
     * Creates a new keychain and activates it using the seed of the active key chain, if the path does not exist.
     */
    @VisibleForTesting
    public void initializeCoinJoin() {
        getCoinJoin().addKeyChain(getKeyChainSeed(), derivationPathFactory.coinJoinDerivationPath());
    }

    public Coin getDenominatedBalance() {
        return getBalance(BalanceType.DENOMINATED);
    }

    public Coin getCoinJoinBalance() {
        return getBalance(BalanceType.COINJOIN);
    }

    /**
     * Returns the balance of this wallet as calculated by the provided balanceType.
     */
    @Override
    public Coin getBalance(BalanceType balanceType) {
        lock.lock();
        try {
            if (balanceType == BalanceType.AVAILABLE || balanceType == BalanceType.AVAILABLE_SPENDABLE) {
                List<TransactionOutput> candidates = calculateAllSpendCandidates(true, balanceType == BalanceType.AVAILABLE_SPENDABLE);
                CoinSelection selection = coinSelector.select(MAX_MONEY, candidates);
                return selection.valueGathered;
            } else if (balanceType == BalanceType.ESTIMATED || balanceType == BalanceType.ESTIMATED_SPENDABLE) {
                List<TransactionOutput> all = calculateAllSpendCandidates(false, balanceType == BalanceType.ESTIMATED_SPENDABLE);
                Coin value = Coin.ZERO;
                for (TransactionOutput out : all) value = value.add(out.getValue());
                return value;
            } else if (balanceType == BalanceType.COINJOIN|| balanceType == BalanceType.COINJOIN_SPENDABLE) {
                List<TransactionOutput> all = calculateAllSpendCandidates(true, balanceType == BalanceType.COINJOIN_SPENDABLE);
                Coin value = Coin.ZERO;
                for (TransactionOutput out : all) {
                    // exclude non coinjoin outputs if isCoinJoinOnly is true
                    // exclude coinjoin outputs when isCoinJoinOnly is false
                    boolean isCoinJoin = out.isDenominated() && out.isCoinJoin(this) && isFullyMixed(out);

                    if (isCoinJoin)
                        value = value.add(out.getValue());
                }
                return value;
                //CoinSelection selection = new CoinJoinCoinSelector(this).select(NetworkParameters.MAX_MONEY, candidates);
                //return selection.valueGathered;
            } else if (balanceType == BalanceType.DENOMINATED || balanceType == BalanceType.DENOMINATED_SPENDABLE) {
                List<TransactionOutput> candidates = calculateAllSpendCandidates(false, balanceType == BalanceType.DENOMINATED_SPENDABLE);
                CoinSelection selection = DenominatedCoinSelector.get().select(MAX_MONEY, candidates);
                Coin value = Coin.ZERO;
                for (TransactionOutput out : selection.gathered) {
                    if (out.isDenominated())
                        value = value.add(out.getValue());
                }
                return value;
            } else {
                throw new AssertionError("Unknown balance type");  // Unreachable.
            }
        } finally {
            lock.unlock();
        }
    }

    public Balance getBalanceInfo() {
        return new Balance()
                .setMyTrusted(getBalance(BalanceType.AVAILABLE_SPENDABLE))
                .setMyUntrustedPending(Coin.ZERO)
                .setDenominatedTrusted(getBalance(BalanceType.DENOMINATED_SPENDABLE))
                //.setDenominatedUntrustedPending(getBalance(BalanceType.DENOMINATED_FOR_MIXING))
                .setAnonymized(getBalance(BalanceType.COINJOIN_SPENDABLE))
                // watch only
                .setWatchOnlyImmature(Coin.ZERO)
                .setWatchOnlyTrusted(Coin.ZERO)
                .setWatchOnlyUntrustedPending(Coin.ZERO);

        //TODO: support as many balance types as possible
    }

    @Override
    public boolean isCoinJoinPubKeyHashMine(byte[] pubKeyHash, @Nullable Script.ScriptType scriptType) {
        return coinjoin.findKeyFromPubKeyHash(pubKeyHash, scriptType) != null;
    }

    @Override
    public boolean isCoinJoinPubKeyMine(byte[] pubKey) {
        return coinjoin.findKeyFromPubKey(pubKey) != null;
    }

    @Override
    public boolean isCoinJoinPayToScriptHashMine(byte[] payToScriptHash) {
        return  coinjoin.findRedeemDataFromScriptHash(payToScriptHash) != null;
    }

    public boolean hasCollateralInputs() {
        return hasCollateralInputs(true);
    }

    public boolean hasCollateralInputs(boolean onlyConfirmed) {
        ArrayList<TransactionOutput> vCoins = Lists.newArrayList();
        CoinControl coin_control = new CoinControl();
        coin_control.setCoinType(CoinType.ONLY_COINJOIN_COLLATERAL);
        availableCoins(vCoins, onlyConfirmed, coin_control);

        return !vCoins.isEmpty();
    }

    public boolean isSpent(Sha256Hash hash, long index) {
        lock.lock();
        try {
            // TODO: should this be spent.contains(hash)?
            Transaction tx = unspent.get(hash);
            if (tx == null) {
                return spent.get(hash) != null;
            }

            return tx.getOutput(index).getSpentBy() != null;
        } finally {
            lock.unlock();
        }
    }

    HashSet<TransactionOutPoint> lockedCoinsSet = Sets.newHashSet();
    public boolean isLockedCoin(Sha256Hash hash, long index) {
        lock.lock();
        try {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, index, hash);
            return lockedCoinsSet.contains(outPoint);
        } finally {
            lock.unlock();
        }
    }

    HashMap<TransactionOutPoint, Integer> mapOutpointRoundsCache = new HashMap<>();
    // Recursively determine the rounds of a given input (How deep is the CoinJoin chain for a given input)
    int getRealOutpointCoinJoinRounds(TransactionOutPoint outPoint) {
        return getRealOutpointCoinJoinRounds(outPoint, 0);
    }

    boolean isMine(TransactionDestination dest) {
        return isMine(dest.getScript());
    }

    boolean isMine(Script script) {
        return canSignFor(script);
    }

    boolean isMine(TransactionOutput txout) {
        return isMine(txout.getScriptPubKey());
    }

    boolean isMine(TransactionInput input) {
        lock.lock();
        try {
            Transaction tx = getTransaction(input.getOutpoint().getHash());
            if (tx != null) {
                if (input.getOutpoint().getIndex() < tx.getOutputs().size()) {
                    return tx.getOutput(input.getOutpoint().getIndex()).isMine(this);
                }
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    int getRealOutpointCoinJoinRounds(TransactionOutPoint outpoint, int rounds) {
        lock.lock();
        try {

            final int roundsMax = CoinJoinConstants.MAX_COINJOIN_ROUNDS + CoinJoinClientOptions.getRandomRounds();

            if (rounds >= roundsMax) {
                // there can only be roundsMax rounds max
                return roundsMax - 1;
            }

            Integer roundsRef = mapOutpointRoundsCache.get(outpoint);
            if (roundsRef == null) {
                roundsRef = -10;
                mapOutpointRoundsCache.put(outpoint, roundsRef);
            } else {
                return roundsRef;
            }

            // TODO wtx should refer to a CWalletTx object, not a pointer, based on surrounding code
            WalletTransaction wtx = getWalletTransaction(outpoint.getHash());

            if (wtx == null || wtx.getTransaction() == null) {
                // no such tx in this wallet
                roundsRef = -1;
                mapOutpointRoundsCache.put(outpoint, roundsRef);

                log.error(String.format("FAILED    %-70s %3d (no such tx)", outpoint.toStringCpp(), -1));
                return roundsRef;
            }

            // bounds check
            if (outpoint.getIndex() >= wtx.getTransaction().getOutputs().size()) {
                // should never actually hit this
                roundsRef = -4;
                mapOutpointRoundsCache.put(outpoint, roundsRef);
                log.error(String.format("FAILED    %-70s %3d (bad index)", outpoint.toStringCpp(), -4));
                return roundsRef;
            }

            TransactionOutput txOut = wtx.getTransaction().getOutput(outpoint.getIndex());

            if (CoinJoin.isCollateralAmount (txOut.getValue())) {
                roundsRef = -3;
                mapOutpointRoundsCache.put(outpoint, roundsRef);

                log.info(String.format("UPDATED   %-70s %3d (collateral)", outpoint.toStringCpp(), roundsRef));
                return roundsRef;
            }

            // make sure the final output is non-denominate
            if (!CoinJoin.isDenominatedAmount (txOut.getValue())){ //NOT DENOM
                roundsRef = -2;
                mapOutpointRoundsCache.put(outpoint, roundsRef);

                log.info(String.format("UPDATED   %-70s %3d (non-denominated)", outpoint.toStringCpp(), roundsRef));
                return roundsRef;
            }

            for (TransactionOutput out :wtx.getTransaction().getOutputs()){
                if (!CoinJoin.isDenominatedAmount (out.getValue())){
                    // this one is denominated but there is another non-denominated output found in the same tx
                    roundsRef = 0;
                    mapOutpointRoundsCache.put(outpoint, roundsRef);

                    log.info(String.format("UPDATED   %-70s %3d (non-denominated)", outpoint.toStringCpp(), roundsRef));
                    return roundsRef;
                }
            }

            int nShortest = -10; // an initial value, should be no way to get this by calculations
            boolean fDenomFound = false;
            // only denoms here so let's look up
            for (TransactionInput txinNext :wtx.getTransaction().getInputs()){
                if (isMine(txinNext)) {
                    int n = getRealOutpointCoinJoinRounds(txinNext.getOutpoint(), rounds + 1);
                    // denom found, find the shortest chain or initially assign nShortest with the first found value
                    if (n >= 0 && (n < nShortest || nShortest == -10)) {
                        nShortest = n;
                        fDenomFound = true;
                    }
                }
            }
            roundsRef = fDenomFound
                    ? (nShortest >= roundsMax - 1 ? roundsMax : nShortest + 1) // good, we a +1 to the shortest one but only roundsMax rounds max allowed
                    : 0;            // too bad, we are the fist one in that chain
            mapOutpointRoundsCache.put(outpoint, roundsRef);
            log.info(String.format("UPDATED   %-70s %3d (coinjoin)", outpoint.toStringCpp(), roundsRef));
            return roundsRef;
        } finally {
            lock.unlock();
        }
    }

    Sha256Hash coinJoinSalt = Sha256Hash.ZERO_HASH;

    public boolean isFullyMixed(TransactionOutput output) {
        return isFullyMixed(new TransactionOutPoint(params, output));
    }

    public boolean isFullyMixed(TransactionOutPoint outPoint) {
        lock.lock();
        try {
            int rounds = getRealOutpointCoinJoinRounds(outPoint);
            // Mix again if we don't have N rounds yet
            if (rounds < CoinJoinClientOptions.getRounds()) return false;

            // Try to mix a "random" number of rounds more than minimum.
            // If we have already mixed N + MaxOffset rounds, don't mix again.
            // Otherwise, we should mix again 50% of the time, this results in an exponential decay
            // N rounds 50% N+1 25% N+2 12.5%... until we reach N + GetRandomRounds() rounds where we stop.
            if (rounds < CoinJoinClientOptions.getRounds() + CoinJoinClientOptions.getRandomRounds()) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try {
                    outPoint.bitcoinSerialize(stream);
                    stream.write(coinJoinSalt.getReversedBytes());
                    Sha256Hash hash = Sha256Hash.twiceOf(stream.toByteArray());
                    if (Utils.readInt64(hash.getBytes(), 0) % 2 == 0) {
                        return false;
                    }
                } catch (IOException x) {
                    throw new RuntimeException(x);
                }
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean anonymizableTallyCached = false;
    ArrayList<CompactTallyItem> vecAnonymizableTallyCached = new ArrayList<>();
    boolean anonymizableTallyCachedNonDenom = false;
    void clearAnonymizableCaches() {
        anonymizableTallyCachedNonDenom = false;
        anonymizableTallyCached = false;
    }
    ArrayList<CompactTallyItem> vecAnonymizableTallyCachedNonDenom = new ArrayList<>();

    public List<CompactTallyItem> selectCoinsGroupedByAddresses(boolean skipDenominated,
                                                                boolean anonymizable,
                                                                boolean skipUnconfirmed) {
        return selectCoinsGroupedByAddresses(skipDenominated, anonymizable, skipUnconfirmed, -1);
    }

    public List<CompactTallyItem> selectCoinsGroupedByAddresses(boolean skipDenominated,
                                                                boolean anonymizable,
                                                                boolean skipUnconfirmed,
                                                                int maxOutpointsPerAddress) {
        List<TransactionOutput> candidates = calculateAllSpendCandidates(true, true);

        CoinSelection selection = skipUnconfirmed ?
                DefaultCoinSelector.get().select(MAX_MONEY, candidates) :
                ZeroConfCoinSelector.get().select(MAX_MONEY, candidates);

        lock.lock();
        try {
            // Try using the cache for already confirmed mixable inputs.
            // This should only be used if maxOupointsPerAddress was NOT specified.
            if(maxOutpointsPerAddress == -1 && anonymizable && skipUnconfirmed) {
                if(skipDenominated && anonymizableTallyCachedNonDenom) {
                    log.info("SelectCoinsGroupedByAddresses - using cache for non-denom inputs {}", vecAnonymizableTallyCachedNonDenom.size());
                    return vecAnonymizableTallyCachedNonDenom;
                }
                if(!skipDenominated && anonymizableTallyCached) {
                    log.info("SelectCoinsGroupedByAddresses - using cache for all inputs {}", vecAnonymizableTallyCached.size());
                    return vecAnonymizableTallyCached;
                }
            }

            Coin smallestDenom = CoinJoin.getSmallestDenomination();

            // Tally
            HashMap<TransactionDestination, CompactTallyItem> mapTally = new HashMap<>();
            HashSet<Sha256Hash> setWalletTxesCounted = new HashSet<>();
            for (TransactionOutput outpoint : selection.gathered) {

                if (!setWalletTxesCounted.add(outpoint.getParentTransactionHash()))
                    continue;

                Transaction wtx = getTransaction(outpoint.getParentTransactionHash());
                if (wtx == null)
                    continue;

                if (wtx.isCoinBase() && wtx.isMature())
                    continue;

                TransactionConfidence confidence = wtx.getConfidence();
                if (skipUnconfirmed && !wtx.isTrusted(this))
                    continue;

                if (confidence.getConfidenceType() != TransactionConfidence.ConfidenceType.BUILDING && confidence.getConfidenceType() != TransactionConfidence.ConfidenceType.PENDING)
                    continue;

                // why do we need to cycle through the outputs if we have them already?
                // it seems like this loop find a few more outputs that are not in selection.gathered
                for (int i = 0; i < wtx.getOutputs().size(); i++) {
                    TransactionDestination txdest = TransactionDestination.fromScript(wtx.getOutput(i).getScriptPubKey());
                    if (txdest == null)
                        continue;

                    boolean mine = isMine(txdest);
                    if (!mine) continue;

                    CompactTallyItem itTallyItem = mapTally.get(txdest);
                    if (maxOutpointsPerAddress != -1 && itTallyItem != null && (long) (itTallyItem.inputCoins.size()) >= maxOutpointsPerAddress)
                        continue;

                    if (isSpent(outpoint.getParentTransactionHash(), i) || isLockedCoin(outpoint.getParentTransactionHash(), i))
                        continue;

                    if (skipDenominated && CoinJoin.isDenominatedAmount(wtx.getOutput(i).getValue()))
                        continue;

                    if (anonymizable) {
                        // ignore collaterals
                        if (CoinJoin.isCollateralAmount(wtx.getOutput(i).getValue())) continue;
                        // ignore outputs that are 10 times smaller than the smallest denomination
                        // otherwise they will just lead to higher fee / lower priority
                        if (wtx.getOutput(i).getValue().isLessThan(smallestDenom.div(10)) ||
                                wtx.getOutput(i).getValue().equals(smallestDenom.div(10))) continue;
                        // ignore mixed
                        if (isFullyMixed(new TransactionOutPoint(params, i, outpoint.getParentTransactionHash()))) continue;
                    }

                    if (itTallyItem == null) {
                        itTallyItem = new CompactTallyItem();
                        itTallyItem.txDestination = txdest;
                        mapTally.put(txdest, itTallyItem);
                    }
                    itTallyItem.amount = itTallyItem.amount.add(wtx.getOutput(i).getValue());
                    itTallyItem.inputCoins.add(new InputCoin(wtx, i));
                }
            }

            // construct resulting vector
            // NOTE: vecTallyRet is "sorted" by txdest (i.e. address), just like mapTally
            ArrayList<CompactTallyItem> vecTallyRet = Lists.newArrayList();
            for (Map.Entry<TransactionDestination, CompactTallyItem> item : mapTally.entrySet()) {
                if (anonymizable && item.getValue().amount.isLessThan(smallestDenom)) continue;
                vecTallyRet.add(item.getValue());
            }

            // Cache already confirmed mixable entries for later use.
            // This should only be used if nMaxOupointsPerAddress was NOT specified.
            if (maxOutpointsPerAddress == -1 && anonymizable && skipUnconfirmed) {
                if (skipDenominated) {
                    vecAnonymizableTallyCachedNonDenom = vecTallyRet;
                    anonymizableTallyCachedNonDenom = true;
                } else {
                    vecAnonymizableTallyCached = vecTallyRet;
                    anonymizableTallyCached = true;
                }
            }

            // debug

            StringBuilder strMessage = new StringBuilder("vecTallyRet:\n");
            for (CompactTallyItem item :vecTallyRet)
                strMessage.append(String.format("  %s %s\n", item.txDestination, item.amount.toFriendlyString()));
            log.info(strMessage.toString()); /* Continued */

            return vecTallyRet;
        } finally {
            lock.unlock();
        }
    }


    public int countInputsWithAmount(Coin inputValue) {
        int count = 0;
        for (Transaction tx : getTransactionPool(WalletTransaction.Pool.UNSPENT).values()) {
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.getValue().equals(inputValue) && output.isMine(this))
                    count++;
            }
        }
        return count;
    }

    // TODO: Do we need these lock functions
    public boolean lockCoin(TransactionOutPoint outPoint) {
        lockedCoinsSet.add(outPoint);
        clearAnonymizableCaches();
        return false;
    }

    public void unlockCoin(TransactionOutPoint outPoint) {
        lockedCoinsSet.remove(outPoint);
        clearAnonymizableCaches();
    }

    //TODO: need to implement these methods
    public Coin getAnonymizableBalance() {
        return getAnonymizableBalance(false, true);
    }

    public Coin getAnonymizableBalance(boolean skipDenominated) {
        return getAnonymizableBalance(skipDenominated, true);
    }
    public Coin getAnonymizableBalance(boolean skipDenominated, boolean skipUnconfirmed) {
        if (!CoinJoinClientOptions.isEnabled())
            return Coin.ZERO;

        List<CompactTallyItem> tallyItems = selectCoinsGroupedByAddresses(skipDenominated, true, skipUnconfirmed);
        if (tallyItems.isEmpty())
            return Coin.ZERO;

        Coin total = Coin.ZERO;

        Coin smallestDenom = CoinJoin.getSmallestDenomination();
        Coin mixingCollateral = CoinJoin.getCollateralAmount();
        for (CompactTallyItem item : tallyItems) {
            boolean isDenominated = CoinJoin.isDenominatedAmount(item.amount);
            if(skipDenominated && isDenominated)
                continue;
            // assume that the fee to create denoms should be mixing collateral at max
            if(item.amount.isGreaterThanOrEqualTo(smallestDenom.add((isDenominated ? Coin.ZERO : mixingCollateral))))
                total = total.add(item.amount);
        }

        return total;
    }

    boolean getDestData(TransactionDestination dest, String key, StringBuilder value) {
        // TODO: we are not storing this currently
        // add something to the Key entry that it was used?

        /*markKeysAsUsed();
        std::map<CTxDestination, CAddressBookData>::const_iterator i = mapAddressBook.find(dest);
        if(i != mapAddressBook.end())
        {
            CAddressBookData::StringMap::const_iterator j = i->second.destdata.find(key);
            if(j != i->second.destdata.end())
            {
                if(value)
                *value = j->second;
                return true;
            }
        }
        return false;*/
        return false;
    }

    boolean isUsedDestination(TransactionDestination destination) {
        lock.lock();
        try {
            return isMine(destination) && getDestData(destination, "used", null);
        } finally {
            lock.unlock();
        }
    }

    boolean isUsedDestination(Sha256Hash hash, int index) {
        TransactionDestination destination;
        WalletTransaction walletSrcTx = getWalletTransaction(hash);
        return walletSrcTx != null &&
                (destination = TransactionDestination.fromScript(walletSrcTx.getTransaction().getOutput(index).getScriptPubKey())) != null &&
                isUsedDestination(destination);
    }

    public void availableCoins(ArrayList<TransactionOutput> vCoins) {
        availableCoins(vCoins, true, null, Coin.SATOSHI, MAX_MONEY, MAX_MONEY, 0, 0, 9999999);
    }

    public void availableCoins(ArrayList<TransactionOutput> vCoins,
                               boolean onlySafe) {
        availableCoins(vCoins, onlySafe, null, Coin.SATOSHI, MAX_MONEY, MAX_MONEY, 0, 0, 9999999);
    }

    public void availableCoins(ArrayList<TransactionOutput> vCoins,
                               boolean onlySafe,
                               @Nullable CoinControl coinControl) {
        availableCoins(vCoins, onlySafe, coinControl, Coin.SATOSHI, MAX_MONEY, MAX_MONEY, 0, 0, 9999999);
    }

    public void availableCoins(ArrayList<TransactionOutput> vCoins,
                               boolean onlySafe,
                               @Nullable CoinControl coinControl,
                               Coin nMinimumAmount, Coin nMaximumAmount,
                               Coin nMinimumSumAmount, int maximumCount,
                               int minDepth, int maxDepth
    ) {
        lock.lock();
        try {
            vCoins.clear();
            CoinType nCoinType = coinControl != null ? coinControl.getCoinType() : CoinType.ALL_COINS;

            Coin total = Coin.ZERO;
            // Either the WALLET_FLAG_AVOID_REUSE flag is not set (in which case we always allow), or we default to avoiding, and only in the case where
            // a coin control object is provided, and has the avoid address reuse flag set to false, do we allow already used addresses
            boolean allowUsedAddresses = /*!IsWalletFlagSet(WALLET_FLAG_AVOID_REUSE) ||*/ (coinControl != null && !coinControl.shouldAvoidAddressReuse());

            for (Transaction coin : unspent.values()) {
                final Sha256Hash wtxid = coin.getTxId();

                if (!coin.isFinal(getLastBlockSeenHeight(), getLastBlockSeenTimeSecs()))
                    continue;

                if (!coin.isMature())
                    continue;

                boolean safeTx = coin.isTrusted(this);

                if (onlySafe && !safeTx) {
                    continue;
                }

                int depth = coin.getConfidence().getDepthInBlocks();
                if (depth < minDepth || depth > maxDepth)
                    continue;

                for (int i = 0; i < coin.getOutputs().size(); ++i) {
                    boolean found = false;
                    Coin value = coin.getOutput(i).getValue();
                    if (nCoinType == CoinType.ONLY_FULLY_MIXED) {
                        if (!CoinJoin.isDenominatedAmount (value))
                            continue;
                        found = isFullyMixed(new TransactionOutPoint(params, i, wtxid));
                    } else if (nCoinType == CoinType.ONLY_READY_TO_MIX) {
                        if (!CoinJoin.isDenominatedAmount (value))
                            continue;
                        found = !isFullyMixed(new TransactionOutPoint(params, i, wtxid));
                    } else if (nCoinType == CoinType.ONLY_NONDENOMINATED) {
                        if (CoinJoin.isCollateralAmount (value))
                            continue; // do not use collateral amounts
                        found = !CoinJoin.isDenominatedAmount (value);
                    } else if (nCoinType == CoinType.ONLY_MASTERNODE_COLLATERAL) {
                        found = value == Coin.valueOf(1000,0);
                    } else if (nCoinType == CoinType.ONLY_COINJOIN_COLLATERAL) {
                        found = CoinJoin.isCollateralAmount (value);
                    } else {
                        found = true;
                    }
                    if (!found) continue;

                    if (value.isLessThan(nMinimumAmount) || value.isGreaterThan(nMaximumAmount))
                        continue;

                    if (coinControl != null && coinControl.hasSelected() && !coinControl.shouldAllowOtherInputs()
                            && !coinControl.isSelected(new TransactionOutPoint(params, i, wtxid)))
                        continue;

                    if (isLockedCoin(wtxid, i) && nCoinType != CoinType.ONLY_MASTERNODE_COLLATERAL)
                        continue;

                    if (isSpent(wtxid, i))
                        continue;

                    boolean mine = isMine(coin.getOutput(i));

                    if (!mine) {
                        continue;
                    }

                    if (!allowUsedAddresses && isUsedDestination(wtxid, i)) {
                        continue;
                    }

                    // our TransactionOutput doesn't have a place to put these fields, add them?
                    //const SigningProvider * provider = GetSigningProvider();

                    //boolean solvable = provider ? IsSolvable( * provider, pcoin->tx -> vout[i].scriptPubKey) :false;
                    //boolean spendable = (mine || (((mine & ISMINE_WATCH_ONLY) != ISMINE_NO) && (coinControl && coinControl -> fAllowWatchOnly && solvable));

                    vCoins.add(coin.getOutput(i));//new TransactionOutput(params, coin, coin.getOutput(i).getValue(), coin.getOutput(i).getScriptBytes()));

                    // Checks the sum amount of all UTXO's.
                    if (nMinimumSumAmount != MAX_MONEY) {
                        total = total.add(value);

                        if (total.isGreaterThanOrEqualTo(nMinimumSumAmount)) {
                            return;
                        }
                    }

                    // Checks the maximum number of UTXO's.
                    if (maximumCount > 0 && vCoins.size() >= maximumCount) {
                        return;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }


    public boolean selectTxDSInsByDenomination(int nDenom, Coin nValueMax, List<CoinJoinTransactionInput> vecTxDSInRet) {

        Coin nValueTotal = Coin.ZERO;

        HashSet<Sha256Hash> setRecentTxIds = new HashSet<>();
        ArrayList<TransactionOutput> vCoins = new ArrayList<>();

        vecTxDSInRet.clear();

        if (!CoinJoin.isValidDenomination(nDenom)) {
            return false;
        }

        Coin nDenomAmount = CoinJoin.denominationToAmount(nDenom);

        CoinControl coin_control = new CoinControl();
        coin_control.setCoinType(CoinType.ONLY_READY_TO_MIX);
        availableCoins(vCoins, true, coin_control);
        log.info("-- vCoins.size(): {}", vCoins.size());

        Collections.shuffle(vCoins);

        for (final TransactionOutput out : vCoins) {
            Sha256Hash txHash = out.getParentTransactionHash();
            Coin nValue = out.getParentTransaction().getOutput(out.getIndex()).getValue();
            if (setRecentTxIds.contains(txHash))
                continue; // no duplicate txids
            if (nValueTotal.add(nValue).isGreaterThan(nValueMax))
                continue;
            if (!nValue.equals(nDenomAmount))
                continue;

            TransactionInput txin = new TransactionInput(params, null, new byte[0], new TransactionOutPoint(params, out.getIndex(), txHash));
            Script scriptPubKey = out.getParentTransaction().getOutput(out.getIndex()).getScriptPubKey();
            int nRounds = getRealOutpointCoinJoinRounds(txin.getOutpoint());

            nValueTotal = nValueTotal.add(nValue);
            vecTxDSInRet.add(new CoinJoinTransactionInput(txin, scriptPubKey, nRounds));
            log.info("coinjoin test {}\n  {}", new CoinJoinTransactionInput(txin, scriptPubKey, nRounds), new CoinJoinTransactionInput(txin, scriptPubKey, nRounds).toStringHex());
            setRecentTxIds.add(txHash);
            log.info("coinjoin:  -- hash: {}, nValue: {}", txHash, nValue.toFriendlyString());
        }

        log.info("coinjoin:   -- setRecentTxIds.size(): {}", setRecentTxIds.size());

        return nValueTotal.isPositive();
    }

    static class CompareByPriority implements Comparator<TransactionOutput> {

        @Override
        public int compare(TransactionOutput transactionOutput, TransactionOutput transactionOutputTwo) {
            return (int)(CoinJoin.calculateAmountPriority(transactionOutput.getValue()) - CoinJoin.calculateAmountPriority(transactionOutputTwo.getValue()));
        }
    }
    public boolean selectDenominatedAmounts(Coin valueMax, Set<Coin> setAmountsRet) {
        lock.lock();
        try {

            Coin valueTotal = Coin.ZERO;
            setAmountsRet.clear();

            ArrayList<TransactionOutput> vCoins = new ArrayList<>();
            CoinControl coin_control = new CoinControl();
            coin_control.setCoinType(CoinType.ONLY_READY_TO_MIX);
            availableCoins(vCoins, true, coin_control);
            // larger denoms first
            Collections.sort(vCoins, new CompareByPriority());

            for (TransactionOutput out : vCoins) {
                Coin value = out.getValue();
                if (valueTotal.add(value).isLessThanOrEqualTo(valueMax)) {
                    valueTotal = valueTotal.add(value);
                    setAmountsRet.add(value);
                }
            }

            return valueTotal.isGreaterThanOrEqualTo(CoinJoin.getSmallestDenomination());
        } finally {
            lock.unlock();
        }
    }

    /**
     * If the transactions outputs are all marked as spent, and it's in the unspent map, move it.
     * If the owned transactions outputs are not all marked as spent, and it's in the spent map, move it.
     */
    @Override
    protected void maybeMovePool(Transaction tx, String context) {
        super.maybeMovePool(tx, context);
        clearAnonymizableCaches();
    }

    /**
     * Adds the given transaction to the given pools and registers a confidence change listener on it.
     */
    @Override
    protected void addWalletTransaction(WalletTransaction.Pool pool, Transaction tx) {
        super.addWalletTransaction(pool, tx);
        clearAnonymizableCaches();
    }

    @Override
    public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException {
        super.reorganize(splitPoint, oldBlocks, newBlocks);
        clearAnonymizableCaches();
    }

    /**
     * Returns a list of all outputs that are being tracked by this wallet either from the {@link UTXOProvider}
     * (in this case the existence or not of private keys is ignored), or the wallets internal storage (the default)
     * taking into account the flags.
     *
     * @param excludeImmatureCoinbases Whether to ignore coinbase outputs that we will be able to spend in future once they mature.
     * @param excludeUnsignable Whether to ignore outputs that we are tracking but don't have the keys to sign for.
     */
//    public List<TransactionOutput> calculateAllSpendCandidates(boolean excludeImmatureCoinbases, boolean excludeUnsignable, boolean isCoinJoinOnly) {
//        lock.lock();
//        try {
//            List<TransactionOutput> candidates;
//            if (getUTXOProvider() == null) {
//                candidates = new ArrayList<>(myUnspents.size());
//                for (TransactionOutput output : myUnspents) {
//                    if (excludeUnsignable && !canSignFor(output.getScriptPubKey())) continue;
//
//                    // exclude non coinjoin outputs if isCoinJoinOnly is true
//                    // exclude coinjoin outputs when isCoinJoinOnly is false
//                    boolean isCoinJoin = output.isCoinJoin(this) && isFullyMixed(output);
//
//                    if (isCoinJoin != isCoinJoinOnly)
//                        continue;
//
//                    Transaction transaction = checkNotNull(output.getParentTransaction());
//                    if (excludeImmatureCoinbases && !transaction.isMature())
//                        continue;
//
//                    candidates.add(output);
//                }
//            } else {
//                candidates = calculateAllSpendCandidatesFromUTXOProvider(excludeImmatureCoinbases);
//            }
//            return candidates;
//        } finally {
//            lock.unlock();
//        }
//    }

    /**
     * Returns the spendable coinjoin candidates from the {@link UTXOProvider} based on keys that the wallet contains.
     * @return The list of candidates.
     */
//    protected LinkedList<TransactionOutput> calculateAllCoinJoinSpendCandidatesFromUTXOProvider(boolean mixed) {
//        checkState(lock.isHeldByCurrentThread());
//        UTXOProvider utxoProvider = checkNotNull(getUTXOProvider(), "No UTXO provider has been set");
//        LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
//        try {
//            int chainHeight = utxoProvider.getChainHeadHeight();
//            for (UTXO output : getStoredOutputsFromUTXOProvider()) {
//                boolean coinbase = output.isCoinbase();
//                // Do not try and spend coinbases, they cannot be coinjoin transactions.
//                if (!coinbase) {
//                    // TODO deterimine if this item is a coinjoin transaction
//                    byte [] p2pkh = ScriptPattern.extractHashFromP2PKH(output.getScript());
//                    if (mixed && coinjoin.findKeyFromPubKey(p2pkh) != null) {
//                        candidates.add(new FreeStandingTransactionOutput(params, output, chainHeight));
//                    } else if (!mixed && CoinJoin.isDenominatedAmount(output.getValue())) {
//                        candidates.add(new FreeStandingTransactionOutput(params, output, chainHeight));
//                    }
//                }
//            }
//        } catch (UTXOProviderException e) {
//            throw new RuntimeException("UTXO provider error", e);
//        }
//        // We need to handle the pending transactions that we know about.
//        for (Transaction tx : pending.values()) {
//            // Remove the spent outputs.
//            for (TransactionInput input : tx.getInputs()) {
//                if (input.getConnectedOutput().isMine(this)) {
//                    candidates.remove(input.getConnectedOutput());
//                }
//            }
//            // Add change outputs. Do not try and spend coinbases that were mined too recently, the protocol forbids it.
//            if (tx.isMature()) {
//                for (TransactionOutput output : tx.getOutputs()) {
//                    if (output.isAvailableForSpending() && output.isMine(this)) {
//                        candidates.add(output);
//                    }
//                }
//            }
//        }
//        return candidates;
//    }

    /**
     * Returns a list of the outputs that can potentially be spent, i.e. that we have the keys for and are unspent
     * according to our knowledge of the block chain.
     */
//    public List<TransactionOutput> calculateAllSpendCandidates() {
//        return calculateAllSpendCandidates(true, true, false);
//    }

    /**
     * Returns a list of all outputs that are being tracked by this wallet either from the {@link UTXOProvider}
     * (in this case the existence or not of private keys is ignored), or the wallets internal storage (the default)
     * taking into account the flags.
     *
     * @param excludeImmatureCoinbases Whether to ignore coinbase outputs that we will be able to spend in future once they mature.
     * @param excludeUnsignable Whether to ignore outputs that we are tracking but don't have the keys to sign for.
     */
//    public List<TransactionOutput> calculateAllSpendCandidates(boolean excludeImmatureCoinbases, boolean excludeUnsignable, boolean isCoinJoin) {
//        lock.lock();
//        try {
//            List<TransactionOutput> candidates;
//            if (vUTXOProvider == null) {
//                candidates = new ArrayList<>(myUnspents.size());
//                for (TransactionOutput output : myUnspents) {
//                    if (excludeUnsignable && !canSignFor(output.getScriptPubKey())) continue;
//                    Transaction transaction = checkNotNull(output.getParentTransaction());
//                    if (excludeImmatureCoinbases && !transaction.isMature())
//                        continue;
//
//                    // exclude non coinjoin outputs if isCoinJoinOnly is true
//                    // exclude coinjoin outputs when isCoinJoinOnly is false
//                    boolean isOutputCoinJoin = output.isCoinJoin(this) && isFullyMixed(output);
//
//                    if (isCoinJoin != isOutputCoinJoin)
//                        continue;
//
//                    candidates.add(output);
//                }
//            } else {
//                candidates = calculateAllSpendCandidatesFromUTXOProvider(excludeImmatureCoinbases, isCoinJoin);
//            }
//            return candidates;
//        } finally {
//            lock.unlock();
//        }
//    }
//
//    @Override
//    protected LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(boolean excludeImmatureCoinbases) {
//        return calculateAllSpendCandidatesFromUTXOProvider(excludeImmatureCoinbases, false);
//    }
    /**
     * Returns the spendable candidates from the {@link UTXOProvider} based on keys that the wallet contains.
     * @return The list of candidates.
     */
//    protected LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(boolean excludeImmatureCoinbases, boolean isCoinJoinOnly) {
//        checkState(lock.isHeldByCurrentThread());
//        UTXOProvider utxoProvider = checkNotNull(vUTXOProvider, "No UTXO provider has been set");
//        LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
//        try {
//            int chainHeight = utxoProvider.getChainHeadHeight();
//            for (UTXO output : getStoredOutputsFromUTXOProvider()) {
//                boolean coinjoin = false;
//                if (ScriptPattern.isP2PKH(output.getScript()))
//                    coinjoin = isCoinJoinPubKeyHashMine(ScriptPattern.extractHashFromP2PKH(output.getScript()), Script.ScriptType.P2PKH);
//                else if (ScriptPattern.isP2PK(output.getScript()))
//                    coinjoin = isCoinJoinPubKeyMine(ScriptPattern.extractKeyFromP2PK(output.getScript()));
//                else if (ScriptPattern.isP2SH(output.getScript()))
//                    coinjoin = isCoinJoinPayToScriptHashMine(ScriptPattern.extractHashFromP2SH(output.getScript()));
//
//                if (coinjoin != isCoinJoinOnly)
//                    continue;
//                boolean coinbase = output.isCoinbase();
//                int depth = chainHeight - output.getHeight() + 1; // the current depth of the output (1 = same as head).
//                // Do not try and spend coinbases that were mined too recently, the protocol forbids it.
//                if (!excludeImmatureCoinbases || !coinbase || depth >= params.getSpendableCoinbaseDepth()) {
//                    candidates.add(new FreeStandingTransactionOutput(params, output, chainHeight));
//                }
//            }
//        } catch (UTXOProviderException e) {
//            throw new RuntimeException("UTXO provider error", e);
//        }
//        // We need to handle the pending transactions that we know about.
//        for (Transaction tx : pending.values()) {
//            // Remove the spent outputs.
//            for (TransactionInput input : tx.getInputs()) {
//                if (input.getConnectedOutput().isMine(this)) {
//                    candidates.remove(input.getConnectedOutput());
//                }
//            }
//            // Add change outputs. Do not try and spend coinbases that were mined too recently, the protocol forbids it.
//            if (!excludeImmatureCoinbases || tx.isMature()) {
//                for (TransactionOutput output : tx.getOutputs()) {
//                    if (output.isAvailableForSpending() && output.isMine(this) && output.isCoinJoin(this) == isCoinJoinOnly) {
//                        candidates.add(output);
//                    }
//                }
//            }
//        }
//        return candidates;
//    }

    public CoinJoinExtension getCoinJoin() {
        return coinjoin;
    }

    List<TransactionOutput> getDenominatedOutputs() {
        ArrayList<TransactionOutput> result = Lists.newArrayList();
        List<TransactionOutput> candidates = calculateAllSpendCandidates(false, true);
        CoinSelection selection = DenominatedCoinSelector.get().select(MAX_MONEY, candidates);
        for (TransactionOutput out : selection.gathered) {
            if (out.isDenominated() && !isFullyMixed(out))
                result.add(out);
        }
        return result;
    }

    List<TransactionOutput> getCoinJoinOutputs() {
        ArrayList<TransactionOutput> result = Lists.newArrayList();
        List<TransactionOutput> candidates = calculateAllSpendCandidates(false, true);
        CoinSelection selection = DenominatedCoinSelector.get().select(MAX_MONEY, candidates);
        for (TransactionOutput out : selection.gathered) {
            if (out.isDenominated() && isFullyMixed(out))
                result.add(out);
        }
        return result;
    }
}