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

import com.google.common.collect.Lists;
import com.google.inject.Provides;
import com.google.inject.mini.MiniGuice;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import vogar.android.ActivityMode;
import vogar.android.AndroidSdk;
import vogar.android.DeviceDalvikVm;
import vogar.android.DeviceFileCache;
import vogar.android.EnvironmentDevice;
import vogar.android.HostDalvikVm;
import vogar.util.Strings;

/**
 * Command line interface for running benchmarks and tests on dalvik.
 */
public final class Vogar {

    private static final int LARGE_TIMEOUT_MULTIPLIER = 10;
    private static final int NUM_PROCESSORS = Runtime.getRuntime().availableProcessors();

    private final List<File> actionFiles = new ArrayList<File>();
    private final List<String> actionClassesAndPackages = new ArrayList<String>();
    private final List<String> targetArgs = new ArrayList<String>();
    private final OptionParser optionParser = new OptionParser(this);
    private File configFile = Vogar.dotFile(".vogarconfig");

    public static File dotFile (String name) {
        return new File(System.getProperty("user.home", "."), name);
    }


    @Option(names = { "--expectations" })
    private Set<File> expectationFiles = new LinkedHashSet<File>();
    {
        expectationFiles.addAll(AndroidSdk.defaultExpectations());
    }

    @Option(names = { "--mode" })
    private ModeId mode = ModeId.DEVICE;

    @Option(names = { "--timeout" })
    private int timeoutSeconds = 1 * 60; // default is one minute;

    @Option(names = { "--first-monitor-port" })
    private int firstMonitorPort = -1;

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

    @Option(names = { "--pass-color" })
    private int passColor = 32; // green

    @Option(names = { "--warn-color" })
    private int warnColor = 33; // yellow

    @Option(names = { "--fail-color" })
    private int failColor = 31; // red

    @Option(names = { "--debug" })
    private Integer debugPort;

    @Option(names = { "--device-dir" })
    private File deviceDir = new File("/sdcard/vogar");

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

    @Option(names = { "--jar-search-dir" })
    private List<File> jarSearchDirs = Lists.newArrayList();

    @Option(names = { "--vogar-dir" })
    private File vogarDir = Vogar.dotFile(".vogar");

    @Option(names = { "--tag-dir" })
    private File tagDir = null;

    @Option(names = { "--tag" }, savedInTag = false)
    private String tagName = null;

    @Option(names = { "--run-tag" }, savedInTag = false)
    private String runTag = null;

    @Option(names = { "--compare-to-tag" }, savedInTag = false)
    private String compareToTag = null;

    @Option(names = { "--tag-overwrite" }, savedInTag = false)
    private boolean tagOverwrite = false;

    @Option(names = { "--record-results" })
    private boolean recordResults = false;

    @Option(names = { "--results-dir" })
    private File resultsDir = null;

    @Option(names = { "--suggest-classpaths" })
    private boolean suggestClasspaths = false;

    @Option(names = { "--invoke-with" })
    private String invokeWith = null;

    @Option(names = { "--native-output" })
    private boolean nativeOutput = false;

    @Option(names = { "--benchmark" })
    private boolean benchmark = false;

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
        System.out.println("  --mode <activity|device|sim|host|jvm>: specify which environment to run in.");
        System.out.println("      activity: runs in an Android application on a device or emulator");
        System.out.println("      device: runs in a Dalvik VM on a device or emulator");
        System.out.println("      sim: runs in a Dalvik VM on the local desktop built with sim-eng lunch combo");
        System.out.println("      host: runs in a Dalvik VM on the local desktop built with any lunch combo.");
        System.out.println("      jvm: runs in a Java VM on the local desktop");
        System.out.println("      Default is: " + mode);
        System.out.println();
        System.out.println("  --clean: synonym for --clean-before and --clean-after (default).");
        System.out.println("      Disable with --no-clean if you want no files removed.");
        System.out.println();
        System.out.println("  --stream: stream output as it is emitted.");
        System.out.println();
        System.out.println("  --native-output: print out native output (prefixed with \"[native]\").");
        System.out.println();
        System.out.println("  --benchmark: for use with dalvikvm, this dexes all files together,");
        System.out.println("      and is mandatory for running Caliper benchmarks, and a good idea");
        System.out.println("      other performance sensitive code.");
        System.out.println();
        System.out.println("  --invoke-with: provide a command to invoke the VM with (e.g. ");
        System.out.println("      \"valgrind --leak-check=full\"). Only supported in certain modes.");
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
        System.out.println("  --vogar-dir <directory>: directory in which to find Vogar");
        System.out.println("      configuration information, caches, saved results, and tags,");
        System.out.println("      unless they've been put explicitly elsewhere.");
        System.out.println("      Default is: " + vogarDir);
        System.out.println();
        System.out.println("  --tag-dir <directory>: directory in which to find tag information.");
        System.out.println("      Default is: " + vogarDir + "/tags");
        System.out.println();
        System.out.println("  --tag <tag name>: creates a tag recording the arguments to this");
        System.out.println("      invocation of Vogar so that it can be rerun later.");
        System.out.println();
        System.out.println("  --run-tag <tag name>: runs Vogar with arguments as specified by the");
        System.out.println("      tag. Any arguments supplied for this run will override those");
        System.out.println("      supplied by the tag.");
        System.out.println();
        System.out.println("  --compare-to-tag <tag name>: compares the results of this run with");
        System.out.println("      the results saved when the tag was created. Defaults to the value");
        System.out.println("      of --run-tag if that argument is given.");
        System.out.println();
        System.out.println("  --record-results: record test results for future comparison.");
        System.out.println();
        System.out.println("  --results-dir <directory>: read and write (if --record-results used)");
        System.out.println("      results from and to this directory.");
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
        System.out.println("  --device-dir <directory>: use the specified directory for");
        System.out.println("      on-device temporary files and code.");
        System.out.println("      Default is: " + deviceDir);
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
        System.out.println("      directories given by --jar-search-dir arguments. If Vogar then ");
        System.out.println("      fails due to missing classes or packages, it will use the index to");
        System.out.println("      diagnose the problem and suggest a fix.");
        System.out.println();
        System.out.println("      Currently only looks for jars called exactly \"classes.jar\".");
        System.out.println();
        System.out.println("  --jar-search-dir <directory>: a directory that should be searched for");
        System.out.println("      jar files to add to the class file index for use with");
        System.out.println("      --suggest-classpaths.");
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
        System.out.println("  --pass-color: ANSI color code to use for passes.");
        System.out.println("      Default: 32 (green)");
        System.out.println();
        System.out.println("  --warn-color: ANSI color code to use for warnings.");
        System.out.println("      Default: 33 (yellow)");
        System.out.println();
        System.out.println("  --fail-color: ANSI color code to use for failures.");
        System.out.println("      Default: 31 (red)");
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
        System.out.println("  --first-monitor-port <port>: the port on the host (and possibly target)");
        System.out.println("      used to traffic control messages between vogar and forked processes.");
        System.out.println("      Use this to avoid port conflicts when running multiple vogar instances");
        System.out.println("      concurrently. Vogar will use up to N ports starting with this one,");
        System.out.println("      where N is the number of processors on the host (" + NUM_PROCESSORS + "). ");
        System.out.println();
        System.out.println("CONFIG FILE");
        System.out.println();
        System.out.println("  User-defined default arguments can be specified in ~/.vogarconfig. See");
        System.out.println("  .vogarconfig.example for an example.");
        System.out.println();
    }

    private boolean parseArgs(String[] args) {
        List<String> actionsAndTargetArgs;

        // extract arguments from config file
        String[] configArgs = optionParser.readFile(configFile);

        // config file args are added first so that in a conflict, the currently supplied
        // arguments win.
        actionsAndTargetArgs = optionParser.parse(configArgs);
        if (!actionsAndTargetArgs.isEmpty()) {
            throw new RuntimeException(
                    "actions or targets given in .vogarconfig: " + actionsAndTargetArgs);
        }

        try {
            actionsAndTargetArgs.addAll(optionParser.parse(args));
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
            if (tagDir == null) {
                tagDir = new File(vogarDir, "/tags");
            }
            try {
                runTagArgs = new Tag(tagDir, runTag, false).getArgs();
                System.out.println("Executing Vogar with additional arguments from tag \""
                        + runTag + "\":");
                System.out.println(Strings.join(" ", (Object[]) runTagArgs));
            } catch (FileNotFoundException e) {
                System.out.println("Tag \"" + runTag + "\" doesn't exist");
                System.out.println("Existing tags are: "
                        + Strings.join(", ", (Object[]) Tag.getAllTags(tagDir)));
                return false;
            }
            // rollback changes already made by the optionParser to insert tag arguments
            optionParser.reset();
            actionsAndTargetArgs = optionParser.parse(configArgs);
            // runTags options are applied first so that the current command's arguments win if
            // there is a conflict
            actionsAndTargetArgs.addAll(optionParser.parse(runTagArgs));
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

        if (firstMonitorPort == -1) {
            firstMonitorPort = mode.isHost() ? 8788 : 8787;
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

        if (invokeWith != null && !mode.supportsInvokeWith()) {
            System.out.println("Valgrind is not supported for mode " + mode);
            return false;
        }

        if (tagName != null) {
            if (tagDir == null) {
                tagDir = new File(vogarDir, "/tags");
            }
            new Tag(tagDir, tagName, tagOverwrite).saveArgs(args);
        }

        return true;
    }

    private boolean run() {
        System.out.println(System.getProperty("java.class.path"));
        Console.init(stream);
        Console.getInstance().setUseColor(color, passColor, warnColor, failColor);
        Console.getInstance().setIndent(indent);
        Console.getInstance().setVerbose(verbose);
        Driver driver = MiniGuice.inject(Driver.class, new Module());
        return driver.buildAndRun(actionFiles, actionClassesAndPackages);
    }

    public static void main(String[] args) {
        Vogar vogar = new Vogar();
        if (!vogar.parseArgs(args)) {
            vogar.printUsage();
            return;
        }
        boolean allSuccess = vogar.run();
        System.exit(allSuccess ? 0 : 1);
    }

    enum ModeId {
        DEVICE, JVM, ACTIVITY, SIM, HOST;

        public boolean supportsInvokeWith() {
            return this == SIM;
        }

        public boolean acceptsVmArgs() {
            return this != ACTIVITY;
        }

        public boolean isHost() {
            return this == JVM || this == SIM || this == HOST;
        }

        public boolean requiresAndroidSdk() {
            return this == DEVICE || this == ACTIVITY || this == SIM || this == HOST;
        }
    }

    private class Module {
        @Provides @Named("additionalVmArgs") List<String> provideAdditionalVmArgs() {
            return vmArgs;
        }

        @Provides @Singleton AndroidSdk provideAndroidSdk() {
            AndroidSdk androidSdk = AndroidSdk.getFromPath();
            if (deviceCache) {
                androidSdk.setDeviceCache(new DeviceFileCache(deviceDir, androidSdk));
            }
            return androidSdk;
        }

        @Provides @Named("benchmark") boolean provideBenchmark() {
            return benchmark;
        }

        @Provides @Named("buildClasspath") Classpath provideBuildClasspath(
                Provider<AndroidSdk> androidSdkProvider) {
            Classpath result = Classpath.of(buildClasspath);
            if (mode.requiresAndroidSdk()) {
                result.addAll(androidSdkProvider.get().getAndroidClasses());
            }
            return result;
        }

        @Provides ClassFileIndex provideClassFileIndex() {
            ClassFileIndex classFileIndex = new ClassFileIndex(jarSearchDirs);
            if (suggestClasspaths) {
                classFileIndex.createIndex();
            }
            return classFileIndex;
        }

        @Provides Classpath provideClasspath() {
            return Classpath.of(classpath);
        }

        @Provides @Named("cleanAfter") boolean provideCleanAfter() {
            return cleanAfter;
        }

        @Provides @Named("cleanBefore") boolean provideCleanBefore() {
            return cleanBefore;
        }

        @Provides @Named("compareToTag") String provideCompareToTag() {
            // Automatically compare to a tag if we explicitly run it
            return (runTag != null && compareToTag == null) ? runTag : compareToTag;
        }

        @Provides Date provideDate() {
            return new Date();
        }

        @Provides @Named("debugPort") Integer provideDebugPort() {
            return debugPort;
        }

        @Provides @Named("deviceDir") File provideDeviceDir() {
            return deviceDir;
        }

        @Provides Environment provideEnvironment(Provider<EnvironmentHost> environmentHostProvider,
                Provider<EnvironmentDevice> environmentDeviceProvider) {
            return mode.isHost()
                    ? environmentHostProvider.get()
                    : environmentDeviceProvider.get();
        }

        @Provides ExpectationStore provideExpectationStore() throws IOException {
            return ExpectationStore.parse(expectationFiles);
        }

        @Provides @Named("fastMode") boolean provideFastMode() {
            return benchmark;
        }

        @Provides @Named("firstMonitorPort") int provideFirstMonitorPort() {
            return firstMonitorPort;
        }

        @Provides @Named("hostBuild") boolean provideHostBuild() {
            return (Vogar.this.mode == ModeId.HOST);
        }

        @Provides @Named("invokeWith") String provideInvokeWith() {
            return invokeWith;
        }

        @Provides @Named("javacArgs") List<String> provideJavacArgs() {
            return javacArgs;
        }

        @Provides @Named("javaHome") File provideJavaHome() {
            return javaHome;
        }

        @Provides @Named("largeTimeoutSeconds") int provideLargeTimeoutSeconds() {
            return timeoutSeconds * LARGE_TIMEOUT_MULTIPLIER;
        }

        @Provides @Singleton @Named("localTemp") File provideLocalTemp() {
            return new File("/tmp/vogar/" + UUID.randomUUID());
        }

        @Provides @Named("nativeOutput") boolean provideNativeOutput() {
            return nativeOutput;
        }

        @Provides @Named("numRunners") int provideNumRunners() {
            return (stream || Vogar.this.mode == ModeId.ACTIVITY)
                    ? 1
                    : NUM_PROCESSORS;
        }

        @Provides Mode provideMode(Provider<JavaVm> javaVmProvider,
                Provider<HostDalvikVm> hostDalvikVmProvider,
                Provider<DeviceDalvikVm> deviceDalvikVmProvider,
                Provider<ActivityMode> activityModeProvider) {
            switch (Vogar.this.mode) {
                case JVM:
                    return javaVmProvider.get();
                case HOST:
                case SIM:
                    return hostDalvikVmProvider.get();
                case DEVICE:
                    return deviceDalvikVmProvider.get();
                case ACTIVITY:
                    return activityModeProvider.get();
                default:
                    throw new AssertionError();
            }
        }

        @Provides @Named("recordResults") boolean provideRecordResults() {
            return recordResults;
        }

        @Provides @Named("resultsDir") File provideResultsDir() {
            return resultsDir == null ? new File(vogarDir, "results") : resultsDir;
        }

        @Provides @Named("runnerDir") File provideRunnerDir() {
            return new File(deviceDir, "run");
        }

        @Provides @Named("smallTimeoutSeconds") int provideSmallTimeoutSeconds() {
            return timeoutSeconds;
        }

        @Provides @Named("sourcepath") List<File> provideSourcepath() {
            return sourcepath;
        }

        @Provides @Named("useBootClasspath") boolean provideUseBootClasspath() {
            return useBootClasspath;
        }

        @Provides @Named("tagDir") File provideTagDir() {
            return tagDir != null ? tagDir : new File(vogarDir, "results/tags");
        }

        @Provides @Named("tagName") String provideTagName() {
            return tagName;
        }

        @Provides @Named("targetArgs") List<String> provideTargetArgs() {
            return targetArgs;
        }

        @Provides @Named("vogarTemp") File provideVogarTemp() {
            return vogarDir;
        }

        @Provides @Named("xmlReportsDirectory") File provideXmlReportsDirectory() {
            return xmlReportsDirectory;
        }
    }
}
