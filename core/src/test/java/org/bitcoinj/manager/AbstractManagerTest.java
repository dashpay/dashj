package org.bitcoinj.manager;

import org.bitcoinj.core.AbstractManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AbstractManagerTest {

    Context context = new Context(MainNetParams.get());
    static class TestAbstractManager extends AbstractManager {

        public Sha256Hash hash;

        TestAbstractManager(Context context) {
            super(context);
            hash = context.getParams().getGenesisBlock().getHash();
        }

        @Override
        public int calculateMessageSizeInBytes() {
            return 0;
        }

        @Override
        public void checkAndRemove() {

        }

        @Override
        public void clear() {

        }

        @Override
        public AbstractManager createEmpty() {
            return null;
        }

        @Override
        protected void parse() throws ProtocolException {
            hash = readHash();
        }

        @Override
        protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
            stream.write(hash.getReversedBytes());
        }

        public void setHash(Sha256Hash hash) {
            this.hash = hash;
            saveNow();
        }

        public void setHashDelayed(Sha256Hash hash) {
            this.hash = hash;
            saveLater();
        }

        public void setHashNoSave(Sha256Hash hash) {
            this.hash = hash;
        }
    }

    TestAbstractManager abstractManager = new TestAbstractManager(context);

    @Test
    public void autosaveImmediate() throws Exception {
        // Test that the manager will save itself automatically when it changes.
        File f = File.createTempFile("dashj-unit-test", null);
        Sha256Hash hash1 = Sha256Hash.of(f);
        // Start with zero delay and ensure the manager file changes after adding a key.
        abstractManager.autosaveToFile(f, 0, TimeUnit.SECONDS, null);

        abstractManager.setHash(Sha256Hash.ZERO_HASH);
        Sha256Hash hash2 = Sha256Hash.of(f);
        assertNotEquals("Manager not saved after generating fresh key", hash1, hash2);  // File has changed.

        abstractManager.setHash(context.getParams().getGenesisBlock().getMerkleRoot());
        Sha256Hash hash3 = Sha256Hash.of(f);
        assertNotEquals("Manager not saved after receivePending", hash2, hash3);  // File has changed again.
    }

    @Test
    public void autosaveDelayed() throws Exception {
        // Test that the manager will save itself automatically when it changes, but not immediately and near-by
        // updates are coalesced together. This test is a bit racy, it assumes we can complete the unit test within
        // an auto-save cycle of 1 second.
        final File[] results = new File[2];
        final CountDownLatch latch = new CountDownLatch(3);
        File f = File.createTempFile("dashj-unit-test", null);
        Sha256Hash hash1 = Sha256Hash.of(f);
        abstractManager.autosaveToFile(f, 1, TimeUnit.SECONDS,
                new ManagerFiles.Listener() {
                    @Override
                    public void onBeforeAutoSave(File tempFile) {
                        results[0] = tempFile;
                    }

                    @Override
                    public void onAfterAutoSave(File newlySavedFile) {
                        results[1] = newlySavedFile;
                        latch.countDown();
                    }
                }
        );
        abstractManager.setHash(Sha256Hash.ZERO_HASH);
        Sha256Hash hash2 = Sha256Hash.of(f);
        assertNotEquals(hash1, hash2);  // File has changed immediately despite the delay, as keys are important.
        assertNotNull(results[0]);
        assertEquals(f, results[1]);
        results[0] = results[1] = null;

        abstractManager.setHashDelayed(context.getParams().getGenesisBlock().getMerkleRoot());
        Sha256Hash hash3 = Sha256Hash.of(f);
        assertEquals(hash2, hash3);  // File has NOT changed yet.
        assertNull(results[0]);
        assertNull(results[1]);

        abstractManager.setHash(context.getParams().getGenesisBlock().getHash());
        Sha256Hash hash4 = Sha256Hash.of(f);
        assertNotEquals(hash3, hash4);  // File HAS changed.
        results[0] = results[1] = null;

        // A block that contains some random tx we don't care about.
        abstractManager.setHashNoSave(context.getParams().getGenesisBlock().getPrevBlockHash());
        assertEquals(hash4, Sha256Hash.of(f));  // File has NOT changed.
        assertNull(results[0]);
        assertNull(results[1]);

        // Wait for an auto-save to occur.
        latch.await();
        Sha256Hash hash5 = Sha256Hash.of(f);
        assertNotEquals(hash4, hash5);  // File has now changed.
        assertNotNull(results[0]);
        assertEquals(f, results[1]);

        // Now we shutdown auto-saving and expect manager changes to remain unsaved, even "important" changes.
        abstractManager.shutdownAutosaveAndWait();
        results[0] = results[1] = null;
        abstractManager.setHash(context.getParams().getGenesisBlock().getHash());
        assertEquals(hash5, Sha256Hash.of(f)); // File has NOT changed.
        Thread.sleep(2000); // Wait longer than autosave delay. TODO Fix the racyness.
        assertEquals(hash5, Sha256Hash.of(f)); // File has still NOT changed.
        assertNull(results[0]);
        assertNull(results[1]);
    }
}
