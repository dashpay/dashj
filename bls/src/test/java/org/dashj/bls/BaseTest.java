package org.dashj.bls;

import static org.junit.Assert.fail;

/**
 * Created by hashengineering on 11/13/18.
 */
public class BaseTest {
    static {
        try {
            System.loadLibrary("dashjbls");
        } catch (UnsatisfiedLinkError x) {
            fail(x.getMessage());
        }
    }
}
