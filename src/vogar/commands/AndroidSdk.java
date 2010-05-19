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

package vogar.commands;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import vogar.Classpath;
import vogar.Console;
import vogar.Md5Cache;
import vogar.Strings;

/**
 * Android SDK commands such as adb, aapt and dx.
 */
public class AndroidSdk {
    private static final Md5Cache DEX_CACHE = new Md5Cache("dex");

    private static final Comparator<File> ORDER_BY_NAME = new Comparator<File>() {
        public int compare(File a, File b) {
            // TODO: this should be a numeric comparison, but we don't need to worry until version 10.
            return a.getName().compareTo(b.getName());
        }
    };

    /** A list of generic names that we avoid when naming generated files. */
    private static final Set<String> BANNED_NAMES = new HashSet<String>();
    static {
        BANNED_NAMES.add("classes");
        BANNED_NAMES.add("javalib");
    }

    private final File androidClasses;
    private final File androidToolsDir;

    private AndroidSdk(File androidClasses, File androidToolsDir) {
        this.androidClasses = androidClasses;
        this.androidToolsDir = androidToolsDir;
    }

    public static AndroidSdk getFromPath() {
        List<String> path = new Command("which", "adb").execute();
        if (path.isEmpty()) {
            throw new RuntimeException("Adb not found");
        }
        File adb = new File(path.get(0)).getAbsoluteFile();
        String parentFileName = adb.getParentFile().getName();

        /*
         * We probably get adb from either a copy of the Android SDK or a copy
         * of the Android source code.
         *
         * Android SDK:
         *  <sdk>/tools/adb
         *  <sdk>/platforms/android-?/android.jar
         *
         * Android build tree:
         *  <source>/out/host/linux-x86/bin/adb
         *  <source>/out/target/common/obj/JAVA_LIBRARIES/core_intermediates/classes.jar
         */

        if ("tools".equals(parentFileName)) {
            File sdkRoot = adb.getParentFile().getParentFile();
            Console.getInstance().verbose("using android sdk: " + sdkRoot);

            List<File> platforms = Arrays.asList(new File(sdkRoot, "platforms").listFiles());
            Collections.sort(platforms, ORDER_BY_NAME);
            File newestPlatform = platforms.get(platforms.size() - 1);
            Console.getInstance().verbose("using android platform: " + newestPlatform);

            return new AndroidSdk(new File(newestPlatform, "android.jar"), new File(newestPlatform, "tools"));

        } else if ("bin".equals(parentFileName)) {
            File sourceRoot = adb.getParentFile().getParentFile()
                    .getParentFile().getParentFile().getParentFile();
            Console.getInstance().verbose("using android build tree: " + sourceRoot);
            File coreClasses = new File(sourceRoot
                    + "/out/target/common/obj/JAVA_LIBRARIES/core_intermediates/classes.jar");
            return new AndroidSdk(coreClasses, null);

        } else {
            throw new RuntimeException("Couldn't derive Android home from " + adb);
        }
    }

    public static Collection<File> defaultExpectations() {
        File[] files = new File("libcore/expectations").listFiles();
        return (files != null) ? Arrays.asList(files) : Collections.<File>emptyList();
    }

    public File getAndroidClasses() {
        return androidClasses;
    }

    /**
     * Returns the path to a version-specific tool.
     *
     * An SDK has two tools directories: a version-independent one containing "adb" and a few
     * others, plus a version-specific one containing "dx" and a few others.
     *
     * If you're running from an Android build tree, everything is already on your path.
     */
    private String toolPath(String tool) {
        return (androidToolsDir != null) ? new File(androidToolsDir, tool).toString() : tool;
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
        output.getParentFile().mkdirs();
        File key = DEX_CACHE.makeKey(classpath);
        if (key != null && key.exists()) {
            Console.getInstance().verbose("dex cache hit for " + classpath);
            new Command.Builder().args("cp", key, output).execute();
            return;
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
        new Command.Builder()
                .args(toolPath("dx"))
                .args("-JXms16M")
                .args("-JXmx1536M")
                .args("--dex")
                .args("--output=" + output)
                .args("--core-library")
                .args(Strings.objectsToStrings(classpath.getElements()))
                .execute();
        DEX_CACHE.insert(key, output);
    }

    public void packageApk(File apk, File manifest) {
        new Command(toolPath("aapt"),
                "package",
                "-F", apk.getPath(),
                "-M", manifest.getPath(),
                "-I", androidClasses.getPath())
                .execute();
    }

    public void addToApk(File apk, File dex) {
        new Command(toolPath("aapt"), "add", "-k", apk.getPath(), dex.getPath()).execute();
    }

    public void mkdir(File name) {
        new Command("adb", "shell", "mkdir", name.getPath()).execute();
    }

    public void rm(File name) {
        new Command("adb", "shell", "rm", "-r", name.getPath()).execute();
    }

    public void push(File local, File remote) {
        new Command("adb", "push", local.getPath(), remote.getPath()).execute();
    }

    public void install(File apk) {
        new Command("adb", "install", "-r", apk.getPath()).execute();
    }

    public void uninstall(String packageName) {
        new Command("adb", "uninstall", packageName).execute();
    }

    public void forwardTcp(int localPort, int devicePort) {
        new Command("adb", "forward", "tcp:" + localPort, "tcp:" + devicePort).execute();
    }

    public void waitForDevice() {
        new Command("adb", "wait-for-device").execute();
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
            Command command = new Command("adb", "shell", "ls", pathArgument);
            List<String> output;
            try {
                output = command.executeWithTimeout(remainingSeconds);
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out after " + timeoutSeconds +
                                           " seconds waiting for file " + path, e);
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
