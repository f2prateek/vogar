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

package vogar.android;

import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vogar.Action;
import vogar.Classpath;
import vogar.Mode;
import vogar.Run;
import vogar.commands.VmCommandBuilder;
import vogar.tasks.Task;

/**
 * Execute actions on a Dalvik VM using an Android device or emulator.
 */
public class DeviceDalvikVm extends Mode {
    public DeviceDalvikVm(Run run) {
        super(run);
    }

    @Override protected Set<Task> installTasks() {
        Set<Task> result = new HashSet<Task>();
        // dex everything on the classpath and push it to the device.
        for (File classpathElement : run.classpath.getElements()) {
            dexAndPush(result, run.androidSdk.basenameOfJar(classpathElement),
                    classpathElement, null);
        }
        return result;
    }

    @Override public Task prepareUserDirTask(Action action) {
        return new PrepareUserDirTask(run.androidSdk, action);
    }

    @Override public Set<Task> installActionTasks(Action action, File jar) {
        Set<Task> result = new HashSet<Task>();
        dexAndPush(result, action.getName(), jar, action);
        return result;
    }

    private void dexAndPush(Set<Task> tasks, final String name,
            final File jar, final Action action) {
        File localDex = run.localFile(name, name + ".dx.jar");
        File deviceDex = run.deviceDexFile(name);
        Task dex = new DexTask(run.androidSdk, run.classpath, run.benchmark, name, jar, action,
                localDex);
        tasks.add(dex);
        tasks.add(new InstallDexOnDeviceTask(run.androidSdk, localDex, deviceDex)
                .afterSuccess(dex));
    }

    @Override public VmCommandBuilder newVmCommandBuilder(Action action, File workingDirectory) {
        List<String> vmCommand = new ArrayList<String>();
        vmCommand.addAll(run.androidSdk.deviceProcessPrefix(workingDirectory));
        vmCommand.add(run.getAndroidData());
        Iterables.addAll(vmCommand, invokeWith());
        vmCommand.add("dalvikvm");

        // If you edit this, see also HostDalvikVm...
        VmCommandBuilder vmCommandBuilder = new VmCommandBuilder(run.log)
                .vmCommand(vmCommand)
                .vmArgs("-Duser.home=" + run.deviceUserHome)
                .vmArgs("-Duser.name=" + run.androidSdk.getDeviceUserName())
                .vmArgs("-Duser.language=en")
                .vmArgs("-Duser.region=US")
                .maxLength(1024);
        if (!run.benchmark) {
            vmCommandBuilder.vmArgs("-Xverify:none");
            vmCommandBuilder.vmArgs("-Xdexopt:none");
            vmCommandBuilder.vmArgs("-Xcheck:jni");
        }
        // dalvikvm defaults to no limit, but the framework sets the limit at 2000.
        vmCommandBuilder.vmArgs("-Xjnigreflimit:2000");
        return vmCommandBuilder;
    }

    @Override public Task retrieveFilesTask(Action action) {
        return run.environmentDevice.retrieveFilesTask(action);
    }

    @Override public Set<Task> cleanupTasks(Action action) {
        return Collections.singleton(run.environmentDevice.cleanupTask(action));
    }

    @Override public Classpath getRuntimeClasspath(Action action) {
        Classpath result = new Classpath();
        result.addAll(run.deviceDexFile(action.getName()));
        if (!run.benchmark) {
            for (File classpathElement : run.classpath.getElements()) {
                result.addAll(run.deviceDexFile(run.androidSdk.basenameOfJar(classpathElement)));
            }
        }
        return result;
    }
}
