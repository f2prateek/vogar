/*
 * Copyright (C) 2011 The Android Open Source Project
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
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import vogar.android.ActivityMode;
import vogar.android.AndroidSdk;
import vogar.android.AppProcessMode;
import vogar.android.DeviceDalvikVm;
import vogar.android.DeviceFileCache;
import vogar.android.EnvironmentDevice;
import vogar.android.HostDalvikVm;
import vogar.commands.Mkdir;
import vogar.commands.Rm;
import vogar.tasks.TaskQueue;
import vogar.util.Strings;

public final class Run {
    public final boolean hostBuild;
    public final File xmlReportsDirectory;
    public final File resultsDir;
    public final boolean recordResults;
    public final ExpectationStore expectationStore;
    public final Date date;
    public final String invokeWith;
    public final File keystore;
    public final Log log;
    public final Classpath classpath;
    public final Classpath buildClasspath;
    public final List<File> sourcepath;
    public final Mkdir mkdir;
    public final Rm rm;
    public final int firstMonitorPort;
    public final int timeoutSeconds;
    public final boolean profile;
    public final boolean profileBinary;
    public final int profileDepth;
    public final int profileInterval;
    public final boolean profileThreadGroup;
    public final File profileFile;
    public final File javaHome;
    public final Integer debugPort;
    public final List<String> javacArgs;
    public final boolean benchmark;
    public final File runnerDir;
    public final boolean cleanBefore;
    public final boolean cleanAfter;
    public final File localTemp;
    public final int numRunners;
    public final File deviceUserHome;
    public final Console console;
    public final int smallTimeoutSeconds;
    public final List<String> additionalVmArgs;
    public final List<String> targetArgs;
    public final boolean useBootClasspath;
    public final int largeTimeoutSeconds;
    public final RetrievedFilesFilter retrievedFiles;
    public final Driver driver;
    public final Mode mode;
    public final Environment environment;
    public final EnvironmentDevice environmentDevice;
    public final AndroidSdk androidSdk;
    public final XmlReportPrinter reportPrinter;
    public final JarSuggestions jarSuggestions;
    public final ClassFileIndex classFileIndex;
    public final OutcomeStore outcomeStore;
    public final TaskQueue taskQueue;

    public Run(Vogar vogar) throws IOException {
        this.console = vogar.stream
                ? new Console.StreamingConsole()
                : new Console.MultiplexingConsole();
        console.setUseColor(vogar.color, vogar.passColor, vogar.warnColor, vogar.failColor);
        console.setAnsi(vogar.ansi);
        console.setIndent(vogar.indent);
        console.setVerbose(vogar.verbose);
        this.localTemp = new File("/tmp/vogar/" + UUID.randomUUID());
        this.log = console;
        this.additionalVmArgs = vogar.vmArgs;
        this.benchmark = vogar.benchmark;
        this.cleanBefore = vogar.cleanBefore;
        this.cleanAfter = vogar.cleanAfter;
        this.date = new Date();
        this.debugPort = vogar.debugPort;
        this.deviceUserHome = new File(vogar.deviceDir, "user.home");
        this.mkdir = new Mkdir(console);
        this.rm = new Rm(console);
        this.firstMonitorPort = vogar.firstMonitorPort;
        this.hostBuild = vogar.mode == ModeId.HOST;
        this.invokeWith = vogar.invokeWith;
        this.javacArgs = vogar.javacArgs;
        this.javaHome = vogar.javaHome;
        this.largeTimeoutSeconds = vogar.timeoutSeconds * Vogar.LARGE_TIMEOUT_MULTIPLIER;
        this.numRunners = (vogar.stream || vogar.mode == ModeId.ACTIVITY)
                    ? 1
                    : Vogar.NUM_PROCESSORS;
        this.timeoutSeconds = vogar.timeoutSeconds;
        this.smallTimeoutSeconds = vogar.timeoutSeconds;
        this.sourcepath = vogar.sourcepath;
        this.useBootClasspath = vogar.useBootClasspath;
        this.targetArgs = vogar.targetArgs;
        this.xmlReportsDirectory = vogar.xmlReportsDirectory;
        this.profile = vogar.profile;
        this.profileBinary = vogar.profileBinary;
        this.profileFile = vogar.profileFile;
        this.profileDepth = vogar.profileDepth;
        this.profileInterval = vogar.profileInterval;
        this.profileThreadGroup = vogar.profileThreadGroup;
        this.recordResults = vogar.recordResults;
        this.resultsDir =  vogar.resultsDir == null
                ? new File(vogar.vogarDir, "results")
                : vogar.resultsDir;
        this.runnerDir = new File(vogar.deviceDir, "run");
        this.keystore = localFile("activity", "vogar.keystore");
        this.classpath = Classpath.of(vogar.classpath);
        this.classpath.addAll(vogarJar());


        androidSdk = new AndroidSdk(log, mkdir, vogar.mode);
        androidSdk.setCaches(new HostFileCache(log, mkdir),
                new DeviceFileCache(log, vogar.deviceDir, androidSdk));

        if (vogar.mode.isHost()) {
            this.environment = new EnvironmentHost(this);
            this.environmentDevice = null;
        } else {
            this.environmentDevice = new EnvironmentDevice(this);
            this.environment = environmentDevice;
        }

        expectationStore = ExpectationStore.parse(console, vogar.expectationFiles, vogar.mode);
        if (vogar.openBugsCommand != null) {
            expectationStore.loadBugStatuses(new CommandBugDatabase(log, vogar.openBugsCommand));
        }

        if (vogar.mode == ModeId.JVM) {
            this.mode = new JavaVm(this);
        } else if (vogar.mode == ModeId.HOST) {
            this.mode = new HostDalvikVm(this);
        } else if (vogar.mode == ModeId.DEVICE) {
            this.mode = new DeviceDalvikVm(this);
        } else if (vogar.mode == ModeId.ACTIVITY) {
            this.mode = new ActivityMode(this);
        } else if (vogar.mode == ModeId.APP_PROCESS) {
            this.mode = new AppProcessMode(this);
        } else {
            throw new IllegalStateException();
        }

        this.buildClasspath = Classpath.of(vogar.buildClasspath);
        if (vogar.mode.requiresAndroidSdk()) {
            buildClasspath.addAll(androidSdk.getAndroidClasses());
        }

        this.classFileIndex = new ClassFileIndex(log, mkdir, vogar.jarSearchDirs);
        if (vogar.suggestClasspaths) {
            classFileIndex.createIndex();
        }

        this.retrievedFiles = new RetrievedFilesFilter(profile, profileFile);
        this.reportPrinter = new XmlReportPrinter(xmlReportsDirectory, expectationStore, date);
        this.jarSuggestions = new JarSuggestions();
        this.outcomeStore = new OutcomeStore(log, mkdir, rm, resultsDir, recordResults,
                expectationStore, date);
        this.driver = new Driver(this);
        this.taskQueue = new TaskQueue(console);
    }

    public final File localFile(Object... path) {
        return new File(localTemp + "/" + Strings.join("/", path));
    }

    private File vogarJar() {
        URL jarUrl = Vogar.class.getResource("/vogar/Vogar.class");
        if (jarUrl == null) {
            // should we add an option for IDE users, to use a user-specified vogar.jar?
            throw new IllegalStateException("Vogar cannot find its own .jar");
        }

        /*
         * Parse a URI like jar:file:/Users/jessewilson/vogar/vogar.jar!/vogar/Vogar.class
         * to yield a .jar file like /Users/jessewilson/vogar/vogar.jar.
         */
        String url = jarUrl.toString();
        int bang = url.indexOf("!");
        String JAR_URI_PREFIX = "jar:file:";
        if (url.startsWith(JAR_URI_PREFIX) && bang != -1) {
            return new File(url.substring(JAR_URI_PREFIX.length(), bang));
        } else {
            throw new IllegalStateException("Vogar cannot find the .jar file in " + jarUrl);
        }
    }

    public final File hostJar(Object nameOrAction) {
        return localFile(nameOrAction, nameOrAction + ".jar");
    }

    /**
     * Returns a path for a Java tool such as java, javac, jar where
     * the Java home is used if present, otherwise assumes it will
     * come from the path.
     */
    public String javaPath(String tool) {
        return (javaHome == null)
            ? tool
            : new File(new File(javaHome, "bin"), tool).getPath();
    }

    public File deviceDexFile(String name) {
        return new File(runnerDir, name + ".jar");
    }

    public File vogarTemp() {
        return new File(runnerDir, "tmp");
    }

    public File dalvikCache() {
        return new File(runnerDir.getParentFile(), "dalvik-cache");
    }

    /**
     * Returns an environment variable assignment to configure where the VM will
     * store its dexopt files. This must be set on production devices and is
     * optional for development devices.
     */
    public String getAndroidData() {
        // The VM wants the parent directory of a directory named "dalvik-cache"
        return "ANDROID_DATA=" + dalvikCache().getParentFile();
    }
}
