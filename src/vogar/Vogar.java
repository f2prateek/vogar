/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vogar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vogar.commands.AndroidSdk;

/**
 * Command line interface for running benchmarks and tests on dalvik.
 */
public final class Vogar {

    private final List<File> actionFiles = new ArrayList<File>();
    private final List<String> actionClassesAndPackages = new ArrayList<String>();
    private final List<String> targetArgs = new ArrayList<String>();

    @Option(names = { "--expectations" })
    private Set<File> expectationFiles = new LinkedHashSet<File>();
    {
        File[] files = new File("expectations").listFiles();
        if (files != null) {
            expectationFiles.addAll(Arrays.asList(files));
        }
    }

    @Option(names = { "--mode" })
    private ModeId mode = ModeId.DEVICE;

    @Option(names = { "--timeout" })
    private long timeoutSeconds = 10 * 60; // default is ten minutes;

    @Option(names = { "--monitor-timeout" })
    private long monitorTimeout = 30;

    @Option(names = { "--clean-before" })
    private boolean cleanBefore = true;

    @Option(names = { "--clean-after" })
    private boolean cleanAfter = true;

    @Option(names = { "--clean" })
    private boolean clean = true;

    @Option(names = { "--xml-reports-directory" })
    private File xmlReportsDirectory;

    @Option(names = { "--indent" })
    private String indent = "  ";

    @Option(names = { "--verbose" })
    private boolean verbose;

    @Option(names = { "--stream" })
    private boolean stream = true;

    @Option(names = { "--color" })
    private boolean color = true;

    @Option(names = { "--debug" })
    private Integer debugPort;

    @Option(names = { "--device-runner-dir" })
    private File deviceRunnerDir = new File("/sdcard/dalvikrunner");

    @Option(names = { "--vm-arg" })
    private List<String> vmArgs = new ArrayList<String>();

    @Option(names = { "--java-home" })
    private File javaHome;

    @Option(names = { "--javac-arg" })
    private List<String> javacArgs = new ArrayList<String>();

    @Option(names = { "--use-boot-classpath" })
    private boolean useBootClasspath = false;

    @Option(names = { "--build-classpath" })
    private List<File> buildClasspath = new ArrayList<File>();

    @Option(names = { "--classpath", "-cp" })
    private List<File> classpath = new ArrayList<File>();

    @Option(names = { "--sourcepath" })
    private List<File> sourcepath = new ArrayList<File>();

    private Vogar() {}

    private void printUsage() {
        System.out.println("Usage: Vogar [options]... <actions>... [-- target args]...");
        System.out.println();
        System.out.println("  <actions>: .java files, directories, or class names.");
        System.out.println("      These should be JUnit tests, jtreg tests, Caliper benchmarks");
        System.out.println("      or executable Java classes.");
        System.out.println();
        System.out.println("  [args]: arguments passed to the target process. This is only useful when");
        System.out.println("      the target process is a Caliper benchmark or main method.");
        System.out.println();
        System.out.println("GENERAL OPTIONS");
        System.out.println();
        System.out.println("  --mode <activity|device|sim|host>: specify which environment to run in.");
        System.out.println("      activity: runs in an Android application on a device or emulator");
        System.out.println("      device: runs in a Dalvik VM on a device or emulator");
        System.out.println("      sim: runs in a Dalvik VM on the local desktop.");
        System.out.println("      host: runs in a Java VM on the local desktop");
        System.out.println("      Default is: " + mode);
        System.out.println();
        System.out.println("  --clean: synonym for --clean-before and --clean-after (default).");
        System.out.println("      Disable with --no-clean if you want no files removed.");
        System.out.println();
        System.out.println("  --stream: stream output as it is emitted.");
        System.out.println();
        System.out.println("  --timeout <seconds>: maximum execution time of each action before the");
        System.out.println("      runner aborts it. Specifying zero seconds or using --debug will");
        System.out.println("      disable the execution timeout.");
        System.out.println("      Default is: " + timeoutSeconds);
        System.out.println();
        System.out.println("  --xml-reports-directory <path>: directory to emit JUnit-style");
        System.out.println("      XML test results.");
        System.out.println();
        System.out.println("  --classpath <jar file>: add the .jar to both build and execute classpaths.");
        System.out.println();
        System.out.println("  --use-boot-classpath: use the classpath as search path for bootstrap classes.");
        System.out.println();
        System.out.println("  --build-classpath <element>: add the directory or .jar to the build");
        System.out.println("      classpath. Such classes are available as build dependencies, but");
        System.out.println("      not at runtime.");
        System.out.println();
        System.out.println("  --sourcepath <directory>: add the directory to the build sourcepath.");
        System.out.println();
        System.out.println("  --verbose: turn on persistent verbose output.");
        System.out.println();
        System.out.println("TARGET OPTIONS");
        System.out.println();
        System.out.println("  --debug <port>: enable Java debugging on the specified port.");
        System.out.println("      This port must be free both on the device and on the local");
        System.out.println("      system. Disables the timeout specified by --timeout-seconds.");
        System.out.println();
        System.out.println("  --device-runner-dir <directory>: use the specified directory for");
        System.out.println("      on-device temporary files and code.");
        System.out.println("      Default is: " + deviceRunnerDir);
        System.out.println();
        System.out.println("  --vm-arg <argument>: include the specified argument when spawning a");
        System.out.println("      virtual machine. Examples: -Xint:fast, -ea, -Xmx16M");
        System.out.println();
        System.out.println("  --java-home <java_home>: execute the actions on the local workstation");
        System.out.println("      using the specified java home directory. This does not impact");
        System.out.println("      which javac gets used. When unset, java is used from the PATH.");
        System.out.println();
        System.out.println("EXOTIC OPTIONS");
        System.out.println();
        System.out.println("  --clean-before: remove working directories before building and");
        System.out.println("      running (default). Disable with --no-clean-before if you are");
        System.out.println("      using interactively with your own temporary input files.");
        System.out.println();
        System.out.println("  --clean-after: remove temporary files after running (default).");
        System.out.println("      Disable with --no-clean-after and use with --verbose if");
        System.out.println("      you'd like to manually re-run commands afterwards.");
        System.out.println();
        System.out.println("  --color: format output in technicolor.");
        System.out.println();
        System.out.println("  --expectations <file>: include the specified file when looking for");
        System.out.println("      action expectations. The file should include qualified action names");
        System.out.println("      and the corresponding expected output.");
        System.out.println("      Default is: " + expectationFiles);
        System.out.println();
        System.out.println("  --ident: amount to indent action result output. Can be set to ''");
        System.out.println("      (aka empty string) to simplify output parsing.");
        System.out.println("      Default is: '" + indent + "'");
        System.out.println();
        System.out.println("  --javac-arg <argument>: include the specified argument when invoking");
        System.out.println("      javac. Examples: --javac-arg -Xmaxerrs --javac-arg 1");
        System.out.println();
        System.out.println("  --monitor-timeout <seconds>: number of seconds to wait for the target");
        System.out.println("      process to launch. This can be used to prevent connection failures");
        System.out.println("      when dexopt is slow.");
        System.out.println();
    }

    private boolean parseArgs(String[] args) {
        List<String> actionsAndTargetArgs;
        try {
            actionsAndTargetArgs = new OptionParser(this).parse(args);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            return false;
        }

        //
        // Semantic error validation
        //

        if (javaHome != null && !new File(javaHome, "/bin/java").exists()) {
            System.out.println("Invalid java home: " + javaHome);
            return false;
        }

        // check vm option consistency
        if (!mode.acceptsVmArgs() && !vmArgs.isEmpty()) {
            System.out.println("VM args " + vmArgs + " should not be specified for mode " + mode);
            return false;
        }

        if (xmlReportsDirectory != null && !xmlReportsDirectory.isDirectory()) {
            System.out.println("Invalid XML reports directory: " + xmlReportsDirectory);
            return false;
        }

        if (!clean) {
            cleanBefore = false;
            cleanAfter = false;
        }

        //
        // Post-processing arguments
        //

        // disable timeout when debugging
        if (debugPort != null) {
            timeoutSeconds = 0;
        }

        // separate the actions and the target args
        int index = 0;
        for (; index < actionsAndTargetArgs.size(); index++) {
            String arg = actionsAndTargetArgs.get(index);
            if (arg.equals("--")) {
                index++;
                break;
            }

            File file = new File(arg);
            if (file.exists()) {
                if (arg.endsWith(".java") || file.isDirectory()) {
                    actionFiles.add(file);
                } else {
                    System.out.println("Expected a .jar file, .java file, directory, "
                            + "package name or classname, but was: " + arg);
                    return false;
                }
            } else {
                actionClassesAndPackages.add(arg);
            }
        }

        targetArgs.addAll(actionsAndTargetArgs.subList(index, actionsAndTargetArgs.size()));

        if (actionFiles.isEmpty() && actionClassesAndPackages.isEmpty()) {
            System.out.println("No actions provided.");
            return false;
        }

        if (!mode.acceptsVmArgs() && !targetArgs.isEmpty()) {
            System.out.println("Target args " + targetArgs + " should not be specified for mode " + mode);
            return false;
        }

        return true;
    }

    private void run() {
        Console.getInstance().setColor(color);
        Console.getInstance().setIndent(indent);
        Console.getInstance().setStream(stream);
        Console.getInstance().setVerbose(verbose);

        int monitorPort = mode.isHost() ? 8788 : 8787;
        Mode.Options modeOptions = new Mode.Options(Classpath.of(buildClasspath), sourcepath,
                javacArgs, javaHome, monitorPort, useBootClasspath, Classpath.of(classpath));

        AndroidSdk androidSdk = null;
        if (mode.requiresAndroidSdk()) {
            androidSdk = AndroidSdk.getFromPath();
            modeOptions.buildClasspath.addAll(androidSdk.getAndroidClasses());
        }

        File localTemp = new File("/tmp/vogar/" + UUID.randomUUID());
        Environment environment = mode.isHost()
                ? new EnvironmentHost(cleanBefore, cleanAfter, debugPort, localTemp)
                : new EnvironmentDevice(cleanBefore, cleanAfter, debugPort, monitorPort, localTemp,
                        deviceRunnerDir, androidSdk);

        Vm.Options vmOptions = (mode.acceptsVmArgs())
                ? new Vm.Options(vmArgs, targetArgs)
                : null;

        Mode mode;
        if (this.mode == ModeId.HOST) {
            mode = new JavaVm(environment, modeOptions, vmOptions);
        } else if (this.mode == ModeId.SIM) {
            mode = new HostDalvikVm(environment, modeOptions, vmOptions, androidSdk);
        } else if (this.mode == ModeId.DEVICE) {
            mode = new DeviceDalvikVm(environment, modeOptions, vmOptions);
        } else if (this.mode == ModeId.ACTIVITY) {
            mode = new ActivityMode(environment, modeOptions);
        } else {
            throw new AssertionError();
        }

        HostMonitor monitor = new HostMonitor(monitorTimeout);

        List<RunnerSpec> runnerSpecs = Arrays.asList(
                new JtregSpec(localTemp),
                new JUnitSpec(),
                new CaliperSpec(),
                new MainSpec());

        ExpectationStore expectationStore;
        try {
            expectationStore = ExpectationStore.parse(expectationFiles);
        } catch (IOException e) {
            System.out.println("Problem loading expectations: " + e);
            return;
        }

        XmlReportPrinter xmlReportPrinter = xmlReportsDirectory != null
                ? new XmlReportPrinter(xmlReportsDirectory, expectationStore)
                : null;

        Driver driver = new Driver(
                localTemp,
                mode,
                expectationStore,
                runnerSpecs,
                xmlReportPrinter,
                monitor,
                monitorPort,
                timeoutSeconds);

        driver.buildAndRun(actionFiles, actionClassesAndPackages);
    }

    public static void main(String[] args) {
        Vogar vogar = new Vogar();
        if (!vogar.parseArgs(args)) {
            vogar.printUsage();
            return;
        }
        vogar.run();
    }

    enum ModeId {
        DEVICE, HOST, ACTIVITY, SIM;

        public boolean acceptsVmArgs() {
            return this != ACTIVITY;
        }

        public boolean isHost() {
            return this == ModeId.HOST || this == ModeId.SIM;
        }

        public boolean requiresAndroidSdk() {
            return this == DEVICE || this == ACTIVITY || this == SIM;
        }
    }
}
