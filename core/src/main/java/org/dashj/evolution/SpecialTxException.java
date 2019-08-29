package org.bitcoinj.evolution;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.VerificationException;

public class SpecialTxException {

    public class UserExists extends VerificationException {
        public UserExists(String message) { super(message); }
    }

    public static class UserDoesNotExist extends VerificationException {
        public Sha256Hash regTxId;
        public UserDoesNotExist(String message) { super(message); }
        public UserDoesNotExist(Sha256Hash regTxId) {
            this("user ["+ regTxId +"] does not exist");
            this.regTxId = regTxId;
        }
    }

    public static class TopupExists extends VerificationException {
        public Sha256Hash txResetHash;
        public TopupExists(String message) { super(message); }
        public TopupExists(Sha256Hash txResetHash) {
            this("Topup already exists: " + txResetHash);
            this.txResetHash = txResetHash;
        }
    }

    public static class ResetExists extends VerificationException {
        public Sha256Hash txResetHash;
        public ResetExists(String message) { super(message); }
        public ResetExists(Sha256Hash txResetHash) {
            this("Topup already exists: " + txResetHash);
            this.txResetHash = txResetHash;
        }
    }
}
