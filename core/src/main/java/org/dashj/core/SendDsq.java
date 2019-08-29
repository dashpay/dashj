package org.dashj.core;

/**
 * Created by Hash Engineering on 4/12/2019.
 */
public class SendDsq extends EmptyMessage {

    public SendDsq(NetworkParameters params) {
        super(params);
        length = 0;
    }
}
