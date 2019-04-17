package org.bitcoinj.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Hash Engineering on 2/8/2017.
 */
public class MasternodeInfo {

    enum State {
        MASTERNODE_PRE_ENABLED(0),
        MASTERNODE_ENABLED(1),
        MASTERNODE_EXPIRED(2),
        MASTERNODE_OUTPOINT_SPENT(3),
        MASTERNODE_UPDATE_REQUIRED(4),
        MASTERNODE_SENTINEL_EXPIRED(5),
        MASTERNODE_NEW_START_REQUIRED(6),
        MASTERNODE_POSE_BAN(7);

        State(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        private static final Map<Integer, State> typesByValue = new HashMap<Integer, State>();

        static {
            for (State type : State.values()) {
                typesByValue.put(type.value, type);
            }
        }

        private final int value;

        public static State forValue(int value) {
            return typesByValue.get(value);
        }
    }

    public State activeState = State.MASTERNODE_PRE_ENABLED;
    public int nProtocolVersion = 0;
    public long sigTime = 0; //mnb message time

    public TransactionOutPoint outpoint;
    public MasternodeAddress address;
    public PublicKey pubKeyCollateralAddress;
    public PublicKey pubKeyMasternode;

    public long nLastDsq; //the dsq count from the last dsq broadcast of this node
    public long nTimeLastChecked;
    public long nTimeLastPaid;
    public long nTimeLastPing; //* not in CMN
    public boolean fInfoValid; //* not

    MasternodeInfo()
    {
    }

    MasternodeInfo(MasternodeInfo other)
    {
        this.activeState = other.activeState;
        this.nProtocolVersion = other.nProtocolVersion;
        this.sigTime = other.sigTime;
        this.outpoint = new TransactionOutPoint(Context.get().getParams(), other.outpoint.getIndex(), other.outpoint.getHash());
        this.address = other.address;
        this.pubKeyCollateralAddress = other.pubKeyCollateralAddress;
        this.pubKeyMasternode = other.pubKeyMasternode;
    }

    MasternodeInfo(State activeState, int protoVer, long sTime)
    {
        this.activeState = activeState;
        this.nProtocolVersion = protoVer;
        this.sigTime = sTime;
    }

    MasternodeInfo(NetworkParameters params, State activeState, int protoVer, long sTime,
                      TransactionOutPoint outpoint, MasternodeAddress address,
                      PublicKey pkCollAddr, PublicKey pkMN)
    {
        this.activeState = activeState;
        this.nProtocolVersion = protoVer;
        this.sigTime = sTime;
        this.outpoint = new TransactionOutPoint(outpoint.getParams(), outpoint.getIndex(), outpoint.getHash());;
        this.address = address;
        this.pubKeyCollateralAddress = pkCollAddr;
        this.pubKeyMasternode = pkMN;
    }
}
