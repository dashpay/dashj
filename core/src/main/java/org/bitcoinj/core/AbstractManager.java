package org.bitcoinj.core;


import org.bitcoinj.manager.ManagerFiles;
import org.bitcoinj.store.FlatDB;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by Hash Engineering on 6/21/2016.
 *
 * Base class for all Dash Manager objects.  Derived classes must implement
 * {@link #parse()} and {@link #bitcoinSerializeToStream(OutputStream)}
 * to serialize data to a file with FlatDB.
 */
public abstract class AbstractManager extends Message {

    private static final Logger log = LoggerFactory.getLogger(AbstractManager.class);

    // Saving to files
    public static final int DELAY_TIME = 100;
    private final ReentrantLock fileManagerLock = Threading.lock("abstract-manager-save-lock");
    protected volatile ManagerFiles vFileManager;

    /**
     * The Context.
     */
    protected Context context;
    /**
     * The previous Default file name.
     * The default value is the name of the derived class + ".dat"
     */
    protected String previousDefaultFileName;
    /**
     * The Default file name.
     * The default value is the name of the derived class + "-" network + ".dat"
     */
    protected String defaultFileName;
    /**
     * The Default magic message.
     * The default value is the name of the derived class.
     */
    protected String defaultMagicMessage;
    /**
     * The Magic message.
     * The default value is the name of the derived class.
     */
    protected String magicMessage;
    /**
     * The Format version.  The default value is 1.
     */
    protected int formatVersion;
    /**
     * The constant defaultExtension.
     */
    public static String defaultExtension = ".dat";

    /**
     * The Filename.
     */
    public String filename;

    /**
     * Instantiates a new AbstractManager.
     *
     * @param context the context
     */
    public AbstractManager(Context context) {
        super(context.getParams());
        this.context = context;
        this.formatVersion = 1;
        String fullClassName = this.getClass().getCanonicalName();
        this.defaultMagicMessage = fullClassName.substring(fullClassName.lastIndexOf('.')+1);
        this.magicMessage = defaultMagicMessage;
        this.previousDefaultFileName = this.magicMessage.toLowerCase() + defaultExtension;
        this.defaultFileName = this.magicMessage.toLowerCase() + "-" + params.getNetworkName() + defaultExtension;
    }

    /**
     * Instantiates a new Abstract manager.
     *
     * @param params  the params
     * @param payload the payload
     * @param cursor  the cursor
     */
    public AbstractManager(NetworkParameters params, byte [] payload, int cursor) {
        super(params, payload, cursor);
        this.context = Context.get();
        this.formatVersion = 1;
        String fullClassName = this.getClass().getCanonicalName();
        this.defaultMagicMessage = fullClassName.substring(fullClassName.lastIndexOf('.')+1);
        this.magicMessage = defaultMagicMessage;
        this.defaultFileName = fullClassName + defaultExtension;
    }

    /**
     * Calculates size in bytes of this object.
     *
     * @return the size
     */
    public abstract int calculateMessageSizeInBytes();

    /**
     * Check and remove.  Called to check the validity of all objects
     * and remove the expired objects.
     */
    public abstract void checkAndRemove();

    /**
     * Clear.  Removes all objects and settings.
     */
    public abstract void clear();

    /**
     * Loads a payload into the object
     *
     * @param payload the payload
     * @param offset  the offset
     */
    public void load(byte [] payload, int offset)
    {
        load(payload, offset, getFormatVersion());
    }

    public void load(byte [] payload, int offset, int version) {
        this.protocolVersion = params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT);
        this.payload = payload;
        this.cursor = this.offset = offset;
        this.length = payload.length;
        this.formatVersion = version;

        if (this.length == UNKNOWN_LENGTH)
            checkState(false, "Length field has not been set in constructor for %s after %s parse. " +
                            "Refer to Message.parseLite() for detail of required Length field contract.",
                    getClass().getSimpleName(), /*parseLazy ? "lite" :*/ "full");
        parse();
    }

    /**
     * Create empty abstract manager.
     *
     * @return the abstract manager
     */
    public abstract AbstractManager createEmpty();

    /**
     * Gets default file name.
     *
     * @return the default file name
     */
    public String getPreviousDefaultFileName() {
        return previousDefaultFileName;
    }

    /**
     * Gets default file name.
     *
     * @return the default file name
     */
    public String getDefaultFileName() {
        return defaultFileName;
    }

    /**
     * Gets magic message with the version number.
     *
     * @return the magic message
     */
    public String getMagicMessage() {
        return magicMessage + "-" + formatVersion;
    }

    /**
     * Gets default magic message.
     *
     * @return the default magic message
     */
    public String getDefaultMagicMessage() { return defaultMagicMessage; }

    /**
     * Gets format version.
     *
     * @return the format version
     */
    public int getFormatVersion() {
        return formatVersion;
    }

    public int getCurrentFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = Math.max(formatVersion, this.formatVersion);
    }

    /**
     * <p>Sets up manager to auto-save itself to the given file, using temp files with atomic renames to ensure
     * consistency. After connecting to a file, you no longer need to save the manager manually, it will do it
     * whenever necessary. Manager {@link #bitcoinSerializeToStream(OutputStream)} serialization will be used.</p>
     *
     * <p>If delayTime is set, a background thread will be created and the manager file will only be saved to
     * disk every so many time units. If no changes have occurred for the given time period, nothing will be written.
     * In this way disk IO can be rate limited. It's a good idea to set this as otherwise the manager can change very
     * frequently, eg if there are a lot of transactions in it or during block sync, and there will be a lot of redundant
     * writes. <b>You should still save the manager manually using {@link #saveToFile(File)} when your program
     * is about to shut down as the JVM will not wait for the background thread.</b></p>
     *
     * <p>An event listener can be provided. If a delay greater than 0 was specified, it will be called on a background thread
     * with the manager locked when an auto-save occurs. If delay is zero or you do something that always triggers
     * an immediate save, like adding a key, the event listener will be invoked on the calling threads.</p>
     *
     * @param f The destination file to save to.
     * @param delayTime How many time units to wait until saving the manager on a background thread.
     * @param timeUnit the unit of measurement for delayTime.
     * @param eventListener callback to be informed when the auto-save thread does things, or null
     */
    public ManagerFiles autosaveToFile(File f, long delayTime, TimeUnit timeUnit,
                                      @Nullable ManagerFiles.Listener eventListener) {
        fileManagerLock.lock();
        try {
            checkState(vFileManager == null, "Already auto saving this manager.");
            filename = f.getAbsolutePath();
            ManagerFiles manager = new ManagerFiles(this, f, delayTime, timeUnit);
            if (eventListener != null)
                manager.setListener(eventListener);
            vFileManager = manager;
            return manager;
        } finally {
            fileManagerLock.unlock();
        }
    }

    /**
     * <p>
     * Disables auto-saving, after it had been enabled with
     * {@link AbstractManager#autosaveToFile(File, long, TimeUnit, ManagerFiles.Listener)}
     * before. This method blocks until finished.
     * </p>
     */
    public void shutdownAutosaveAndWait() {
        fileManagerLock.lock();
        try {
            ManagerFiles files = vFileManager;
            vFileManager = null;
            checkState(files != null, "Auto saving manager not enabled.");
            files.shutdownAndWait();
        } finally {
            fileManagerLock.unlock();
        }
    }

    /**
     * Save
     */
    protected void saveNow() {
        ManagerFiles files = vFileManager;
        if (files != null) {
            try {
                files.saveNow();  // This calls back into saveToFile().
            } catch (IOException e) {
                // Can't really do much at this point, just let the API user know.
                log.error("Failed to save manager to disk!", e);
                Thread.UncaughtExceptionHandler handler = Threading.uncaughtExceptionHandler;
                if (handler != null)
                    handler.uncaughtException(Thread.currentThread(), e);
            }
        }
    }

    /** Requests an asynchronous save on a background thread */
    protected void saveLater() {
        ManagerFiles files = vFileManager;
        if (files != null) {
            Context.propagate(context);
            files.saveLater();
        }
    }

    /**
     * Uses manager serialization to save the manager to the given file. To learn more about this file format, see
     * {@link #bitcoinSerializeToStream(OutputStream)}. Writes out first to a temporary file in the same directory and then renames
     * once written.
     */
    public void saveToFile(File f) throws IOException {
        File directory = f.getAbsoluteFile().getParentFile();
        File temp = File.createTempFile("manager", null, directory);
        saveToFile(temp, f);
    }

    /**
     * Sets filename to which the object data will be saved.
     *
     * @param filename the filename
     */
    public void setFilename(String filename) {
        this.filename = filename;
        autosaveToFile(new File(filename), DELAY_TIME, TimeUnit.MILLISECONDS, null);
    }

    public void close() {
        if (vFileManager != null) {
            shutdownAutosaveAndWait();
        }
    }

    public void resume() {
        if (filename != null && vFileManager == null) {
            autosaveToFile(new File(filename), DELAY_TIME, TimeUnit.MILLISECONDS, null);
        }
    }

    public void onFirstSaveComplete() {

    }

    public void saveToFile(File temp, File destFile) throws IOException {
        fileManagerLock.lock();
        try {
            FlatDB<AbstractManager> flatDB = new FlatDB<>(context, temp.getAbsolutePath(), true, magicMessage, getFormatVersion());
            flatDB.dump(AbstractManager.this);

            if (Utils.isWindows()) {
                // Work around an issue on Windows whereby you can't rename over existing files.
                File canonical = destFile.getCanonicalFile();
                if (canonical.exists() && !canonical.delete())
                    throw new IOException("Failed to delete canonical manager file for replacement with autosave");
                if (temp.renameTo(canonical))
                    return;  // else fall through.
                throw new IOException("Failed to rename " + temp + " to " + canonical);
            } else if (!temp.renameTo(destFile)) {
                throw new IOException("Failed to rename " + temp + " to " + destFile);
            }
        } catch (RuntimeException e) {
            log.error("Failed whilst saving manager file", e);
            throw e;
        } finally {
            fileManagerLock.unlock();
            if (temp.exists()) {
                log.warn("Temp file still exists after failed save.");
            }
        }
    }
}
