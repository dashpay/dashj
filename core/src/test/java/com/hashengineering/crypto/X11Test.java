package com.hashengineering.crypto;

import org.bouncycastle.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class X11Test {
    @Test
    public void x11Test() {
        byte [] message = "Hello World!".getBytes();

        byte [] digestOne = X11.x11Digest(message);
        byte [] digestTwo = X11.x11Digest(message, 0, message.length);

        assertArrayEquals(digestTwo, digestOne);
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
