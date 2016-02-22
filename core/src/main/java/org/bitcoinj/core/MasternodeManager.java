package org.bitcoinj.core;

import org.bitcoinj.utils.Threading;
import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Hash Engineering on 2/20/2016.
 */
public class MasternodeManager extends Message {
    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);
    // critical section to protect the inner data structures
    //mutable CCriticalSection cs;
    ReentrantLock lock = Threading.lock("MasternodeManager");

    // critical section to protect the inner data structures specifically on messaging
    //mutable CCriticalSection cs_process_message;
    ReentrantLock lock_messages = Threading.lock("MasternodeManager-Messages");

    // map to hold all MNs
    ArrayList<Masternode> vMasternodes = new ArrayList<Masternode>();
    // who's asked for the Masternode list and the last time
    HashMap<NetAddress, Long> mAskedUsForMasternodeList = new HashMap<NetAddress, Long>();
    // who we asked for the Masternode list and the last time
        HashMap<MasternodeAddress, Long> mWeAskedForMasternodeList = new HashMap<MasternodeAddress, Long>();
    // which Masternodes we've asked for
    HashMap<TransactionOutPoint, Long> mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, Long>();

    // Keep track of all broadcasts I've seen
    public HashMap<Sha256Hash, MasternodeBroadcast> mapSeenMasternodeBroadcast;
    // Keep track of all pings I've seen
    public HashMap<Sha256Hash, MasternodePing> mapSeenMasternodePing;

    // keep track of dsq count to prevent masternodes from gaming darksend queue
    long nDsqCount;

    public MasternodeManager(NetworkParameters params)
    {
        super(params);
    }

    public MasternodeManager(NetworkParameters params, byte [] payload)
    {
        super(params, payload, 0);
    }

    public MasternodeManager(Masternode other)
    {

    }


    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
    }
    protected static int calcLength(byte[] buf, int offset) {
        int cursor = 0;

        return cursor - offset;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        int size = (int)readVarInt();

        vMasternodes = new ArrayList<Masternode>(size);
        for (int i = 0; i < size; ++i)
        {
            Masternode mn = new Masternode(params, payload, cursor);
            vMasternodes.add(mn);
            cursor =+ mn.getMessageSize();
        }

        size = (int)readVarInt();
        mAskedUsForMasternodeList = new HashMap<NetAddress, Long>(size);
        for(int i = 0; i < size; ++i)
        {
            NetAddress ma = new NetAddress(params, payload, cursor, 0);
            cursor += ma.getMessageSize();
            long x = readInt64();
            mAskedUsForMasternodeList.put(ma, x);
        }
        //TODO: add the rest

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(new VarInt(vMasternodes.size()).encode());
        for(Masternode mn : vMasternodes)
        {
            mn.bitcoinSerialize(stream);
        }
        stream.write(new VarInt(mAskedUsForMasternodeList.size()).encode());
        for(NetAddress na: mAskedUsForMasternodeList.keySet())
        {
            na.bitcoinSerialize(stream);
            Utils.int64ToByteStreamLE(mAskedUsForMasternodeList.get(na), stream);
        }
        //TODO: add the rest
    }
/*
    void processMasternodeBroadcast(MasternodeBroadcast mnb)
    {
        if(mapSeenMasternodeBroadcast.containsKey(mnb.getHash())) { //seen
            masternodeSync.AddedMasternodeList(mnb.getHash());
            return;
        }
        mapSeenMasternodeBroadcast.put(mnb.getHash(), mnb);

        int nDoS = 0;
        if(!mnb.checkAndUpdate(nDoS)){

            if(nDoS > 0)
                Misbehaving(pfrom->GetId(), nDoS);

            //failed
            return;
        }

        // make sure the vout that was signed is related to the transaction that spawned the Masternode
        //  - this is expensive, so it's only done once per Masternode
        if(!DarkSendSigner.isVinAssociatedWithPubkey(params, mnb.vin, mnb.pubkey)) {
            log.info("mnb - Got mismatched pubkey and vin");
            //Misbehaving(pfrom->GetId(), 33);
            return;
        }

        // make sure it's still unspent
        //  - this is checked later by .check() in many places and by ThreadCheckDarkSendPool()
        if(mnb.CheckInputsAndAdd(nDoS)) {
            // use this as a peer
            addrman.Add(CAddress(mnb.addr), pfrom->addr, 2*60*60);
            masternodeSync.AddedMasternodeList(mnb.GetHash());
        } else {
            LogPrintf("mnb - Rejected Masternode entry %s\n", mnb.addr.ToString());

            if (nDoS > 0)
                Misbehaving(pfrom->GetId(), nDoS);
        }
    }
*/

}
