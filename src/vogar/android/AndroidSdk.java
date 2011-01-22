/*
 * Copyright (C) 2010 The Android Open Source Project
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

package vogar.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vogar.Classpath;
import vogar.HostFileCache;
import vogar.Log;
import vogar.Md5Cache;
import vogar.commands.Command;
import vogar.commands.CommandFailedException;
import vogar.commands.Mkdir;
import vogar.util.Strings;

/**
 * Android SDK commands such as adb, aapt and dx.
 */
public class AndroidSdk {

    // $BOOTCLASSPATH defined by system/core/rootdir/init.rc
    public static final String[] BOOTCLASSPATH = new String[] { "core",
                                                                "ext",
                                                                "framework",
                                                                "android.policy",
                                                                "services",
                                                                "core-junit",
                                                                "bouncycastle" };


    public static final String[] HOST_BOOTCLASSPATH = new String[] {
            "core-hostdex",
            "bouncycastle-hostdex",
            "apache-xml-hostdex",
    };

    private static final Comparator<File> ORDER_BY_NAME = new Comparator<File>() {
        public int compare(File a, File b) {
            // TODO: this should be a numeric comparison, but we don't
            // need to worry until version 10.
            return a.getName().compareTo(b.getName());
        }
    };

    /** A list of generic names that we avoid when naming generated files. */
    private static final Set<String> BANNED_NAMES = new HashSet<String>();
    static {
        BANNED_NAMES.add("classes");
        BANNED_NAMES.add("javalib");
    }

    private final Log log;
    private final Mkdir mkdir;
    private final File[] androidClasses;
    private final String dx;
    private final String aapt;

    private Md5Cache dexCache;
    private Md5Cache pushCache;
    private final Set<File> mkdirCache = new HashSet<File>();


    public static Collection<File> defaultExpectations() {
        File[] files = new File("libcore/expectations").listFiles(new FilenameFilter() {
            // ignore obviously temporary files
            public boolean accept(File dir, String name) {
                return !name.endsWith("~") && !name.startsWith(".");
            }
        });
        return (files != null) ? Arrays.asList(files) : Collections.<File>emptyList();
    }

    public AndroidSdk(Log log, Mkdir mkdir) {
        this.log = log;
        this.mkdir = mkdir;

        List<String> path = new Command(log, "which", "adb").execute();
        if (path.isEmpty()) {
            throw new RuntimeException("Adb not found");
        }
        File adb = new File(path.get(0)).getAbsoluteFile();
        String parentFileName = adb.getParentFile().getName();

        /*
         * We probably get adb from either a copy of the Android SDK or a copy
         * of the Android source code.
         *
         * Android SDK < v9 (gingerbread):
         *  <sdk>/tools/adb
         *  <sdk>/platforms/android-?/tools/dx
         *  <sdk>/platforms/android-?/tools/aapt
         *  <sdk>/platforms/android-?/android.jar
         *
         * Android SDK >= v9 (gingerbread):
         *  <sdk>/platform-tools/adb
         *  <sdk>/platform-tools/dx
         *  <sdk>/platform-tools/aapt
         *  <sdk>/platforms/android-?/android.jar
         *
         * Android build tree:
         *  <source>/out/host/linux-x86/bin/adb
         *  <source>/out/target/common/obj/JAVA_LIBRARIES/core_intermediates/classes.jar
         */

        if ("tools".equals(parentFileName) || "platform-tools".equals(parentFileName)) {
            File sdkRoot = adb.getParentFile().getParentFile();
            List<File> platforms = Arrays.asList(new File(sdkRoot, "platforms").listFiles());
            Collections.sort(platforms, ORDER_BY_NAME);
            File newestPlatform = platforms.get(platforms.size() - 1);
            log.verbose("using android platform: " + newestPlatform);

            // don't assume dx and aapt are on the $PATH
            if ("tools".equals(parentFileName)) {
                dx = newestPlatform.getPath() + "/tools/dx";
                aapt = newestPlatform.getPath() + "/tools/aapt";
            } else {
                dx = "dx";
                aapt = "aapt";
            }

            androidClasses = new File[] { new File(newestPlatform, "android.jar") };
            log.verbose("using android sdk: " + sdkRoot);

        } else if ("bin".equals(parentFileName)) {
            dx = "dx";
            aapt = "aapt";

            File sourceRoot = adb.getParentFile().getParentFile()
                    .getParentFile().getParentFile().getParentFile();
            log.verbose("using android build tree: " + sourceRoot);

            androidClasses = new File[BOOTCLASSPATH.length];
            for (int i = 0; i < BOOTCLASSPATH.length; i++) {
                String jar = BOOTCLASSPATH[i];
                androidClasses[i] = new File(sourceRoot,
                                             "out/target/common/obj/JAVA_LIBRARIES/"
                                             + jar + "_intermediates/classes.jar");
            }
        } else {
            throw new RuntimeException("Couldn't derive Android home from " + adb);
        }
    }

    public static Collection<File> defaultSourcePath() {
        File supportSrc = new File("libcore/support/src/test/java");
        return supportSrc.exists() ? Arrays.asList(supportSrc) : Collections.<File>emptyList();
    }

    public File[] getAndroidClasses() {
        return androidClasses;
    }

    public void setCaches(HostFileCache hostFileCache, DeviceFileCache deviceCache) {
        this.dexCache = new Md5Cache(log, "dex", hostFileCache);
        this.pushCache = new Md5Cache(log, "pushed", deviceCache);
    }

    /**
     * Returns a recognizable readable name for the given generated .jar file,
     * appropriate for use in naming derived files.
     *
     * @param file a product of the android build system, such as
     *     "out/core_intermediates/javalib.jar".
     * @return a recognizable base name like "core_intermediates".
     */
    public String basenameOfJar(File file) {
        String name = file.getName().replaceAll("\\.jar$", "");
        while (BANNED_NAMES.contains(name)) {
            file = file.getParentFile();
            name = file.getName();
        }
        return name;
    }

    /**
     * Converts all the .class files on 'classpath' into a dex file written to 'output'.
     */
    public void dex(File output, Classpath classpath) {
        mkdir.mkdirs(output.getParentFile());

        String key = dexCache.makeKey(classpath);
        if (key != null) {
            boolean cacheHit = dexCache.getFromCache(output, key);
            if (cacheHit) {
                log.verbose("dex cache hit for " + classpath);
                return;
            }
        }

        /*
         * We pass --core-library so that we can write tests in the
         * same package they're testing, even when that's a core
         * library package. If you're actually just using this tool to
         * execute arbitrary code, this has the unfortunate
         * side-effect of preventing "dx" from protecting you from
         * yourself.
         *
         * Memory options pulled from build/core/definitions.mk to
         * handle large dx input when building dex for APK.
         */
        new Command.Builder(log)
                .args(dx)
                .args("-JXms16M")
                .args("-JXmx1536M")
                .args("--dex")
                .args("--output=" + output)
                .args("--core-library")
                .args((Object[]) Strings.objectsToStrings(classpath.getElements())).execute();
        dexCache.insert(key, output);
    }

    public void packageApk(File apk, File manifest) {
        List<String> aapt = new ArrayList<String>(Arrays.asList(this.aapt,
                                                                "package",
                                                                "-F", apk.getPath(),
                                                                "-M", manifest.getPath()));
        for (File jar : androidClasses) {
            aapt.add("-I");
            aapt.add(jar.getPath());
        }
        new Command(log, aapt).execute();
    }

    public void addToApk(File apk, File dex) {
        new Command(log, this.aapt, "add", "-k", apk.getPath(), dex.getPath()).execute();
    }

    public void mkdir(File name) {
        // to reduce adb traffic, only try to make a directory if we haven't tried before.
        if (mkdirCache.contains(name)) {
            return;
        }
        List<String> args = Arrays.asList("adb", "shell", "mkdir", name.getPath());
        List<String> rawResult = new Command(log, args).execute();
        // fail if this failed for any reason other than the file existing.
        if (!rawResult.isEmpty() && !rawResult.get(0).contains("File exists")) {
            throw new CommandFailedException(args, rawResult);
        }
        mkdirCache.add(name);
    }

    public void mkdirs(File name) {
        LinkedList<File> directoryStack = new LinkedList<File>();
        File dir = name;
        // Do some directory bootstrapping since "mkdir -p" doesn't work in adb shell. Don't bother
        // trying to create /sdcard or /. This might reach dir == null if given a relative path,
        // otherwise it should terminate with "/sdcard" or "/".
        while (dir != null && !dir.getPath().equals("/sdcard") && !dir.getPath().equals("/")) {
            directoryStack.addFirst(dir);
            dir = dir.getParentFile();
        }
        // would love to do "adb shell mkdir DIR1 DIR2 DIR3 ..." but unfortunately this will stop
        // if any of the directories fail to be created (even for a reason like "file exists"), so
        // they have to be created one by one.
        for (File createDir : directoryStack) {
            mkdir(createDir);
        }
    }

    public void mv(File source, File destination) {
        new Command(log, "adb", "shell", "mv", source.getPath(), destination.getPath()).execute();
    }

    public void rm(File name) {
        new Command(log, "adb", "shell", "rm", "-r", name.getPath()).execute();
    }

    public void cp(File source, File destination) {
        // adb doesn't support "cp" command directly
        new Command(log, "adb", "shell", "cat", source.getPath(), ">", destination.getPath())
                .execute();
    }

    public String getDeviceUserName() {
        // The default environment doesn't include $USER, so dalvikvm doesn't set "user.name".
        // DeviceDalvikVm uses this to set "user.name" manually with -D.
        String line = new Command(log, "adb", "shell", "id").execute().get(0);
        Matcher m = Pattern.compile("uid=\\d+\\((\\S+)\\) gid=\\d+\\(\\S+\\)").matcher(line);
        return m.matches() ? m.group(1) : "root";
    }

    public Set<File> ls(File dir) throws FileNotFoundException {
        List<String> rawResult = new Command(log, "adb", "shell", "ls", dir.getPath()).execute();
        Set<File> files = new HashSet<File>();
        for (String fileString : rawResult) {
            if (fileString.equals(dir.getPath() + ": No such file or directory")) {
                throw new FileNotFoundException("File or directory " + dir + " not found.");
            }
            if (fileString.equals(dir.getPath())) {
                // The argument must have been a file or symlink, not a directory
                files.add(dir);
            } else {
                files.add(new File(dir, fileString));
            }
        }
        return files;
    }

    public void pull(File remote, File local) {
        new Command(log, "adb", "pull", remote.getPath(), local.getPath()).execute();
    }

    public void push(File local, File remote) {
        Command fallback = new Command(log, "adb", "push", local.getPath(), remote.getPath());
        mkdirs(remote.getParentFile());
        // don't yet cache directories (only used by jtreg tests)
        if (pushCache != null && local.isFile()) {
            String key = pushCache.makeKey(local);
            boolean cacheHit = pushCache.getFromCache(remote, key);
            if (cacheHit) {
                log.verbose("device cache hit for " + local);
                return;
            }
            fallback.execute();
            pushCache.insert(key, remote);
        } else {
            fallback.execute();
        }
    }

    public void install(File apk) {
        new Command(log, "adb", "install", "-r", apk.getPath()).execute();
    }

    public void uninstall(String packageName) {
        new Command(log, "adb", "uninstall", packageName).execute();
    }

    public void forwardTcp(int localPort, int devicePort) {
        new Command(log, "adb", "forward", "tcp:" + localPort, "tcp:" + devicePort).execute();
    }

    public void remount() {
        new Command(log, "adb", "remount").execute();
    }

    public void waitForDevice() {
        new Command(log, "adb", "wait-for-device").execute();
    }

    /**
     * Loop until we see a non-empty directory on the device. For
     * example, wait until /sdcard is mounted.
     */
    public void waitForNonEmptyDirectory(File path, int timeoutSeconds) {
        waitFor(false, path, timeoutSeconds);
    }

    private void waitFor(boolean file, File path, int timeoutSeconds) {
        final int millisPerSecond = 1000;
        final long start = System.currentTimeMillis();
        final long deadline = start + (millisPerSecond * timeoutSeconds);

        while (true) {
            final int remainingSeconds =
                    (int) ((deadline - System.currentTimeMillis()) / millisPerSecond);
            String pathArgument = path.getPath();
            if (!file) {
                pathArgument += "/";
            }
            Command command = new Command(log, "adb", "shell", "ls", pathArgument);
            List<String> output;
            try {
                output = command.executeWithTimeout(remainingSeconds);
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out after " + timeoutSeconds
                                           + " seconds waiting for file " + path, e);
            }
            try {
                Thread.sleep(millisPerSecond);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (file) {
                // for files, we expect one line of output that matches the filename
                if (output.size() == 1 && output.get(0).equals(path.getPath())) {
                    return;
                }
            } else {
                // for a non empty directory, we just want any output
                if (!output.isEmpty()) {
                    return;
                }
            }
        }
    }
}
