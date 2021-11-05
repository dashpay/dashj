package org.bitcoinj.store;

/**
 * Created by Hash Engineering on 6/21/2016.
 */

/**
 *   Generic Dumping and Loading
 *   ---------------------------
 */


import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

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
            long nStart = Utils.currentTimeMillis();

            if(pathDB == null) {
                pathDB = directory + File.separator + object.getDefaultFileName();
            }
            if(magicMessage == null) {
                magicMessage = object.getMagicMessage();
            }

            if(!magicMessage.contains("-"))
                magicMessage = object.getMagicMessage();
            // serialize, checksum data up to that point, then append checksum
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(object.calculateMessageSizeInBytes()+4+magicMessage.getBytes().length);
            stream.write(magicMessage.getBytes());
            Utils.uint32ToByteStreamLE(object.getParams().getPacketMagic(), stream);
            object.bitcoinSerialize(stream);

            Sha256Hash hash = Sha256Hash.twiceOf(stream.toByteArray());

            stream.write(hash.getReversedBytes());


            // open output file, and associate with CAutoFile

            FileOutputStream fileStream = new FileOutputStream(pathDB);

            //FILE * file = fopen(pathDB.string().c_str(), "wb");
            //CAutoFile fileout (file, SER_DISK, CLIENT_VERSION);
            //if (fileout.IsNull())
            //    return error("%s : Failed to open file %s", __func__, pathDB.string());


            // Write and commit header, data
            fileStream.write(stream.toByteArray());
            //fileStream.write(hash.getBytes());



//    FileCommit(fileout);
            fileStream.close();
            //fileout.fclose();

            log.info("Written info to {}  {}ms", pathDB, Utils.currentTimeMillis() - nStart);
            log.info("  {}", object.toString());

            return true;

        }
        catch(IOException x)
        {
            return false;
        }
    }

    ReadResult read(Type object, boolean fDryRun) {

        long nStart = Utils.currentTimeMillis();
        try {
            // open input file, and associate with CAutoFile

            if(magicMessage == null) {
                magicMessage = object.getMagicMessage();
            }

            if(pathDB == null) {
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

            long fileSize = file.length();//fileStream.boost::filesystem::file_size(pathDB);
            long dataSize = fileSize - 32;
            // Don't try to resize to a negative number if file is small
            if (dataSize < 0)
                dataSize = 0;
            if(dataSize == 0) {
                fileStream.close();
                return ReadResult.FileError;
            }

            //vector<unsigned char>vchData;
            //vchData.resize(dataSize);
            byte [] hashIn = new byte[32];
            byte [] vchData = new byte[(int)dataSize];

            try {
                fileStream.read(vchData);
                fileStream.read(hashIn);
            } catch (IOException x)
            {
                return ReadResult.HashReadError;
            }
            fileStream.close();



            //CDataStream ssMasternodes (vchData, SER_DISK, CLIENT_VERSION);



            // verify stored checksum matches input data
            Sha256Hash hashTmp = Sha256Hash.twiceOf(vchData);
            if (!Arrays.equals(hashIn, hashTmp.getReversedBytes())) {
                log.error("Checksum mismatch, data corrupted");
                return ReadResult.IncorrectHash;
            }

            long pchMsgTmp;
            String strMagicMessageTmp;
            try {
                // de-serialize file header (masternode cache file specific magic message) and ..


                strMagicMessageTmp = new String(vchData, 0, magicMessage.length());

                log.info("file magic message: " + strMagicMessageTmp);
                // ... verify the message matches predefined one
                if (!magicMessage.equals(strMagicMessageTmp)) {
                    String startStrMagicMessageTmp = strMagicMessageTmp.substring(0, strMagicMessageTmp.lastIndexOf('-'));

                    String startMagicMessage = strMagicMessageTmp.substring(0, strMagicMessageTmp.lastIndexOf('-'));

                    if(!startMagicMessage.equals(startStrMagicMessageTmp)) {
                        log.error("Invalid cache magic message");
                        return ReadResult.IncorrectMagicMessage;
                    }
                }

                // de-serialize file header (network specific magic number) and ..
                //ssMasternodes >> FLATDATA(pchMsgTmp);
                pchMsgTmp = (int)Utils.readUint32(vchData, magicMessage.length());

                // ... verify the network matches ours
                if (pchMsgTmp != context.getParams().getPacketMagic()) {
                    log.error("Invalid network magic number");
                    return ReadResult.IncorrectMagicNumber;
                }
                // de-serialize data into CMasternodeMan object
                int version = 1;
                try {
                    String fileVersionString = strMagicMessageTmp.substring(strMagicMessageTmp.lastIndexOf('-') + 1);
                    version = fileVersionString != null ? Integer.parseInt(fileVersionString) : 1;
                } catch (IndexOutOfBoundsException x) {
                    //swallow
                } catch (NumberFormatException x) {
                    //swallow
                }
                object.load(vchData, magicMessage.length()+ 4, version);

            } catch (Exception e){
                object.clear();
                e.printStackTrace();
                log.error("Deserialize or I/O error - {}",  e.getMessage());
                return  ReadResult.IncorrectFormat;
            }

            log.info("Loaded info from {}  {}ms", fileName, Utils.currentTimeMillis() - nStart);
            log.info("  {}", object.toString());
            if (!fDryRun) {
                log.info("manager - cleaning....");
                object.checkAndRemove();
                log.info("manager - result:");
                log.info("  {}", object.toString());
            }

            return ReadResult.Ok;
        }
        catch(IOException x) {
            return ReadResult.FileError;
        }
    }
    ReadResult read(Type object) {
            return read(object, false);
        }

    public boolean load(Type objToLoad)
    {
        log.info("Reading info from {}...", fileName);
        ReadResult readResult = read(objToLoad);
        if (readResult == ReadResult.FileError)
            log.warn("Missing file - {}, will try to recreate", fileName);
        else if (readResult != ReadResult.Ok)
        {
            log.error("Error reading {}: ", fileName);
            if(readResult == ReadResult.IncorrectFormat)
            {
                log.error("magic is ok but data has invalid format, will try to recreate");
            }
            else {
                log.error("file format is unknown or invalid, please fix it manually");
                // program should exit with an error
                return false;
            }
        }
        objToLoad.setFilename(pathDB);
        return true;
    }

    public boolean dump(Type objToSave)
    {
        long nStart = Utils.currentTimeSeconds();


        // LOAD SERIALIZED FILE TO DETERMINE SAFETY OF SAVING INTO THAT FILE

        /*


            2016-06-02 21:23:55     dash-shutoff |      Governance Objects: 1, Seen Budgets: 1, Seen Budget Votes: 0, Vote Count: 0
            2016-06-02 21:23:55     dash-shutoff |      Governance Objects: 1, Seen Budgets: 0, Seen Budget Votes: 0, Vote Count: 0
            2016-06-02 21:29:17            dashd |      Governance Objects: 1, Seen Budgets: 0, Seen Budget Votes: 0, Vote Count: 0
            2016-06-02 21:29:17            dashd | CFlatDB - Governance Objects: 1, Seen Budgets: 0, Seen Budget Votes: 0, Vote Count: 0
            2016-06-02 21:29:25     dash-shutoff |      Governance Objects: 1, Seen Budgets: 0, Seen Budget Votes: 0, Vote Count: 0
            2016-06-02 21:30:07     dash-shutoff |      Governance Objects: 1, Seen Budgets: 1, Seen Budget Votes: 0, Vote Count: 0
            2016-06-02 21:30:16            dashd |      Governance Objects: 1, Seen Budgets: 1, Seen Budget Votes: 0, Vote Count: 0
            2016-06-02 21:30:16            dashd | CFlatDB - Governance Objects: 1, Seen Budgets: 1, Seen Budget Votes: 0, Vote Count: 0


            This fact can be demonstrated by adding a governance item, then stopping and starting the client.
            With the code enabled, "Seen Budgets" will equal 0, whereas the object should have one entry.
        */

        /*log.info("Verifying {} format...\n", fileName);
        Type tmpObjToLoad = (Type)objToSave.createEmpty();
        ReadResult readResult = read(tmpObjToLoad);

        // there was an error and it was not an error on file opening => do not proceed
        if (readResult == ReadResult.FileError)
            log.warn("Missing file - {}, will try to recreate", fileName);
        else if (readResult != ReadResult.Ok)
        {
            log.error("Error reading{}: ", fileName);
            if(readResult == ReadResult.IncorrectFormat)
                log.error("magic is ok but data has invalid format, will try to recreate");
            else
            {
                log.error("file format is unknown or invalid, please fix it manually");
                return false;
            }
        }*/

        log.info("Writing info to {}...", fileName);
        write(objToSave);
        log.info("{} dump finished  {}ms", fileName, Utils.currentTimeSeconds() - nStart);

        return true;
    }
}

