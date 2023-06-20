package org.bitcoinj.store;

import com.google.common.base.Stopwatch;
import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author by Hash Engineering on 6/21/2016.
 *   Generic Dumping and Loading
 *   ---------------------------
 */
public class FlatDB<Type extends AbstractManager> {
    private static final Logger log = LoggerFactory.getLogger(FlatDB.class);
    private String pathDB;
    private String previousPathDB;
    private String fileName;
    private String directory;
    private String magicMessage;
    public enum ReadResult {
        Ok,
        FileError,
        HashReadError,
        IncorrectHash,
        IncorrectMagicMessage,
        IncorrectMagicNumber,
        IncorrectFormat,
        NoResult
    }

    ReadResult lastReadResult = ReadResult.NoResult;

    Context context;

    public FlatDB(Context context, String fileOrDirectory, boolean isFileName) {
        this.context = context;
        if(isFileName) {
            this.pathDB = fileOrDirectory;
            this.directory = new File(pathDB).getParentFile().getAbsolutePath();
            try {
                this.fileName = new File(pathDB).getCanonicalFile().getName();
            } catch (IOException x) {
                // swallow
            }
        } else {
            this.directory = fileOrDirectory;
            this.pathDB = null;
        }
    }

    public FlatDB(Context context, String fileOrDirectory, boolean isFileName, String magicMessage, int version) {
        this.context = context;
        this.magicMessage = magicMessage + ((version > 1) ? "-" + version : "");
        if(isFileName) {
            this.pathDB = fileOrDirectory;
            this.directory = new File(pathDB).getParentFile().getAbsolutePath();
            try {
                this.fileName = new File(pathDB).getCanonicalFile().getName();
            } catch (IOException x) {
                // swallow
            }
        } else {
            this.directory = fileOrDirectory;
            this.pathDB = null;
        }
    }

    public FlatDB(String directory, String fileName, String magicMessage) {
        context = Context.get();
        this.magicMessage = magicMessage;
        this.fileName = fileName;
        setPath(directory, fileName);
    }

    void setPath(String directory, String file)
    {
        this.directory = directory;
        pathDB = directory + File.separator +file;
    }

    public String getDirectory()
    {
        return directory;
    }

    boolean write(Type object) {

        try {
            Stopwatch watch = Stopwatch.createStarted();

            if (pathDB == null) {
                pathDB = directory + File.separator + object.getDefaultFileName();
            }

            if (magicMessage == null) {
                magicMessage = object.getMagicMessage();
            }

            if (!magicMessage.contains("-"))
                magicMessage = object.getMagicMessage();

            // serialize, checksum data up to that point, then append checksum
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(object.calculateMessageSizeInBytes()+4+magicMessage.getBytes().length);
            stream.write(magicMessage.getBytes());
            Utils.uint32ToByteStreamLE(object.getParams().getPacketMagic(), stream);
            object.bitcoinSerialize(stream);

            Sha256Hash hash = Sha256Hash.twiceOf(stream.toByteArray());

            stream.write(hash.getReversedBytes());

            FileOutputStream fileStream = new FileOutputStream(pathDB);

            fileStream.write(stream.toByteArray());

            fileStream.close();

            log.info("Written info to {}  {}ms", pathDB, watch.elapsed(TimeUnit.MILLISECONDS));
            log.info("  {}", object);

            return true;
        } catch (IndexOutOfBoundsException x) {
            return false;
        } catch (IOException x) {
            return false;
        }
    }

    ReadResult read(Type object, boolean fDryRun) {
        Stopwatch watch = Stopwatch.createStarted();
        try {
            // open input file, and associate with CAutoFile

            if (magicMessage == null) {
                magicMessage = object.getMagicMessage();
            }

            if (pathDB == null) {
                pathDB = directory + File.separator + object.getDefaultFileName();
            }

            if (previousPathDB == null) {
                previousPathDB = directory + File.separator + object.getPreviousDefaultFileName();
            }

            FileInputStream fileStream = new FileInputStream(pathDB);

            File file = new File(pathDB);
            // try loading the previous file
            if (!file.exists() && previousPathDB != null) {
                file = new File(previousPathDB);
            }

            // use file size to size memory buffer
            long fileSize = file.length();
            long dataSize = fileSize - 32;
            // Don't try to resize to a negative number if file is small
            if (dataSize < 0)
                dataSize = 0;
            if(dataSize == 0) {
                fileStream.close();
                return ReadResult.FileError;
            }

            byte [] hashIn = new byte[32];
            byte [] vchData = new byte[(int)dataSize];

            try {
                fileStream.read(vchData);
                fileStream.read(hashIn);
            } catch (IOException x) {
                return ReadResult.HashReadError;
            }
            fileStream.close();

            // verify stored checksum matches input data
            Sha256Hash hashTmp = Sha256Hash.twiceOf(vchData);
            if (!Arrays.equals(hashIn, hashTmp.getReversedBytes())) {
                log.error("Checksum mismatch, data corrupted");
                return ReadResult.IncorrectHash;
            }

            long pchMsgTmp;
            String magicMessageTmp;
            try {
                // de-serialize file header (masternode cache file specific magic message) and ..
                magicMessageTmp = new String(vchData, 0, magicMessage.length());

                log.info("file magic message: {}",magicMessageTmp);
                int fileVersion = 1;
                try {
                    String fileVersionString = magicMessageTmp.substring(magicMessageTmp.lastIndexOf('-') + 1);
                    fileVersion = Integer.parseInt(fileVersionString);
                } catch (NumberFormatException x) {
                    //swallow
                }


                // ... verify the message matches predefined one
                if (!magicMessage.equals(magicMessageTmp)) {
                    String startStrMagicMessageTmp = magicMessageTmp.substring(0, magicMessageTmp.lastIndexOf('-'));

                    String startMagicMessage = magicMessageTmp.substring(0, magicMessage.lastIndexOf('-'));

                    if(!startMagicMessage.equals(startStrMagicMessageTmp)) {
                        log.error("Invalid cache magic message");
                        return ReadResult.IncorrectMagicMessage;
                    }

                    try {
                        String expectedVersionString = magicMessage.substring(magicMessageTmp.lastIndexOf('-') + 1);

                        int expectedVersion = Integer.parseInt(expectedVersionString);

                        if (expectedVersion > fileVersion) {
                            log.error("expected version {} but was {}", expectedVersion, fileVersion);
                            return ReadResult.IncorrectMagicMessage;
                        }

                    } catch (IndexOutOfBoundsException | NumberFormatException x) {
                        //swallow
                    }
                }

                // de-serialize file header (network specific magic number) and ..
                pchMsgTmp = Utils.readUint32(vchData, magicMessage.length());

                // ... verify the network matches ours
                if (pchMsgTmp != context.getParams().getPacketMagic()) {
                    log.error("Invalid network magic number");
                    return ReadResult.IncorrectMagicNumber;
                }
                // de-serialize data into CMasternodeMan object

                object.load(vchData, magicMessageTmp.length()+ 4, fileVersion);

            } catch (Exception e){
                object.clear();
                e.printStackTrace();
                log.error("Deserialize or I/O error - {}",  e.getMessage());
                return  ReadResult.IncorrectFormat;
            }

            log.info("Loaded info from {} {}ms", file.getCanonicalFile(), watch.elapsed(TimeUnit.MILLISECONDS));
            log.info("  {}", object);
            if (!fDryRun) {
                log.info("manager - cleaning....");
                object.checkAndRemove();
                log.info("manager - result:");
                log.info("  {}", object);
            }

            return ReadResult.Ok;
        } catch(IOException x) {
            return ReadResult.FileError;
        }
    }

    ReadResult read(Type object) {
        lastReadResult = read(object, false);
        return lastReadResult;
    }

    public boolean load(Type objToLoad) {
        String fileName = this.fileName != null ? this.fileName : objToLoad.getDefaultFileName();
        log.info("Reading info from {}...", fileName);
        ReadResult readResult = read(objToLoad);
        if (readResult == ReadResult.FileError)
            log.warn("Missing file - {}, will try to recreate", fileName);
        else if (readResult != ReadResult.Ok)
        {
            log.error("Error reading {}: ", fileName);
            if(readResult == ReadResult.IncorrectFormat) {
                log.error("magic is ok but data has invalid format, will try to recreate");
            } else {
                log.error("file format is unknown or invalid, please fix it manually");
                // program should exit with an error
                return false;
            }
        }
        objToLoad.setFilename(pathDB);
        return true;
    }

    public boolean dump(Type objToSave) {
        Stopwatch watch = Stopwatch.createStarted();

        log.info("Writing {} to {}...", objToSave.getMagicMessage(), fileName);
        write(objToSave);
        log.info("{} dump finished  {}ms", fileName, watch.elapsed(TimeUnit.MILLISECONDS));

        return true;
    }
}

