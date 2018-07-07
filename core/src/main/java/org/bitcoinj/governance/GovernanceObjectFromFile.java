package org.bitcoinj.governance;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;

/**
 * Created by Eric on 6/21/2018.
 */
public class GovernanceObjectFromFile extends GovernanceObject {

    public GovernanceObjectFromFile(NetworkParameters params, byte[] payload, int cursor) {
        super(params, payload, cursor);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        parseFromDisk();
        length = cursor - offset;
    }
}