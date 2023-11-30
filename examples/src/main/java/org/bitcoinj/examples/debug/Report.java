package org.bitcoinj.examples.debug;

import org.bitcoinj.core.NetworkParameters;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

abstract public class Report {
    File outputFile;
    String dashClientPath;
    String confPath;

    private static final DateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH_mm");

    public Report(String prefix, String dashClientPath, String confPath, NetworkParameters params) {
        this.dashClientPath = dashClientPath;
        this.confPath = confPath;
        outputFile = new File(prefix + params.getId() + "-" + format.format(new Date()) + ".csv");
    }

    JSONObject runRPCCommand(String command) {
        try {
            String config;
            if (confPath.startsWith("-")) {
                config = confPath;
            } else {
                config = String.format("-conf=%s", confPath);
            }
            Process process = Runtime.getRuntime().exec(String.format("%s %s %s", dashClientPath, config, command));
            int result = process.waitFor();

            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(process.getInputStream()));

            String s;
            StringBuilder output = new StringBuilder();
            while ((s = stdInput.readLine()) != null) {
                output.append(s);
            }

            return new JSONObject(output.toString());

        } catch (IOException e) {
            return null;
        } catch (JSONException e) {
            System.out.println("There is a problem parsing the output" + e);
            return null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract void printReport();
}
