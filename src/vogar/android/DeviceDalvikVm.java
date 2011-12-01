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
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.Action;
import vogar.Classpath;
import vogar.Result;
import vogar.Vm;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;

/**
 * Execute actions on a Dalvik VM using an Android device or emulator.
 */
public class DeviceDalvikVm extends Vm {
    @Inject @Named("benchmark") boolean fastMode;
    @Inject @Named("deviceUserHome") File deviceUserHome;

    protected EnvironmentDevice getEnvironmentDevice() {
        return (EnvironmentDevice) environment;
    }

    protected AndroidSdk getSdk() {
        return getEnvironmentDevice().androidSdk;
    }

    @Override protected void installTasks(TaskQueue taskQueue) {
        // dex everything on the classpath and push it to the device.
        for (File classpathElement : classpath.getElements()) {
            dexAndPush(taskQueue, null, getSdk().basenameOfJar(classpathElement),
                    classpathElement, null);
        }
    }

    @Override public Task installActionTask(TaskQueue taskQueue, Task compileTask,
            Action action, File jar) {
        return dexAndPush(taskQueue, compileTask, action.getName(), jar, action);
    }

    private Task dexAndPush(TaskQueue taskQueue, final Task compileTask, final String name,
            final File jar, final Action action) {
        final File localDex = environment.file(name, name + ".dx.jar");
        final Task dex = new Task("dex " + name) {
            @Override protected Result execute() throws Exception {
                // make the local dex (inside a jar)
                Classpath cp = Classpath.of(jar);
                if (fastMode && action != null) {
                    cp.addAll(classpath);
                }
                getSdk().dex(localDex, cp);
                return Result.SUCCESS;
            }
            @Override public boolean isRunnable() {
                return compileTask == null || compileTask.getResult() != null;
            }
        };
        final Task push = new Task("push " + name) {
            @Override protected Result execute() throws Exception {
                if (action != null) {
                    prepareUserDir(action);
                }
                getSdk().push(localDex, deviceDexFile(name));
                return Result.SUCCESS;
            }
            @Override public boolean isRunnable() {
                return getEnvironmentDevice().prepareDeviceTask.getResult() == Result.SUCCESS
                        && dex.getResult() == Result.SUCCESS;
            }
            private void prepareUserDir(Action action) {
                File actionClassesDir = getEnvironmentDevice().actionClassesDirOnDevice(action);
                getSdk().mkdir(actionClassesDir);
                File resourcesDirectory = action.getResourcesDirectory();
                if (resourcesDirectory != null) {
                    getSdk().push(resourcesDirectory, actionClassesDir);
                }
                action.setUserDir(actionClassesDir);
            }
        };
        taskQueue.enqueue(dex);
        taskQueue.enqueue(push);
        return push;
    }

    private File deviceDexFile(String name) {
        return new File(getEnvironmentDevice().runnerDir, name + ".jar");
    }

    @Override protected VmCommandBuilder newVmCommandBuilder(Action action, File workingDirectory) {
        List<String> vmCommand = new ArrayList<String>();
        vmCommand.addAll(getSdk().deviceProcessPrefix(workingDirectory));
        vmCommand.add(getEnvironmentDevice().getAndroidData());
        Iterables.addAll(vmCommand, invokeWith());
        vmCommand.add("dalvikvm");

        // If you edit this, see also HostDalvikVm...
        VmCommandBuilder vmCommandBuilder = new VmCommandBuilder()
                .vmCommand(vmCommand)
                .vmArgs("-Duser.home=" + deviceUserHome)
                .vmArgs("-Duser.name=" + getSdk().getDeviceUserName())
                .vmArgs("-Duser.language=en")
                .vmArgs("-Duser.region=US")
                .maxLength(1024);
        if (!fastMode) {
            vmCommandBuilder.vmArgs("-Xverify:none");
            vmCommandBuilder.vmArgs("-Xdexopt:none");
            vmCommandBuilder.vmArgs("-Xcheck:jni");
        }
        // dalvikvm defaults to no limit, but the framework sets the limit at 2000.
        vmCommandBuilder.vmArgs("-Xjnigreflimit:2000");
        return vmCommandBuilder;
    }

    @Override protected Classpath getRuntimeClasspath(Action action) {
        Classpath result = new Classpath();
        result.addAll(deviceDexFile(action.getName()));
        if (!fastMode) {
            for (File classpathElement : classpath.getElements()) {
                result.addAll(deviceDexFile(getSdk().basenameOfJar(classpathElement)));
            }
        }
        return result;
    }
}
