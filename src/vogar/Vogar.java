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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import vogar.android.AndroidSdk;

/**
 * Command line interface for running benchmarks and tests on dalvik.
 */
public final class Vogar {
    static final int LARGE_TIMEOUT_MULTIPLIER = 10;
    static final int NUM_PROCESSORS = Runtime.getRuntime().availableProcessors();

    private final List<File> actionFiles = new ArrayList<File>();
    private final List<String> actionClassesAndPackages = new ArrayList<String>();
    final List<String> targetArgs = new ArrayList<String>();
    private final OptionParser optionParser = new OptionParser(this);
    private File configFile = Vogar.dotFile(".vogarconfig");

    public static File dotFile (String name) {
        return new File(System.getProperty("user.home", "."), name);
    }

    @Option(names = { "--expectations" })
    Set<File> expectationFiles = new LinkedHashSet<File>();
    {
        expectationFiles.addAll(AndroidSdk.defaultExpectations());
    }

    @Option(names = { "--mode" })
    ModeId mode = ModeId.DEVICE;

    @Option(names = { "--ssh" })
    String sshHost;

    @Option(names = { "--timeout" })
    int timeoutSeconds = 1 * 60; // default is one minute;

    @Option(names = { "--first-monitor-port" })
    int firstMonitorPort = -1;

    @Option(names = { "--clean-before" })
    boolean cleanBefore = true;

    @Option(names = { "--clean-after" })
    boolean cleanAfter = true;

    @Option(names = { "--clean" })
    private boolean clean = true;

    @Option(names = { "--xml-reports-directory" })
    File xmlReportsDirectory;

    @Option(names = { "--indent" })
    String indent = "  ";

    @Option(names = { "--verbose" })
    boolean verbose;

    @Option(names = { "--stream" })
    boolean stream = true;

    @Option(names = { "--color" })
    boolean color = true;

    @Option(names = { "--pass-color" })
    int passColor = 32; // green

    @Option(names = { "--warn-color" })
    int warnColor = 33; // yellow

    @Option(names = { "--fail-color" })
    int failColor = 31; // red

    @Option(names = { "--ansi" })
    boolean ansi = !"dumb".equals(System.getenv("TERM"));

    @Option(names = { "--debug" })
    Integer debugPort;

    @Option(names = { "--device-dir" })
    File deviceDir;

    @Option(names = { "--vm-arg" })
    List<String> vmArgs = new ArrayList<String>();

    @Option(names = { "--java-home" })
    File javaHome;

    @Option(names = { "--javac-arg" })
    List<String> javacArgs = new ArrayList<String>();

    @Option(names = { "--use-bootclasspath" })
    boolean useBootClasspath = false;

    @Option(names = { "--build-classpath" })
    List<File> buildClasspath = new ArrayList<File>();

    @Option(names = { "--classpath", "-cp" })
    List<File> classpath = new ArrayList<File>();

    @Option(names = { "--sourcepath" })
    List<File> sourcepath = new ArrayList<File>();
    {
        sourcepath.addAll(AndroidSdk.defaultSourcePath());
    }

    @Option(names = { "--jar-search-dir" })
    List<File> jarSearchDirs = Lists.newArrayList();

    @Option(names = { "--vogar-dir" })
    File vogarDir = Vogar.dotFile(".vogar");

    @Option(names = { "--record-results" })
    boolean recordResults = false;

    @Option(names = { "--results-dir" })
    File resultsDir = null;

    @Option(names = { "--suggest-classpaths" })
    boolean suggestClasspaths = false;

    @Option(names = { "--invoke-with" })
    String invokeWith = null;

    @Option(names = { "--benchmark" })
    boolean benchmark = false;

    @Option(names = { "--open-bugs-command" })
    String openBugsCommand;

    @Option(names = { "--profile" })
    boolean profile = false;

    @Option(names = { "--profile-binary" })
    boolean profileBinary = false;

    @Option(names = { "--profile-file" })
    File profileFile;

    @Option(names = { "--profile-depth" })
    int profileDepth = 4;

    @Option(names = { "--profile-interval" })
    int profileInterval = 10;

    @Option(names = { "--profile-thread-group" })
    boolean profileThreadGroup = false;

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
        System.out.println("  --mode <activity|device|host|jvm>: specify which environment to run in.");
        System.out.println("      activity: runs in an Android application on a device or emulator");
        System.out.println("      device: runs in a Dalvik VM on a device or emulator");
        System.out.println("      host: runs in a Dalvik VM on the local desktop built with any lunch combo.");
        System.out.println("      jvm: runs in a Java VM on the local desktop");
        System.out.println("      Default is: " + mode);
        System.out.println();
        System.out.println("  --ssh <host:port>: target a remote machine via SSH.");
        System.out.println();
        System.out.println("  --clean: synonym for --clean-before and --clean-after (default).");
        System.out.println("      Disable with --no-clean if you want no files removed.");
        System.out.println();
        System.out.println("  --stream: stream output as it is emitted.");
        System.out.println();
        System.out.println("  --benchmark: for use with dalvikvm, this dexes all files together,");
        System.out.println("      and is mandatory for running Caliper benchmarks, and a good idea");
        System.out.println("      other performance sensitive code.");
        System.out.println();
        System.out.println("  --profile: run with a profiler to produce an hprof file.");
        System.out.println();
        System.out.println("  --profile-binary: produce a binary hprof file instead of the default ASCII.");
        System.out.println();
        System.out.println("  --profile-file <filename>: filename for hprof profile data.");
        System.out.println("      Default is java.hprof.txt in ASCII mode and java.hprof in binary mode.");
        System.out.println();
        System.out.println("  --profile-depth <count>: number of frames in profile stack traces.");
        System.out.println("      Default is: " + profileDepth);
        System.out.println();
        System.out.println("  --profile-interval <milliseconds>: interval between profile samples.");
        System.out.println("      Default is: " + profileInterval);
        System.out.println();
        System.out.println("  --profile-thread-group: profile thread group instead of single thread in dalvikvms");
        System.out.println("      Note --mode jvm only supports full VM profiling.");
        System.out.println("      Default is: " + profileThreadGroup);
        System.out.println();
        System.out.println("  --invoke-with: provide a command to invoke the VM with. Examples:");
        System.out.println("      --mode host --invoke-with \"valgrind --leak-check=full\"");
        System.out.println("      --mode device --invoke-with \"strace -f -o/sdcard/strace.txt\"");
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
        System.out.println("      configuration information, caches, saved and results");
        System.out.println("      unless they've been put explicitly elsewhere.");
        System.out.println("      Default is: " + vogarDir);
        System.out.println();
        System.out.println("  --record-results: record test results for future comparison.");
        System.out.println();
        System.out.println("  --results-dir <directory>: read and write (if --record-results used)");
        System.out.println("      results from and to this directory.");
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
        System.out.println("  --ansi: use ANSI escape sequences to remove intermediate output.");
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
        System.out.println("  --open-bugs-command <command>: a command that will take bug IDs as parameters");
        System.out.println("      and return those bugs that are still open. For example, if bugs 123 and");
        System.out.println("      789 are both open, the command should echo those values:");
        System.out.println("         $ ~/bin/bug-command 123 456 789");
        System.out.println("         123");
        System.out.println("         789");
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

        // disable timeout when benchmarking or debugging
        if (benchmark || debugPort != null) {
            timeoutSeconds = 0;
        }

        if (firstMonitorPort == -1) {
            firstMonitorPort = mode.isHost() ? 8788 : 8787;
        }

        if (profileFile == null) {
            profileFile = new File(profileBinary ? "java.hprof" : "java.hprof.txt");
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

        return true;
    }

    private boolean run() throws IOException {
        Run run = new Run(this);
        return run.driver.buildAndRun(actionFiles, actionClassesAndPackages);
    }

    public static void main(String[] args) throws IOException {
        Vogar vogar = new Vogar();
        if (!vogar.parseArgs(args)) {
            vogar.printUsage();
            return;
        }
        boolean allSuccess = vogar.run();
        System.exit(allSuccess ? 0 : 1);
    }
}
