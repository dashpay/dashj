package com.google.bitcoin.core;

import java.math.BigInteger;
import java.util.Date;
import java.util.Map;
import java.util.Vector;

/**
 * Created with IntelliJ IDEA.
 * User: HashEngineering
 * Date: 8/13/13
 * Time: 7:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class CoinDefinition {


    public static final String coinName = "LimeCoin";
    public static final String coinTicker = "DGC";
    public static final String coinURIScheme = "limecoin";
    public static final String cryptsyMarketId = "26";
    public static final String cryptsyMarketCurrency = "BTC";
    public static final String PATTERN_PRIVATE_KEY_START = "6";

    public enum CoinPrecision {
        Coins,
        Millicoins,
    }
    public static final CoinPrecision coinPrecision = CoinPrecision.Coins;


    public static final String BLOCKEXPLORER_BASE_URL_PROD = "http://limecoin.coinexplorers.com/";    //blockr.io
    public static final String BLOCKEXPLORER_ADDRESS_PATH = "address/";             //blockr.io path
    public static final String BLOCKEXPLORER_TRANSACTION_PATH = "tx/";              //blockr.io path
    public static final String BLOCKEXPLORER_BLOCK_PATH = "block/";                 //blockr.io path
    public static final String BLOCKEXPLORER_BASE_URL_TEST = BLOCKEXPLORER_BASE_URL_PROD;

    public static final String DONATION_ADDRESS = "8te616mgL3DM9H7dvPpajeidb6ng6HPxc1";  //HashEngineering donation DGC address

    enum CoinHash {
        SHA256,
        scrypt,
        x11
    };
    public static final CoinHash coinPOWHash = CoinHash.x11;

    public static boolean checkpointFileSupport = true;

    public static final int TARGET_TIMESPAN = (int)(36 * 10 * 60);  // 6 hours per difficulty cycle, on average.
    public static final int TARGET_SPACING = (int)(10 * 60);  // 10 minutes seconds per block.
    public static final int INTERVAL = TARGET_TIMESPAN / TARGET_SPACING;  //36 blocks

    public static final int getInterval(int height, boolean testNet) {
            return INTERVAL;      //108
    }
    public static final int getIntervalCheckpoints() {
            return INTERVAL;

    }
    public static final int getTargetTimespan(int height, boolean testNet) {
            return TARGET_TIMESPAN;    //72 min
    }

    public static int spendableCoinbaseDepth = 25; //main.h: static const int COINBASE_MATURITY
    public static final BigInteger MAX_MONEY = BigInteger.valueOf(21000000).multiply(Utils.COIN);                 //main.h:  MAX_MONEY
    //public static final String MAX_MONEY_STRING = "200000000";     //main.h:  MAX_MONEY

    public static final BigInteger DEFAULT_MIN_TX_FEE = BigInteger.valueOf(100000);   // MIN_TX_FEE
    public static final BigInteger DUST_LIMIT = BigInteger.valueOf(100000); //main.h CTransaction::GetMinFee        0.01 coins

    public static final int PROTOCOL_VERSION = 70002;          //version.h PROTOCOL_VERSION
    public static final int MIN_PROTOCOL_VERSION = 70002;        //version.h MIN_PROTO_VERSION

    public static final int BLOCK_CURRENTVERSION = 1;   //CBlock::CURRENT_VERSION
    public static final int MAX_BLOCK_SIZE = 1 * 1000 * 1000;


    public static final boolean supportsBloomFiltering = false; //Requires PROTOCOL_VERSION 70000 in the client

    public static final int Port    = 9030;       //protocol.h GetDefaultPort(testnet=false)
    public static final int TestPort = 19030;     //protocol.h GetDefaultPort(testnet=true)

    //
    //  Production
    //
    public static final int AddressHeader = 19;             //base58.h CBitcoinAddress::PUBKEY_ADDRESS
    public static final int p2shHeader = 5;             //base58.h CBitcoinAddress::SCRIPT_ADDRESS
    public static final boolean allowBitcoinPrivateKey = true; //for backward compatibility with previous version of digitalcoin
    public static final int dumpedPrivateKeyHeader = 128;   //common to all coins
    public static final long PacketMagic = 0xfbc0b6db;      //0xfb, 0xc0, 0xb6, 0xdb

    //Genesis Block Information from main.cpp: LoadBlockIndex
    static public long genesisBlockDifficultyTarget = (0x1e0ffff0L);         //main.cpp: LoadBlockIndex
    static public long genesisBlockTime = 1396799176L;                       //main.cpp: LoadBlockIndex
    static public long genesisBlockNonce = (33661576);                         //main.cpp: LoadBlockIndex
    static public String genesisHash = "00000ca3f904bc6db2ffc72f800858d4d0f56f14ca412c17794c3bd31161acc0"; //main.cpp: hashGenesisBlock
    static public int genesisBlockValue = 100;                                                              //main.cpp: LoadBlockIndex
    //taken from the raw data of the block explorer
    static public String genesisXInBytes = "04ffff001d01044c5b6c696d65636f696e20736520636f6e7665727469726120656e20756e61206465206c6173206d6f6e65646173206d6173207365677572612064656c206d65726361646f2c206368656361206e75657374726f73206176616e636573";   //"limecoin se convertira en una de las monedas mas segura del mercado, checa nuestros avances"
    static public String genessiXOutBytes = "040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9";

    //net.cpp strDNSSeed
    static public String[] dnsSeeds = new String[] {
            "209.208.111.8",
            "209.208.109.194",
            "209.208.108.71",
            "69.28.94.67"
    };

    public static int minBroadcastConnections = 0;   //0 for default; we need more peers.

    //
    // TestNet - limecoin - not tested
    //
    public static final boolean supportsTestNet = false;
    public static final int testnetAddressHeader = 111;             //base58.h CBitcoinAddress::PUBKEY_ADDRESS_TEST
    public static final int testnetp2shHeader = 196;             //base58.h CBitcoinAddress::SCRIPT_ADDRESS_TEST
    public static final long testnetPacketMagic = 0xfcc1b7dc;      //0xfc, 0xc1, 0xb7, 0xdc
    public static final String testnetGenesisHash = "5e039e1ca1dbf128973bf6cff98169e40a1b194c3b91463ab74956f413b2f9c8";
    static public long testnetGenesisBlockDifficultyTarget = (0x1e0ffff0L);         //main.cpp: LoadBlockIndex
    static public long testnetGenesisBlockTime = 999999L;                       //main.cpp: LoadBlockIndex
    static public long testnetGenesisBlockNonce = (99999);                         //main.cpp: LoadBlockIndex





    //main.cpp GetBlockValue(height, fee)
    public static final BigInteger GetBlockReward(int height)
    {
        int COIN = 1;
        BigInteger nSubsidy = Utils.toNanoCoins(100, 0);
        if (height == 1)
            nSubsidy = Utils.toNanoCoins(420000, 0);
        return nSubsidy;
    }

    public static int subsidyDecreaseBlockCount = 4730400;     //main.cpp GetBlockValue(height, fee)

    public static BigInteger proofOfWorkLimit = Utils.decodeCompactBits(0x1e0fffffL);  //main.cpp bnProofOfWorkLimit (~uint256(0) >> 20); // digitalcoin: starting difficulty is 1 / 2^12

    static public String[] testnetDnsSeeds = new String[] {
          "not supported"
    };
    //from main.h: CAlert::CheckSignature
    public static final String SATOSHI_KEY = "04A9CFD81AF5D53310BE45E6326E706A542B1028DF85D2819D5DE496D8816C83129CE874FE5E3A23B03544BFF35458833779DAB7A6FF687525A4E23CA59F1E2B94";
    public static final String TESTNET_SATOSHI_KEY = "";

    /** The string returned by getId() for the main, production network where people trade things. */
    public static final String ID_MAINNET = "org.digitalcoin.production";
    /** The string returned by getId() for the testnet. */
    public static final String ID_TESTNET = "org.digitalcoin.test";
    /** Unit test network. */
    public static final String ID_UNITTESTNET = "com.google.digitalcoin.unittest";

    //checkpoints.cpp Checkpoints::mapCheckpoints
    public static void initCheckpoints(Map<Integer, Sha256Hash> checkpoints)
    {
        checkpoints.put( 3,   new Sha256Hash("000009900a75b87031c6fdfa5692299818b4337ec51089bdc38cafa0a92b4da3"));
        checkpoints.put( 4,   new Sha256Hash("000007409c06e48341e29c5e983c5226b33eec994e107fcc28b6934a53639a30"));
        checkpoints.put( 345, new Sha256Hash("0000000006224c943f02998d2685370b1c51138f89658aaa91213e97b9aa5a88"));

    }

    //Unit Test Information
    public static final String UNITTEST_ADDRESS = "DPHYTSm3f96dHRY3VG1vZAFC1QrEPkEQnt";
    public static final String UNITTEST_ADDRESS_PRIVATE_KEY = "QU1rjHbrdJonVUgjT7Mncw7PEyPv3fMPvaGXp9EHDs1uzdJ98hUZ";

}
