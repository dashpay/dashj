package org.bitcoinj.masternode.owner;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Hash Engineering on 7/8/2018.
 */
public class MasternodeConfig {

    public static class MasternodeEntry {

        private String alias;
        private String ip;
        private String privKey;
        private String txHash;
        private String outputIndex;

        public MasternodeEntry(String alias, String ip, String privKey, String txHash, String outputIndex) {
            this.alias = alias;
            this.ip = ip;
            this.privKey = privKey;
            this.txHash = txHash;
            this.outputIndex = outputIndex;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getOutputIndex() {
            return outputIndex;
        }

        public void setOutputIndex(String outputIndex) {
            this.outputIndex = outputIndex;
        }

        public String getPrivKey() {
            return privKey;
        }

        public void setPrivKey(String privKey) {
            this.privKey = privKey;
        }

        public String getTxHash() {
            return txHash;
        }

        public void setTxHash(String txHash) {
            this.txHash = txHash;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }
    }

    protected File masternodeConfigFile;

    public MasternodeConfig(File configFile) {
        entries = new ArrayList<>();
        masternodeConfigFile = configFile;
    }

    public MasternodeConfig(String configFile) {
        this(new File(configFile));
    }

    public void add(String alias, String ip, String privKey, String txHash, String outputIndex) {
        Preconditions.checkNotNull(alias);
        Preconditions.checkNotNull(ip);
        Preconditions.checkNotNull(privKey);
        Preconditions.checkNotNull(txHash);
        Preconditions.checkNotNull(outputIndex);
        MasternodeEntry cme = new MasternodeEntry(alias, ip, privKey, txHash, outputIndex);
        entries.add(cme);
    }

    public List<MasternodeEntry> getEntries() {
        return entries;
    }

    public int getCount() {
        return entries.size();
    }

    private final ArrayList<MasternodeEntry> entries;

    public boolean importFromFile(String fileToImport, StringBuilder strError) {
        try {
            //TODO: copy file to correct location, then read()
            File importFile = new File(fileToImport);
            FileInputStream inStream = new FileInputStream(importFile);
            FileOutputStream outStream = new FileOutputStream(masternodeConfigFile);
            byte [] buffer = new byte [(int)importFile.length()];
            inStream.read(buffer);
            outStream.write(buffer);
            inStream.close();
            outStream.close();
            return true;
        } catch (FileNotFoundException x) {
            strError.append(x.getMessage());
            return false;
        } catch (IOException x) {
            strError.append(x.getMessage());
            return false;
        }
    }

    public boolean read(StringBuilder strError) {
        int linenumber = 1;

        try {
            FileReader fileReader = new FileReader(masternodeConfigFile);
            BufferedReader stream = new BufferedReader(fileReader);


            String line;
            while ((line = stream.readLine()) != null) {

                String[] parts = line.split(" ");

                String comment;
                String alias;
                String ip;
                String privKey;
                String txHash;
                String outputIndex;

                if (parts.length == 0 || parts[0].trim().startsWith("#"))
                    continue;

                if (parts.length >= 5) {
                    alias = parts[0];
                    ip = parts[1];
                    privKey = parts[2];
                    txHash = parts[3];
                    outputIndex = parts[4];
                } else {
                    strError.append("Could not parse masternode.conf" + "\n" + String.format("Line: %d", linenumber) + "\n\"" + line + "\"");
                    stream.close();
                    return false;
                }


                Pair<Integer, String> result = Utils.splitHostPort(ip);
                int port = result.getFirst();
                String hostname = result.getSecond();
                if (port == 0 || hostname.equals("")) {
                    strError.append("Failed to parse host:port string" + "\n" + String.format("Line: %d", linenumber) + "\n\"" + line + "\"");
                    stream.close();
                    return false;
                }
                int mainnetDefaultPort = MainNetParams.get().getPort();
                if (Context.get().getParams().getId().equals(NetworkParameters.ID_MAINNET)) {
                    if (port != mainnetDefaultPort) {
                        strError.append("Invalid port detected in masternode.conf" + "\n" + String.format("Port: %d", port) + "\n" + String.format("Line: %d", linenumber) + "\n\"" + line + "\"" + "\n" + String.format("(must be %d for mainnet)", mainnetDefaultPort));
                        stream.close();
                        return false;
                    }
                } else if (port == mainnetDefaultPort) {
                    strError.append("Invalid port detected in masternode.conf" + "\n" + String.format("Line: %d", linenumber) + "\n\"" + line + "\"" + "\n" + String.format("(%d could be used only on mainnet)", mainnetDefaultPort));
                    stream.close();
                    return false;
                }


                add(alias, ip, privKey, txHash, outputIndex);
            }
            stream.close();
            return true;
        } catch (FileNotFoundException x) {
            strError.append(x.getMessage());

            try (FileOutputStream configFile = new FileOutputStream(masternodeConfigFile, true)) {
                String strHeader = "# Masternode config file\n" + "# Format: alias IP:port masternodeprivkey collateral_output_txid collateral_output_index\n" + "# Example: mn1 127.0.0.2:19999 93HaYBVUCYjEMeeH1Y4sBGLALQZE1Yc1K64xiqgX37tGBDQL8Xg 2bcd3c84c84f87eaa86e4e56834c92927a07f9e18718810b92e0d0324456a67c 0\n";
                configFile.write(strHeader.getBytes());
                return true; // Nothing to read, so just return
            } catch (FileNotFoundException x2) {
                strError.append(x2.getMessage());
                return false;
            } catch (IOException x2) {
                strError.append(x2.getMessage());
                return false;
            }
        } catch (IOException x) {
            strError.append(x.getMessage());
            return false;
        }
    }
}

