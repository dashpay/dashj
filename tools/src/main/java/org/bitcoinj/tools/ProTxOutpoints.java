package org.bitcoinj.tools;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public class ProTxOutpoints {
    public static void main(String[] args) {
        try {
            String network = "";
            if (args.length >= 1) {
                network = "-" + args[0];
            }
            InputStream stream = ProTxOutpoints.class.getResourceAsStream("masternodelist"+network+".json");
            byte[] buffer = new byte[stream.available()];
            stream.read(buffer);

            JSONObject json = new JSONObject(new String(buffer));

            Set<String> keys = json.toMap().keySet();

            System.out.println("String [][] proTxHashOutpoints = new String[][] {");

            for (String key : keys) {
                //String txHash = key.substring(32);
                //int index = Integer.parseInt(key.substring(33));

                JSONObject mn = json.getJSONObject(key);
                String proTxHash = mn.getString("proTxHash");

                System.out.println("    {\"" + proTxHash + "\", \"" + key + "\"},");

            }
            System.out.println("};");

            //System.out.println(json.toString(2));
        } catch (IOException x) {
            System.out.println("Failed: " + x.getMessage());
        }
    }
}
