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

import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import vogar.Action;
import vogar.Classpath;
import vogar.Mode;
import vogar.Result;
import vogar.Run;
import vogar.commands.VmCommandBuilder;
import vogar.tasks.PrepareUserDirTask;
import vogar.tasks.RetrieveFilesTask;
import vogar.tasks.Task;

/**
 * Executes actions on a Dalvik VM on a Linux desktop.
 */
public class HostDalvikVm extends Mode {
    private String buildRoot;

    public HostDalvikVm(Run run) {
        super(run);
    }

    public File dalvikCache() {
        return run.localFile("android-data", "dalvik-cache");
    }

    @Override protected Set<Task> installTasks() {
        return Collections.<Task>singleton(new Task("install runner") {
            @Override protected Result execute() throws Exception {
                // dex everything on the classpath
                for (File classpathElement : run.classpath.getElements()) {
                    run.androidSdk.dex(nameDexFile(run.androidSdk.basenameOfJar(classpathElement)),
                            Classpath.of(classpathElement));
                }
                run.mkdir.mkdirs(dalvikCache());
                buildRoot = System.getenv("ANDROID_BUILD_TOP");
                return Result.SUCCESS;
            }
        });
    }

    @Override public Task retrieveFilesTask(Action action) {
        return new RetrieveFilesTask(run, new File("./vogar-results"),
                action.getUserDir(), run.retrievedFiles);
    }

    @Override public Set<Task> cleanupTasks(Action action) {
        return Collections.emptySet();
    }

    private File nameDexFile(String name) {
        return run.localFile(name, name + ".dx.jar");
    }

    @Override public Task prepareUserDirTask(Action action) {
        return new PrepareUserDirTask(run.log, run.mkdir, action);
    }

    @Override public Set<Task> installActionTasks(Action action, File jar) {
        return Collections.<Task>singleton(new DexTask(run.androidSdk, Classpath.of(jar),
                run.benchmark, action.getName(), jar, action, nameDexFile(action.getName())));
    }

    @Override public VmCommandBuilder newVmCommandBuilder(Action action, File workingDirectory) {
        List<File> jars = new ArrayList<File>();
        for (String jar : AndroidSdk.HOST_BOOTCLASSPATH) {
            jars.add(new File(buildRoot, "out/host/linux-x86/framework/" + jar + ".jar"));
        }
        Classpath bootClasspath = Classpath.of(jars);

        VmCommandBuilder builder = new VmCommandBuilder(run.log)
                .userDir(workingDirectory)
                .env("ANDROID_PRINTF_LOG", "tag")
                .env("ANDROID_LOG_TAGS", "*:i")
                .env("ANDROID_DATA", dalvikCache().getParent());

        List<String> vmCommand = new ArrayList<String>();
        Iterables.addAll(vmCommand, invokeWith());

        if (run.hostBuild) {
            vmCommand.add(buildRoot + "/out/host/linux-x86/bin/dalvikvm");
            builder.env("ANDROID_ROOT", buildRoot + "/out/host/linux-x86")
                    .env("LD_LIBRARY_PATH", buildRoot + "/out/host/linux-x86/lib")
                    .env("DYLD_LIBRARY_PATH", buildRoot + "/out/host/linux-x86/lib");
        } else {
            String base = System.getenv("OUT");
            vmCommand.add(base + "/system/bin/dalvikvm");
            builder.env("ANDROID_ROOT", base + "/system")
                    .env("LD_LIBRARY_PATH", base + "/system/lib")
                    .env("DYLD_LIBRARY_PATH", base + "/system/lib");
        }

        // If you edit this, see also DeviceDalvikVm...
        builder.vmCommand(vmCommand)
                .vmArgs("-Xbootclasspath:" + bootClasspath.toString())
                .vmArgs("-Duser.language=en")
                .vmArgs("-Duser.region=US");
        if (!run.benchmark) {
            builder.vmArgs("-Xverify:none");
            builder.vmArgs("-Xdexopt:none");
            builder.vmArgs("-Xcheck:jni");
        }
        // dalvikvm defaults to no limit, but the framework sets the limit at 2000.
        builder.vmArgs("-Xjnigreflimit:2000");
        return builder;
    }

    @Override public Classpath getRuntimeClasspath(Action action) {
        Classpath result = new Classpath();
        result.addAll(nameDexFile(action.getName()));
        for (File classpathElement : run.classpath.getElements()) {
            result.addAll(nameDexFile(run.androidSdk.basenameOfJar(classpathElement)));
        }
        return result;
    }
}
