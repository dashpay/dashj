package org.bitcoinj.net;

/**
 * Created by Hash Engineering on 3/8/2018.
 */
public class Dos {
    int value;
    public Dos() {
        this.value = 0;
    }

    public void set(int value) { this.value = value; }
    public int get() { return value; }
}
