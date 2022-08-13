package org.bitcoinj.examples.debug;

import org.bitcoinj.core.NetworkParameters;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

abstract public class Report {
    File outputFile = new File("tx-report.csv");
    String dashClientPath;
    String confPath;

    public Report(String prefix, String dashClientPath, String confPath, NetworkParameters params) {
        this.dashClientPath = dashClientPath;
        this.confPath = confPath;
        outputFile = new File(prefix + params.getId() + ".csv");
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
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract void printReport();
}
