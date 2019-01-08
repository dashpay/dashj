package org.dashj.bls;

import static org.junit.Assert.fail;

/**
 * Created by hashengineering on 11/13/18.
 */
public class BaseTest {
    static {
        try {
            System.loadLibrary(JNI.LIBRARY_NAME);
        } catch (UnsatisfiedLinkError x) {
            fail(x.getMessage());
        }
    }
}
