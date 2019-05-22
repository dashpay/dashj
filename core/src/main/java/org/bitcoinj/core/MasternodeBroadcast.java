package org.bitcoinj.core;

import org.bitcoinj.net.Dos;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;


import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import static org.bitcoinj.core.Masternode.CollateralStatus.COLLATERAL_INVALID_AMOUNT;
import static org.bitcoinj.core.Masternode.CollateralStatus.COLLATERAL_SPV_ASSUME_VALID;
import static org.bitcoinj.core.Masternode.CollateralStatus.COLLATERAL_UTXO_NOT_FOUND;
import static org.bitcoinj.core.MasternodeInfo.State.MASTERNODE_EXPIRED;

/**
 * Created by Hash Engineering on 2/20/2015.
 */
@Deprecated
public class MasternodeBroadcast extends Masternode {
    private static final Logger log = LoggerFactory.getLogger(MasternodeBroadcast.class);

    boolean fRecovery = false;

    public MasternodeBroadcast(NetworkParameters params, byte [] payloadBytes)
    {
        super(params, payloadBytes, 0);
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();
        nCollateralMinConfBlockHash = Sha256Hash.ZERO_HASH;
    }

    public MasternodeBroadcast(NetworkParameters params, byte [] payloadBytes, int cursor)
    {
        super(params, payloadBytes, cursor);
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();
        nCollateralMinConfBlockHash = Sha256Hash.ZERO_HASH;
    }

    public MasternodeBroadcast(Masternode masternode)
    {
       super(masternode);
    }


    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (uint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        return cursor - offset;
    }

    @Override
    protected void parse() throws ProtocolException {

        info = new MasternodeInfo();
        info.outpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += info.outpoint.getMessageSize();

        info.address = new MasternodeAddress(params, payload, cursor, 0);
        cursor += info.address.getMessageSize();

        info.pubKeyCollateralAddress = new PublicKey(params, payload, cursor);
        cursor += info.pubKeyCollateralAddress.getMessageSize();

        info.pubKeyMasternode = new PublicKey(params, payload, cursor);
        cursor += info.pubKeyMasternode.getMessageSize();

        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();

        info.sigTime = readInt64();

        info.nProtocolVersion = (int)readUint32();

        lastPing = new MasternodePing(params, payload, cursor);
        cursor += lastPing.getMessageSize();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        info.outpoint.bitcoinSerialize(stream);
        info.address.bitcoinSerialize(stream);
        info.pubKeyCollateralAddress.bitcoinSerialize(stream);
        info.pubKeyMasternode.bitcoinSerialize(stream);

        vchSig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(info.sigTime, stream);
        Utils.uint32ToByteStreamLE(info.nProtocolVersion, stream);

        lastPing.bitcoinSerialize(stream);
    }

    public Sha256Hash getHash()
    {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(8 + info.outpoint.getMessageSize() + info.pubKeyCollateralAddress.calculateMessageSizeInBytes());
            new TransactionInput(params, null, new byte[0], info.outpoint).bitcoinSerialize(bos);
            info.pubKeyCollateralAddress.bitcoinSerialize(bos);
            Utils.int64ToByteStreamLE(info.sigTime, bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice((bos.toByteArray())));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e); // Cannot happen.
        }
    }
    public Sha256Hash getSignatureHash()
    {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(8 + info.outpoint.getMessageSize() + info.pubKeyCollateralAddress.calculateMessageSizeInBytes());
            info.outpoint.bitcoinSerialize(bos);
            info.address.bitcoinSerialize(bos);
            info.pubKeyCollateralAddress.bitcoinSerialize(bos);
            info.pubKeyMasternode.bitcoinSerialize(bos);
            Utils.int64ToByteStreamLE(info.sigTime, bos);
            Utils.uint32ToByteStreamLE(info.nProtocolVersion, bos);
           // return Sha256Hash.wrapReversed(Sha256Hash.hashTwice((bos.toByteArray())));
            return Sha256Hash.twiceOf(bos.toByteArray());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    void relay()
    {
        //for SPV, do not relay
        //CInv inv(MSG_MASTERNODE_ANNOUNCE, GetHash());
        //RelayInv(inv);
    }

    boolean simpleCheck(Dos nDos)
    {
        nDos.set(0);

        // make sure addr is valid
        if(!isValidNetAddr()) {
            log.info("CMasternodeBroadcast::SimpleCheck -- Invalid addr, rejected: masternode={}  addr={}",
                    info.outpoint.toStringShort(), info.address.toString());
            return false;
        }

        // make sure signature isn't in the future (past is OK)
        if (info.sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("CMasternodeBroadcast::SimpleCheck -- Signature rejected, too far into the future: masternode={}", info.outpoint.toStringShort());
            nDos.set(1);
            return false;
        }

        // empty ping or incorrect sigTime/unknown blockhash
        if(lastPing == MasternodePing.EMPTY || !lastPing.simpleCheck(nDos)) {
            // one of us is probably forked or smth, just mark it as expired and check the rest of the rules
            info.activeState = MASTERNODE_EXPIRED;
        }

        if(info.nProtocolVersion < context.masternodePayments.getMinMasternodePaymentsProto()) {
            log.info("CMasternodeBroadcast::SimpleCheck -- ignoring outdated Masternode: masternode={}  nProtocolVersion={}", info.outpoint.toStringShort(), info.nProtocolVersion);
            return false;
        }

        Script pubkeyScript;
        pubkeyScript = ScriptBuilder.createOutputScript(new Address(params, info.pubKeyCollateralAddress.getId()));

        if(pubkeyScript.getProgram().length != 25) {
            log.info("CMasternodeBroadcast::SimpleCheck -- pubKeyCollateralAddress has the wrong size");
            nDos.set(100);
            return false;
        }

        Script pubkeyScript2;
        pubkeyScript2 = ScriptBuilder.createOutputScript(new Address(params, info.pubKeyMasternode.getId()));

        if(pubkeyScript2.getProgram().length != 25) {
            log.info("CMasternodeBroadcast::SimpleCheck -- pubKeyMasternode has the wrong size");
            nDos.set(100);
            return false;
        }

        int mainnetDefaultPort = MainNetParams.get().getPort();
        if(params.getId() == NetworkParameters.ID_MAINNET) {
            if(info.address.getPort() != mainnetDefaultPort) return false;
        } else if(info.address.getPort() == mainnetDefaultPort) return false;

        return true;
    }

    boolean update(Masternode mn, Dos nDos)
    {
        nDos.set(0);

        if(mn.info.sigTime == info.sigTime && !fRecovery) {
            // mapSeenMasternodeBroadcast in CMasternodeMan::CheckMnbAndUpdateMasternodeList should filter legit duplicates
            // but this still can happen if we just started, which is ok, just do nothing here.
            return false;
        }

        // this broadcast is older than the one that we already have - it's bad and should never happen
        // unless someone is doing something fishy
        if(mn.info.sigTime > info.sigTime) {
            log.info("CMasternodeBroadcast::Update -- Bad sigTime {} (existing broadcast is at {}) for Masternode {} {}",
                    info.sigTime, mn.info.sigTime, info.outpoint.toStringShort(), info.address.toString());
            return false;
        }

        mn.check();

        // masternode is banned by PoSe
        if(mn.isPoSeBanned()) {
            log.info("CMasternodeBroadcast::Update -- Banned by PoSe, masternode={}", info.outpoint.toStringShort());
            return false;
        }

        // IsVnAssociatedWithPubkey is validated once in CheckOutpoint, after that they just need to match
        if(!mn.info.pubKeyCollateralAddress.equals(info.pubKeyCollateralAddress)) {
            log.info("CMasternodeBroadcast::Update -- Got mismatched pubKeyCollateralAddress and vin");
            nDos.set(33);
            return false;
        }

        if (!checkSignature(nDos)) {
            log.info("CMasternodeBroadcast::Update -- CheckSignature() failed, masternode={}", info.outpoint.toStringShort());
            return false;
        }

        // if ther was no masternode broadcast recently or if it matches our Masternode privkey...
        if(mn.isBroadcastedWithin(MASTERNODE_MIN_MNB_SECONDS) || (context.fMasterNode && info.pubKeyMasternode == context.activeMasternode.pubKeyMasternode)) {
            // take the newest entry
            log.info("CMasternodeBroadcast::Update -- Got UPDATED Masternode entry: addr={}", info.address.toString());
            if(mn.updateFromNewBroadcast(this)) {
                mn.check();
                relay();
            }
            context.masternodeSync.BumpAssetLastTime("CMasternodeBroadcast::Update");
        }

        return true;
    }

    boolean checkOutpoint(Dos nDos)
    {
        // we are a masternode with the same vin (i.e. already activated) and this mnb is ours (matches our Masternode privkey)
        // so nothing to do here for us
        if(context.fMasterNode && info.outpoint == context.activeMasternode.outpoint &&
                info.pubKeyMasternode == context.activeMasternode.pubKeyMasternode) {
            return false;
        }

        if (!checkSignature(nDos)) {
            log.info("CMasternodeBroadcast::CheckOutpoint -- CheckSignature() failed, masternode={}", info.outpoint.toStringShort());
            return false;
        }

        {
            //TODO:  can this be fixed?
            /*TRY_LOCK(cs_main, lockMain);
            if(!lockMain) {
                // not mnb fault, let it to be checked again later
                log.info("masternode--CMasternodeBroadcast::CheckOutpoint -- Failed to acquire lock, addr={}", info.address.toString());
                context.masternodeManager.mapSeenMasternodeBroadcast.remove(getHash());
                return false;
            }*/

            int nHeight;
            Pair<CollateralStatus, Integer> result = checkCollateral(info.outpoint);
            CollateralStatus err = result.getFirst();
            nHeight = result.getSecond();
            if (err == COLLATERAL_UTXO_NOT_FOUND) {
                log.info("masternode--CMasternodeBroadcast::CheckOutpoint -- Failed to find Masternode UTXO, masternode={}", info.outpoint.toStringShort());
                return false;
            }

            if (err == COLLATERAL_INVALID_AMOUNT) {
                log.info("masternode--CMasternodeBroadcast::CheckOutpoint -- Masternode UTXO should have 1000 DASH, masternode={}", info.outpoint.toStringShort());
                return false;
            }

            if(context.blockChain.getBestChainHeight() - nHeight + 1 < params.getMasternodeMinimumConfirmations()) {
                log.info("CMasternodeBroadcast::CheckOutpoint -- Masternode UTXO must have at least {} confirmations, masternode={}",
                        params.getMasternodeMinimumConfirmations(), info.outpoint.toStringShort());
                // maybe we miss few blocks, let this mnb to be checked again later
                context.masternodeManager.mapSeenMasternodeBroadcast.remove(getHash());
                return false;
            }
            // remember the hash of the block where masternode collateral had minimum required confirmations
            //TODO:  can this be fixed?
            //nCollateralMinConfBlockHash = chainActive[nHeight + params.getMasternodeMinimumConfirmations() - 1]->GetBlockHash();

            if(err == COLLATERAL_SPV_ASSUME_VALID)
                return true;
        }

        log.info("masternode--CMasternodeBroadcast::CheckOutpoint -- Masternode UTXO verified");


        // make sure the input that was signed in masternode broadcast message is related to the transaction
        // that spawned the Masternode - this is expensive, so it's only done once per Masternode
        if(!isInputAssociatedWithPubkey()) {
            log.info("CMasternodeMan::CheckOutpoint -- Got mismatched pubKeyCollateralAddress and vin");
            nDos.set(33);
            return false;
        }

        //TODO: can we do a better job at getting the transaction and the block?
        // verify that sig time is legit in past
        // should be at least not earlier than block when 1000 DASH tx got nMasternodeMinimumConfirmations
        /*uint256 hashBlock = uint256();
        CTransaction tx2;
        GetTransaction(vin.prevout.hash, tx2, Params().GetConsensus(), hashBlock, true);
        {
            LOCK(cs_main);
            BlockMap::iterator mi = mapBlockIndex.find(hashBlock);
            if (mi != mapBlockIndex.end() && (*mi).second) {
                CBlockIndex* pMNIndex = (*mi).second; // block for 1000 DASH tx -> 1 confirmation
                CBlockIndex* pConfIndex = chainActive[pMNIndex->nHeight + params.getMasternodeMinimumConfirmations() - 1]; // block where tx got nMasternodeMinimumConfirmations
                if(pConfIndex->GetBlockTime() > sigTime) {
                    log.info("CMasternodeBroadcast::CheckOutpoint -- Bad sigTime {} ({} conf block is at {}) for Masternode {} {}",
                            info.sigTime, params.getMasternodeMinimumConfirmations(), pConfIndex->GetBlockTime(), info.outpoint.toStringShort(), info.address.toString());
                    return false;
                }
            }
        }
        */
        return true;
    }

    boolean sign(ECKey keyCollateralAddress)
    {
        String strMessage;
        StringBuilder strError = new StringBuilder();

        info.sigTime = Utils.currentTimeSeconds();

        strMessage = info.address.toString() + info.sigTime +
                Utils.HEX.encode(Utils.reverseBytes(info.pubKeyCollateralAddress.getId())) + Utils.HEX.encode(Utils.reverseBytes(info.pubKeyMasternode.getId())) +
                info.nProtocolVersion;

        if(null == (vchSig = MessageSigner.signMessage(strMessage, keyCollateralAddress))) {
            log.info("CMasternodeBroadcast::Sign -- SignMessage() failed");
            return false;
        }

        if(!MessageSigner.verifyMessage(info.pubKeyCollateralAddress, vchSig, strMessage, strError)) {
            log.info("CMasternodeBroadcast::Sign -- VerifyMessage() failed, error: {}", strError);
            return false;
        }

        return true;
    }

    boolean checkSignature(Dos nDos)
    {
        String strMessage;
        StringBuilder strError = new StringBuilder();
        nDos.set(0);

        if(context.sporkManager.isSporkActive(SporkManager.SPORK_6_NEW_SIGS)) {
            Sha256Hash hash = getSignatureHash();
            if(!HashSigner.verifyHash(hash, info.pubKeyCollateralAddress, vchSig, strError)) {
                strMessage = info.address.toString() + info.sigTime +
                        Utils.HEX.encode(Utils.reverseBytes(info.pubKeyCollateralAddress.getId())) + Utils.HEX.encode(Utils.reverseBytes(info.pubKeyMasternode.getId())) +
                        info.nProtocolVersion;

                if (!MessageSigner.verifyMessage(info.pubKeyCollateralAddress, vchSig, strMessage, strError)){
                    // nope, not in old format either
                    log.error("CMasternodeBroadcast::CheckSignature -- Got bad Masternode announce signature, error: {}", strError);
                    nDos.set(100);
                    return false;
                }
            }
        } else {
            strMessage = info.address.toString() + info.sigTime +
                    Utils.HEX.encode(Utils.reverseBytes(info.pubKeyCollateralAddress.getId())) + Utils.HEX.encode(Utils.reverseBytes(info.pubKeyMasternode.getId())) +
                    info.nProtocolVersion;

            log.info("masternode--CMasternodeBroadcast::CheckSignature -- strMessage: {}  pubKeyCollateralAddress address: {}  sig: {}",
                    strMessage, new Address(params, info.pubKeyCollateralAddress.getId()), Base64.toBase64String(vchSig.getBytes()));

            if (!MessageSigner.verifyMessage(info.pubKeyCollateralAddress, vchSig, strMessage, strError)) {
                log.info("CMasternodeBroadcast::CheckSignature -- Got bad Masternode announce signature, error: {}", strError);
                nDos.set(100);
                return false;
            }
        }

        return true;
    }

    String getHexData() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(400);
            bitcoinSerialize(bos);
            return Utils.HEX.encode(bos.toByteArray());
        } catch (IOException x) {
            return "";
        }
    }
}
