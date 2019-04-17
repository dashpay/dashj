package org.bitcoinj.governance;

/**
 * Created by Hash Engineering on 5/23/2018.
 */
public class ExpirationInfo {
    public ExpirationInfo(int idFrom, long expirationTime) {
        this.expirationTime = expirationTime;
        this.idFrom = idFrom;
    }

    public long expirationTime;
    int idFrom;
}
