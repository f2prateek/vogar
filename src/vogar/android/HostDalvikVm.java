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

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import vogar.Action;
import vogar.Classpath;
import vogar.Console;
import vogar.Environment;
import vogar.Mode;
import vogar.Vm;
import vogar.commands.Mkdir;

/**
 * Executes actions on a Dalvik VM on a Linux desktop.
 */
public class HostDalvikVm extends Vm {

    private final AndroidSdk androidSdk;
    private final File dalvikCache;
    private final String invokeWith;
    private String base;
    private String buildRoot;
    private boolean hostBuild;

    public HostDalvikVm(Environment environment, Mode.Options options, Options vmOptions,
            AndroidSdk androidSdk, String invokeWith, boolean hostBuild) {
        super(environment, options, vmOptions);
        this.androidSdk = androidSdk;
        this.dalvikCache = environment.file("android-data", "dalvik-cache");
        this.invokeWith = invokeWith;
        this.hostBuild = hostBuild;
    }

    @Override protected void installRunner() {
        // dex everything on the classpath
        for (File classpathElement : classpath.getElements()) {
            dex(androidSdk.basenameOfJar(classpathElement), classpathElement);
        }

        new Mkdir().mkdirs(dalvikCache);
        base = System.getenv("OUT");
        buildRoot = System.getenv("ANDROID_BUILD_TOP");
        Console.getInstance().verbose("dalvikvm base directory: " + base);
    }

    private File nameDexFile(String name) {
        return environment.file(name, name + ".dx.jar");
    }

    @Override protected void postCompile(Action action, File jar) {
        dex(action.getName(), jar);
    }

    private void dex(String name, File jar) {
        Console.getInstance().verbose("dex " + name);
        androidSdk.dex(nameDexFile(name), Classpath.of(jar));
    }

    @Override protected VmCommandBuilder newVmCommandBuilder(File workingDirectory) {
        List<File> jars = new ArrayList<File>();
        for (String jar : AndroidSdk.BOOTCLASSPATH) {
            jars.add(new File(base, "system/framework/" + jar + ".jar"));
        }
        Classpath bootClasspath = Classpath.of(jars);

        VmCommandBuilder builder = new VmCommandBuilder()
                .workingDir(workingDirectory)
                .temp(workingDirectory)
                .env("ANDROID_PRINTF_LOG", "tag")
                .env("ANDROID_LOG_TAGS", "*:w")
                .env("ANDROID_DATA", dalvikCache.getParent());

        List<String> vmCommand = new ArrayList<String>();
        if (invokeWith != null) {
            Iterables.addAll(vmCommand, Splitter.onPattern("\\s+").omitEmptyStrings().split(invokeWith));
        }

        if (hostBuild) {
            vmCommand.add(buildRoot + "/out/host/linux-x86/bin/dalvikvm");
            builder.env("ANDROID_ROOT", buildRoot + "/out/host/linux-x86")
                    .env("LD_LIBRARY_PATH", buildRoot + "/out/host/linux-x86/lib")
                    .env("DYLD_LIBRARY_PATH", buildRoot + "/out/host/linux-x86/lib");
        } else {
            vmCommand.add(base + "/system/bin/dalvikvm");
            builder.env("ANDROID_ROOT", base + "/system")
                    .env("LD_LIBRARY_PATH", base + "/system/lib")
                    .env("DYLD_LIBRARY_PATH", base + "/system/lib");
        }

        builder.vmCommand(vmCommand)
                .vmArgs("-Xbootclasspath:" + bootClasspath.toString())
                .vmArgs("-Duser.home=" + workingDirectory)
                .vmArgs("-Duser.language=en")
                .vmArgs("-Duser.region=US")
                .vmArgs("-Djavax.net.ssl.trustStore=" + base + "/system/etc/security/cacerts.bks");
        return builder;
    }

    @Override protected Classpath getRuntimeClasspath(Action action) {
        Classpath result = new Classpath();
        result.addAll(nameDexFile(action.getName()));
        for (File classpathElement : classpath.getElements()) {
            result.addAll(nameDexFile(androidSdk.basenameOfJar(classpathElement)));
        }
        return result;
    }
}
