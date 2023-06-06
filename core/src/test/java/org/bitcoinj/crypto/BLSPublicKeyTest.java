package org.bitcoinj.crypto;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

public class BLSPublicKeyTest {
    String pkHex = "81367a439778c3b23cd455cd3a91fe2cbefcb7fa70d41ef8687bd48fac6a96b033433119d522b7793c5295b4b647643a";
    byte [] pkBuffer = Utils.HEX.decode(pkHex);

    NetworkParameters TESTNET = TestNet3Params.get();

    @Test
    public void loadFromStringTest() {
        BLSPublicKey publicKeyBasic = new BLSPublicKey(pkHex, true);
        BLSPublicKey publicKeyLegacy = new BLSPublicKey(pkHex, false);
        System.out.println(publicKeyBasic);
        System.out.println(publicKeyLegacy);
    }

    @Test
    public void loadFromBufferTest() {

        BLSPublicKey publicKeyBasic = new BLSPublicKey(TESTNET, pkBuffer, 0, false);
        BLSPublicKey publicKeyLegacy = new BLSPublicKey(TESTNET, pkBuffer, 0, true);
        System.out.println(publicKeyBasic);
        System.out.println(publicKeyLegacy);
    }
}
