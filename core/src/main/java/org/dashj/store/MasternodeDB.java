package org.bitcoinj.store;

/**
 * Created by Hash Engineering on 2/26/2016.
 */

/** Access to the MN database (mncache.dat)
 */

import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
@Deprecated
public class MasternodeDB {
    private static final Logger log = LoggerFactory.getLogger(MasternodeDB.class);
    private static String pathMN;
    private static String directory;
    private final static String strMagicMessage = "MasternodeCache";
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

    public MasternodeDB()
    {
        context = Context.get();
    }
    public MasternodeDB(String directory) {
        context = Context.get();
        setPath(directory);
    }

    String getFileName()
    {
        if(context.getParams().getId().equals(NetworkParameters.ID_MAINNET))
            return "mncache.dat";
        if(context.getParams().getId().equals(NetworkParameters.ID_TESTNET))
            return "mncache-testnet.dat";
        return "mncache-other.dat";

    }

    void setPath(File directory)
    {
        this.directory = directory.getAbsolutePath();
        pathMN = directory.getAbsolutePath() + "/" + getFileName();
    }

    void setPath(String directory)
    {
        this.directory = directory;
        pathMN = directory + "/" + getFileName();
    }
    public String getDirectory()
    {
        return directory;
    }

    public boolean write(MasternodeManager mnodemanToSave) {

        try {
            long nStart = Utils.currentTimeMillis();

            // serialize, checksum data up to that point, then append checksum
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(mnodemanToSave.calculateMessageSizeInBytes()+4+strMagicMessage.getBytes().length);
            stream.write(strMagicMessage.getBytes());
            Utils.uint32ToByteStreamLE(mnodemanToSave.getParams().getPacketMagic(), stream);
            mnodemanToSave.bitcoinSerialize(stream);

            Sha256Hash hash = Sha256Hash.twiceOf(stream.toByteArray());


            // open output file, and associate with CAutoFile

            FileOutputStream fileStream = new FileOutputStream(pathMN);

            //FILE * file = fopen(pathMN.string().c_str(), "wb");
            //CAutoFile fileout (file, SER_DISK, CLIENT_VERSION);
            //if (fileout.IsNull())
            //    return error("%s : Failed to open file %s", __func__, pathMN.string());


            // Write and commit header, data
            fileStream.write(stream.toByteArray());
            fileStream.write(hash.getBytes());



//    FileCommit(fileout);
            fileStream.close();
            //fileout.fclose();

            log.info("Written info to mncache.dat  {}ms\n", Utils.currentTimeMillis() - nStart);
            log.info("  {}\n", mnodemanToSave.toString());

            return true;

        }
        catch(IOException x)
        {
            return false;
        }
    }

    public MasternodeManager read(Context context, boolean fDryRun) {

        long nStart = Utils.currentTimeMillis();
        MasternodeManager manager = null;
        try {
            // open input file, and associate with CAutoFile

            FileInputStream fileStream = new FileInputStream(pathMN);

            File file = new File(pathMN);

            /*FILE * file = fopen(pathMN.string().c_str(), "rb");
            CAutoFile filein (file, SER_DISK, CLIENT_VERSION);
            if (filein.IsNull()) {
                error("%s : Failed to open file %s", __func__, pathMN.string());
                return FileError;
            }*/

            // use file size to size memory buffer

            long fileSize = file.length();//fileStream.boost::filesystem::file_size(pathMN);
            long dataSize = fileSize - 32;
            // Don't try to resize to a negative number if file is small
            if (dataSize < 0)
                dataSize = 0;

            //vector<unsigned char>vchData;
            //vchData.resize(dataSize);
            byte [] hashIn = new byte[32];
            byte [] vchData = new byte[(int)dataSize];

            try {
                fileStream.read(vchData);
                fileStream.read(hashIn);
            } catch (IOException x)
            {
                lastReadResult =  ReadResult.HashReadError;
                return null;
            }
            fileStream.close();



            //CDataStream ssMasternodes (vchData, SER_DISK, CLIENT_VERSION);



            // verify stored checksum matches input data
            Sha256Hash hashTmp = Sha256Hash.twiceOf(vchData);
            if (!Arrays.equals(hashIn, hashTmp.getBytes())) {
                log.error("Checksum mismatch, data corrupted");
                lastReadResult = ReadResult.IncorrectHash;
                return null;
            }

            long pchMsgTmp;
            String strMagicMessageTmp;
            try {
                // de-serialize file header (masternode cache file specific magic message) and ..


                strMagicMessageTmp = new String(vchData, 0, strMagicMessage.length());

                // ... verify the message matches predefined one
                if (!strMagicMessage.equals(strMagicMessageTmp)) {
                    log.error("Invalid masternode cache magic message");
                    lastReadResult = ReadResult.IncorrectMagicMessage;
                    return null;
                }

                // de-serialize file header (network specific magic number) and ..
                //ssMasternodes >> FLATDATA(pchMsgTmp);
                pchMsgTmp = (int)Utils.readUint32(vchData, strMagicMessage.length());

                // ... verify the network matches ours
                if (pchMsgTmp != context.getParams().getPacketMagic()) {
                    log.error("Invalid network magic number");
                    lastReadResult = ReadResult.IncorrectMagicNumber;
                    return null;
                }
                // de-serialize data into CMasternodeMan object

                manager = new MasternodeManager(context.getParams(), vchData, strMagicMessage.length()+ 4);

            } catch (Exception e){
                //mnodemanToLoad.Clear();
                log.error("Deserialize or I/O error - {}",  e.getMessage());
                lastReadResult = ReadResult.IncorrectFormat;
                return null;
            }

            log.info("Loaded info from mncache.dat  {}ms", Utils.currentTimeMillis() - nStart);
            log.info("  {}", manager.toString());
            if (!fDryRun) {
                log.info("Masternode manager - cleaning....");
                manager.checkAndRemove();
                log.info("Masternode manager - result:");
                log.info("  {}", manager.toString());
            }

            lastReadResult = ReadResult.Ok;
            return manager;
        }
        catch(IOException x) {
            lastReadResult = ReadResult.FileError;
            return null;
        }
    }
        MasternodeManager read(Context context) {
            return read(context, false);
        }

    /*public static void dumpMasternodes()
    {
        long nStart = Utils.currentTimeMillis();

        NetworkParameters params = Context.get().getParams();

        MasternodeDB mndb = new MasternodeDB(Context.get().masternodeDB.getDirectory());
        MasternodeManager tempMnodeman;

        log.info("Verifying mncache.dat format...");
        //CMasternodeDB::ReadResult readResult =
        tempMnodeman = mndb.read(Context.get(), true);
        MasternodeDB.ReadResult readResult = mndb.lastReadResult;
        // there was an error and it was not an error on file opening => do not proceed
        if (readResult == MasternodeDB.ReadResult.FileError)
            log.info("Missing masternode cache file - mncache.dat, will try to recreate");
        else if (readResult != MasternodeDB.ReadResult.Ok)
        {
            log.info("Error reading mncache.dat: ");
            if(readResult == MasternodeDB.ReadResult.IncorrectFormat)
                log.info("magic is ok but data has invalid format, will try to recreate");
            else
            {
                log.info("file format is unknown or invalid, please fix it manually");
                return;
            }
        }
        log.info("Writing info to mncache.dat...\n");
        mndb.write(Context.get().masternodeManager);

        log.info("Masternode dump finished  {}ms", Utils.currentTimeMillis() - nStart);
    }*/
};

