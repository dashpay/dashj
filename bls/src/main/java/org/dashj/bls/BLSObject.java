package org.dashj.bls;

public abstract class BLSObject {
    protected transient long cPointer;
    protected transient boolean owner;

    protected BLSObject(long cPointer, boolean owner) {
        this.cPointer = cPointer;
        this.owner = owner;
    }

    protected static long getCPtr(BLSObject obj) {
        return (obj == null) ? 0 : obj.cPointer;
    }
    protected long getCPtr() { return cPointer; }
    protected boolean isOwner() { return owner; }

    protected void finalize() {
        handleDelete();
    }

    public synchronized void handleDelete() {
        if (cPointer != 0) {
            if (owner) {
                owner = false;
                delete();
            }
            cPointer = 0;
        }
    }

    protected abstract void delete();
}
