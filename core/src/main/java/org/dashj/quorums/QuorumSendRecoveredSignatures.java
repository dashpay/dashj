package org.dashj.quorums;

import org.dashj.core.EmptyMessage;
import org.dashj.core.NetworkParameters;

/**
 * Created by Hash Engineering on 4/12/2019.
 */
public class QuorumSendRecoveredSignatures extends EmptyMessage {

    public QuorumSendRecoveredSignatures(NetworkParameters params) {
        super(params);
        length = 0;
    }
}
