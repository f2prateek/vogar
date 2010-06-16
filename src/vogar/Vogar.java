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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vogar.commands.AndroidSdk;

/**
 * Command line interface for running benchmarks and tests on dalvik.
 */
public final class Vogar {

    private static final int LARGE_TIMEOUT_MULTIPLIER = 10;

    private final List<File> actionFiles = new ArrayList<File>();
    private final List<String> actionClassesAndPackages = new ArrayList<String>();
    private final List<String> targetArgs = new ArrayList<String>();
    private final OptionParser optionParser = new OptionParser(this);

    @Option(names = { "--expectations" })
    private Set<File> expectationFiles = new LinkedHashSet<File>();
    {
        expectationFiles.addAll(AndroidSdk.defaultExpectations());
    }

    @Option(names = { "--mode" })
    private ModeId mode = ModeId.DEVICE;

    @Option(names = { "--timeout" })
    private int timeoutSeconds = 1 * 60; // default is one minute;

    @Option(names = { "--monitor-timeout" })
    private int monitorTimeout = 30;

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
    private File deviceRunnerDir = new File("/sdcard/vogar");

    @Option(names = { "--vm-arg" })
    private List<String> vmArgs = new ArrayList<String>();

    @Option(names = { "--java-home" })
    private File javaHome;

    @Option(names = { "--javac-arg" })
    private List<String> javacArgs = new ArrayList<String>();

    @Option(names = { "--use-bootclasspath" })
    private boolean useBootClasspath = false;

    @Option(names = { "--build-classpath" })
    private List<File> buildClasspath = new ArrayList<File>();

    @Option(names = { "--classpath", "-cp" })
    private List<File> classpath = new ArrayList<File>();

    @Option(names = { "--sourcepath" })
    private List<File> sourcepath = new ArrayList<File>();
    {
        sourcepath.addAll(AndroidSdk.defaultSourcePath());
    }

    @Option(names = { "--device-cache" })
    private boolean deviceCache = true;

    @Option(names = { "--tag-dir" }, savedInTag = false)
    private File tagDir = new File(System.getProperty("user.home", ".") + "/.vogar/tags/");

    @Option(names = { "--tag" }, savedInTag = false)
    private String tagName = null;

    @Option(names = { "--run-tag" }, savedInTag = false)
    private String runTag = null;

    @Option(names = { "--tag-overwrite" }, savedInTag = false)
    private boolean tagOverwrite = false;

    @Option(names = { "--suggest-classpaths" })
    private boolean suggestClasspaths = false;

    private Vogar() {}

    private void printUsage() {
        // have to reset fields so that "Default is: FOO" lines are accurate
        optionParser.reset();

        System.out.println("Usage: Vogar [options]... <actions>... [-- target args]...");
        System.out.println();
        System.out.println("  <actions>: .java files, directories, or class names.");
        System.out.println("      These should be JUnit tests, jtreg tests, Caliper benchmarks");
        System.out.println("      or executable Java classes.");
        System.out.println();
        System.out.println("      When passing in a JUnit test class, it may have \"#method_name\"");
        System.out.println("      appended to it, to specify a single test method.");
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
        System.out.println("      disable the execution timeout. Tests tagged with 'large' will time");
        System.out.println("      out in " + LARGE_TIMEOUT_MULTIPLIER + "x this timeout.");
        System.out.println("      Default is: " + timeoutSeconds);
        System.out.println();
        System.out.println("  --xml-reports-directory <path>: directory to emit JUnit-style");
        System.out.println("      XML test results.");
        System.out.println();
        System.out.println("  --classpath <jar file>: add the .jar to both build and execute classpaths.");
        System.out.println();
        System.out.println("  --use-bootclasspath: use the classpath as search path for bootstrap classes.");
        System.out.println();
        System.out.println("  --build-classpath <element>: add the directory or .jar to the build");
        System.out.println("      classpath. Such classes are available as build dependencies, but");
        System.out.println("      not at runtime.");
        System.out.println();
        System.out.println("  --sourcepath <directory>: add the directory to the build sourcepath.");
        System.out.println();
        System.out.println("  --tag-dir <directory>: directory in which to find tags.");
        System.out.println("      Default is: " + tagDir);
        System.out.println();
        System.out.println("  --tag <tag name>: creates a tag recording the arguments to this");
        System.out.println("      invocation of Vogar so that it can be rerun later.");
        System.out.println();
        System.out.println("  --run-tag <tag name>: runs Vogar with arguments as specified by the");
        System.out.println("      tag. Any arguments supplied for this run will override those");
        System.out.println("      supplied by the tag.");
        System.out.println();
        System.out.println("  --tag-overwrite: allow --tag to overwrite an existing tag.");
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
        System.out.println("  --device-cache: keep copies of dexed files on the SD card so they");
        System.out.println("      don't need to be pushed each time a test is run, improving");
        System.out.println("      start times (default). Only affects device mode. Disable with");
        System.out.println("      --no-device-cache to save space on the SD card.");
        System.out.println();
        System.out.println("  --suggest-classpaths: build an index of jar files under the");
        System.out.println("      directories given in VOGAR_JAR_PATH (a colon separated");
        System.out.println("      environment variable). If Vogar then fails due to missing");
        System.out.println("      classes or packages, it will use the index to diagnose the");
        System.out.println("      problem and suggest a fix.");
        System.out.println();
        System.out.println("      Currently only looks for jars called exactly \"classes.jar\".");
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
        System.out.println("  --indent: amount to indent action result output. Can be set to ''");
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
            actionsAndTargetArgs = optionParser.parse(args);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            return false;
        }

        if (runTag != null && tagName != null) {
            System.out.println("Cannot use both --run-tag and --tag options");
            return false;
        }

        if (runTag != null) {
            String oldTag = tagName;
            String[] runTagArgs;
            try {
                runTagArgs = new Tag(tagDir, runTag, false).getArgs();
                System.out.println("Executing Vogar with additional arguments from tag \""
                        + runTag + "\":");
                System.out.println(Strings.join(runTagArgs, " "));
            } catch (FileNotFoundException e) {
                System.out.println("Tag \"" + runTag + "\" doesn't exist");
                System.out.println("Existing tags are: "
                        + Strings.join(Tag.getAllTags(tagDir), ", "));
                return false;
            }
            // rollback changes already made by the optionParser
            optionParser.reset();
            // runTags options are applied first so that the current command's arguments win if
            // there is a conflict
            actionsAndTargetArgs = optionParser.parse(runTagArgs);
            // tag is the only argument we don't allow to be supplied by the run tag
            tagName = oldTag;
            actionsAndTargetArgs.addAll(optionParser.parse(args));
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
                    actionFiles.add(file.getAbsoluteFile());
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

        if (tagName != null) {
            new Tag(tagDir, tagName, tagOverwrite).saveArgs(args);
        }

        return true;
    }

    private void run() {
        Console.init(stream);
        Console.getInstance().setColor(color);
        Console.getInstance().setIndent(indent);
        Console.getInstance().setVerbose(verbose);

        ClassFileIndex classFileIndex = new ClassFileIndex();
        if (suggestClasspaths) {
            classFileIndex.createIndex();
        }

        int numRunners = (stream || this.mode == ModeId.ACTIVITY)
                ? 1
                : Runtime.getRuntime().availableProcessors();

        int monitorPort = mode.isHost() ? 8788 : 8787;
        Mode.Options modeOptions = new Mode.Options(Classpath.of(buildClasspath), sourcepath,
                javacArgs, javaHome, monitorPort, timeoutSeconds, useBootClasspath, Classpath.of(classpath));

        AndroidSdk androidSdk = null;
        if (mode.requiresAndroidSdk()) {
            androidSdk = AndroidSdk.getFromPath();
            androidSdk.setDeviceCache(deviceCache);
            modeOptions.buildClasspath.addAll(androidSdk.getAndroidClasses());
        }

        File localTemp = new File("/tmp/vogar/" + UUID.randomUUID());
        Environment environment = mode.isHost()
                ? new EnvironmentHost(cleanBefore, cleanAfter, debugPort, localTemp)
                : new EnvironmentDevice(cleanBefore, cleanAfter, debugPort, monitorPort, numRunners, localTemp,
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

        int smallTimeoutSeconds = timeoutSeconds;
        Driver driver = new Driver(
                localTemp,
                mode,
                expectationStore,
                xmlReportPrinter,
                monitorTimeout,
                monitorPort,
                smallTimeoutSeconds,
                smallTimeoutSeconds * LARGE_TIMEOUT_MULTIPLIER,
                classFileIndex,
                numRunners);

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
