package org.darkcoinj;

import org.bitcoinj.core.DarkSendQueue;
import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by Hash Engineering Solutions on 2/8/2015.
 */
@Deprecated
public class DarkSend {
    private static final Logger log = LoggerFactory.getLogger(DarkSend.class);
    public static final int POOL_MAX_TRANSACTIONS                =  3; // wait for X transactions to merge and publish
    public static final int POOL_STATUS_UNKNOWN                  =  0; // waiting for update
    public static final int POOL_STATUS_IDLE                     =  1; // waiting for update
    public static final int POOL_STATUS_QUEUE                    =  2; // waiting in a queue
    public static final int POOL_STATUS_ACCEPTING_ENTRIES        =  3; // accepting entries
    public static final int POOL_STATUS_FINALIZE_TRANSACTION     =  4; // master node will broadcast what it accepted
    public static final int POOL_STATUS_SIGNING                  =  5; // check inputs/outputs, sign final tx
    public static final int POOL_STATUS_TRANSMISSION             =  6; // transmit transaction
    public static final int POOL_STATUS_ERROR                    =  7; // error
    public static final int POOL_STATUS_SUCCESS                  =  8;// success

// status update message constants
    public static final int MASTERNODE_ACCEPTED                  =  1;
    public static final int MASTERNODE_REJECTED                  =  0;
    public static final int MASTERNODE_RESET                     =  -1;

    public static final int DARKSEND_QUEUE_TIMEOUT               =  120;
    public static final int DARKSEND_SIGNING_TIMEOUT             =  30;

    //public static DarkSendPool darkSendPool = new DarkSendPool();
    DarkSendSigner darkSendSigner;
    ArrayList<DarkSendQueue> vecDarksendQueue;
    String strMasterNodePrivKey;
    Map<Sha256Hash, DarkSendBroadcastTransaction> mapDarksendBroadcastTxes;
    /*
    ActiveMasternode activeMasternode;

    //specific messages for the Darksend protocol
    void ProcessMessageDarksend(Peer from, String strCommand, CDataStream& vRecv)
    {

    }

    // get the darksend chain depth for a given input
    int GetInputDarksendRounds(TransactionInput in, int rounds=0);

    void ConnectToDarkSendMasterNodeWinner();

    void ThreadCheckDarkSendPool();  */
}
