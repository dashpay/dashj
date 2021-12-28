package com.hashengineering.crypto;

import org.bitcoinj.core.Utils;
import org.bouncycastle.util.Arrays;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class X11Test {
    @Test
    public void x11Test() {
        byte [] message = "Hello World!".getBytes();

        byte [] digestOne = X11.x11Digest(message);
        byte [] digestTwo = X11.x11Digest(message, 0, message.length);
        byte [] digestThree = X11.x11(message, 0, message.length);

        assertArrayEquals(digestTwo, digestOne);
        assertArrayEquals(digestTwo, digestThree);
    }

    @Test
    public void blockHashTest() {
        byte [] blockData = Utils.HEX.decode("020000002cc0081be5039a54b686d24d5d8747ee9770d9973ec1ace02e5c0500000000008d7139724b11c52995db4370284c998b9114154b120ad3486f1a360a1d4253d310d40e55b8f70a1be8e32300");
        // block hash in little endian
        byte [] blockHashLE = Utils.HEX.decode("f29c0f286fd8071669286c6987eb941181134ff5f3978bf89f34070000000000");
        // block hash in big endian (human readable, reversed)
        byte [] blockHashBE = Utils.HEX.decode("000000000007349ff88b97f3f54f13811194eb87696c28691607d86f280f9cf2");

        assertArrayEquals(blockHashLE, X11.x11Digest(blockData));
        assertArrayEquals(blockHashBE, Arrays.reverse(X11.x11Digest(blockData)));

        assertArrayEquals(blockHashBE, Arrays.reverse(X11.x11Digest(blockData, 0, blockData.length)));
        assertArrayEquals(blockHashBE, Arrays.reverse(X11.x11(blockData, 0, blockData.length)));
    }

    @Test
    public void x11ThreadTest() {
        final byte [] message = "Hello World!".getBytes();
        byte [] expectedHash = X11.x11Digest(message);

        class X11Thread extends Thread {
            public Boolean result = true;
            X11Thread() {
                super();
            }

            public void run() {
                for (int i = 0; i < 10; ++i) {
                    byte[] digestOne = X11.x11Digest(message);
                    byte[] digestTwo = X11.x11Digest(message, 0, message.length);

                    result = Arrays.compareUnsigned(expectedHash, digestOne) == 0;
                    result &= Arrays.compareUnsigned(expectedHash, digestTwo) == 0;
                }
            }
        }



        X11Thread [] threads = new X11Thread [10];

        for (int i = 0; i < 10; ++i) {
            threads[i] = new X11Thread();
        }

        for (int i = 0; i < 10; ++i) {
            threads[i].start();
        }

        for (int i = 0; i < 10; ++i) {
            try {
                threads[i].join();
                assertTrue(threads[i].result);
            } catch (InterruptedException x) {
                // do nothing
            }
        }
    }
}
