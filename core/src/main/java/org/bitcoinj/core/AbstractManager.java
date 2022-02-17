package org.bitcoinj.core;


import com.google.common.base.Stopwatch;
import org.bitcoinj.store.FlatDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

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
        this.formatVersion = formatVersion;
    }

    /**
     * Save.
     *
     * @throws NullPointerException the null pointer exception
     */
    public void save() throws FileNotFoundException {
        if(filename != null) {
            //save in a separate thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Stopwatch watch = Stopwatch.createStarted();
                        FlatDB<AbstractManager> flatDB = new FlatDB<>(context, filename, true, magicMessage, getFormatVersion());
                        flatDB.dump(AbstractManager.this);
                        log.info("{} Save time: {}", filename, watch.elapsed(TimeUnit.MICROSECONDS));
                    } catch (Exception x) {
                        log.warn("Saving failed for {}", filename, x);
                    }
                }
            }).start();
        } else throw new FileNotFoundException("filename is not set");
    }

    /**
     * Sets filename to which the object data will be saved.
     *
     * @param filename the filename
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public abstract void close();

    public void onFirstSaveComplete() {

    }
}
