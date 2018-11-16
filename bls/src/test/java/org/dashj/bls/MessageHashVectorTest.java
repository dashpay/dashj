package org.dashj.bls;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * Created by hashengineering on 11/13/18.
 */
public class MessageHashVectorTest extends BaseTest {

    @Test
    public void mainTest() {
        MessageHashVector bav = new MessageHashVector();

        bav.reserve(3);

        byte [] ba1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32};
        byte [] ba2 = {10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40};
        byte [] ba3 = {11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21, 31, 11, 21};

        bav.push_back(ba1);
        bav.push_back(ba2);
        bav.push_back(ba3);

        //assertArrayEquals(ba1, bav.get(0));

        //byte [] ba1s = bav.set(0, ba1);
        byte [] ba1r = bav.get(0);
        byte [] ba2r = bav.get(1);
        byte [] ba3r = bav.get(2);
        assertArrayEquals(ba2, ba2r);
        byte [] ba1s2 = bav.set(0, ba1);
        assertArrayEquals(ba1, ba1s2);
        assertArrayEquals(ba1, bav.get(0));
        //byte [] ba4 = {4, 5, 6, 7};
        //bav.push_back(ba4);

        bav.clear();

        bav.push_back(ba1);
        bav.push_back(ba2);
        bav.push_back(ba3);

        bav.delete();
    }
}
