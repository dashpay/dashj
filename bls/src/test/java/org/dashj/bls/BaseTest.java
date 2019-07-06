package org.dashj.bls;

/**
 * Created by hashengineering on 11/13/18.
 */
public class BaseTest {
    static {
        BLS.Init();/*
        try {
            System.loadLibrary(JNI.LIBRARY_NAME);
        } catch (UnsatisfiedLinkError x) {
            fail(x.getMessage());
        }*/
    }
}
