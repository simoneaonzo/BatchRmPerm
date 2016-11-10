package it.unige.dibris.batchrmperm.service;


import it.unige.dibris.batchrmperm.BatchRmPermApplication;
import it.unige.dibris.batchrmperm.domain.Apk;
import it.unige.dibris.batchrmperm.exception.InstallationException;
import it.unige.dibris.batchrmperm.exception.OutputEmptyException;
import it.unige.dibris.batchrmperm.repository.ApkRepository;

import it.unige.dibris.batchrmperm.utility.Utility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BatchWork {
    private static final String adb = "/home/simo/android-sdk-linux/platform-tools/adb";
    private static final String emulator = "/home/simo/android-sdk-linux/tools/emulator";
    private static final String APKS_FOLDER = "/media/simo/HDEsterno/ApkSamples/";
    private static final File FAILS_FOLDER = new File(APKS_FOLDER, "Fails");

    @Autowired
    ApkRepository apkRepository;

    @Async
    public void doTheWork() throws IOException {
        System.out.println("--> Start.");
        try {
            FAILS_FOLDER.mkdir();
            adbStartServer();
            if (devicesAttached().isEmpty()) {
                startEmulator();
            }

        } catch (Exception e) { // unsatisfied requirement
            e.printStackTrace();
            BatchRmPermApplication.close();
        }
        List<Path> apksPath = Utility.listApkFiles(Paths.get(APKS_FOLDER));

        for (Path apkPath : apksPath) {
            try {
                System.out.println(apkPath.toString());
                Apk apk = new Apk(apkPath);
                installBatch(apk);
                apk.setMonkeyCrash(testMonkey(apk.getPackName()));
                apkRepository.save(apk);
                returnToHomeScreen();
                adbUninstallApk(apk.getPackName());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("<-- End.");
    }


    private void returnToHomeScreen() throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add(adb);
        args.add("shell");
        args.add("am");
        args.add("start");
        args.add("-a");
        args.add("android.intent.action.MAIN");
        args.add("-c");
        args.add("android.intent.category.HOME");
        execute(args);
    }


    /**
     *
     * @param packageName
     * @return true if the app crashed, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean testMonkey(String packageName) throws IOException, InterruptedException, OutputEmptyException {
        List<String> args = new ArrayList<>();
        args.add(adb);
        args.add("shell");
        args.add("monkey");
        args.add("-p");
        args.add(packageName);
        args.add("-vv");
        args.add("256");
        List<String> output = execute(args);
        if (output.isEmpty())
            throw new OutputEmptyException();
        return output.get(output.size()-1).contains("System appears to have crashed");
    }


    private void adbUninstallApk(String packageName) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add(adb);
        args.add("uninstall");
        args.add(packageName);
        List<String> output = execute(args);
        if (!output.isEmpty()) {
            if (output.get(0).equals("Success")) {
                System.out.println("Successfully uninstalled: " + packageName);
            }
            else
                System.err.println("Failure uninstall: " + packageName);

        }
    }

    private void adbStartServer() throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add(adb);
        args.add("start-server");
        execute(args, true, false);
    }

    private void adbRebootEmulator() throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add(adb);
        args.add("shell");
        args.add("reboot");
        execute(args, true, false);
        Thread.sleep(30000);
    }

    private void startEmulator() throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add(emulator);
        args.add("-avd");
        //args.add("Nexus_5_API_23");
        args.add("N5arm");
        args.add("-wipe-data");
        execute(args, true, false);
        Thread.sleep(27000);
    }



    private List<String> devicesAttached() throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add(adb);
        args.add("devices");
        List<String> output = execute(args);
        output.remove("");
        output.remove("List of devices attached");
        return output;
    }


    private void installBatch(Apk apk) throws IOException, InterruptedException, InstallationException {
        List<String> args = new ArrayList<>();
        args.add(adb);
        args.add("install");
        args.add("-r");
        args.add(apk.getPath().toString());
        List<String> output = execute(args);
        if (output.isEmpty()) {
            System.err.println(apk);
            return;
        }
        String last = output.get(output.size() - 1);
        if (!output.isEmpty() && last.equals("Success")) {
            System.out.println(Arrays.toString(output.toArray()));
            apk.setInstallSuccess(true);
        }
        else {
            System.err.println(Arrays.toString(output.toArray()));
            Pattern pattern = Pattern.compile("^Failure \\[(\\w*)\\]$");
            Matcher matcher = pattern.matcher(last);
            if (matcher.matches()) {
                String reason = matcher.group(1);

                /*
                if (reason.equals("INSTALL_FAILED_INSUFFICIENT_STORAGE")) {
                    adbRebootEmulator();
                }
                */

                System.err.println(reason);
                apk.setInstallSuccess(false);
                apk.setFailureReason(reason);
                Path apkPath = apk.getPath();
                java.nio.file.Files.move(apkPath, Paths.get(FAILS_FOLDER.toString(), apkPath.getFileName().toString()));
            }
        }
    }

    private List<String> execute(List<String> args) throws IOException, InterruptedException {
        return execute(args, true, true);
    }

    private List<String> execute(List<String> args, boolean getInputStream, boolean waitFor) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        Process process = pb.start();
        InputStream is;
        if (getInputStream)
            is = process.getInputStream();
        else
            is = process.getErrorStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        if (waitFor) {
            List<String> output = new LinkedList<>();
            process.waitFor(3, TimeUnit.MINUTES);
            //process.waitFor();
            String line;
            while ((line = br.readLine()) != null) {
                output.add(line);
            }
            return output;
        }
        return null;
    }


}
