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

import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.commands.Mkdir;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;

/**
 * A local Java virtual machine like Harmony or the RI.
 */
final class JavaVm extends Vm {
    @Inject JavaVm() {}
    @Inject @Named("profile") boolean profile;
    @Inject @Named("profileBinary") boolean profileBinary;
    @Inject @Named("profileFile") File profileFile;
    @Inject @Named("profileDepth") int profileDepth;
    @Inject @Named("profileInterval") int profileInterval;

    @Override protected VmCommandBuilder newVmCommandBuilder(Action action, File workingDirectory) {
        List<String> vmCommand = new ArrayList<String>();
        Iterables.addAll(vmCommand, invokeWith());
        vmCommand.add(javaPath("java"));
        if (profile) {
            vmCommand.add("-agentlib:hprof="
                          + "cpu=samples,"
                          + "format=" + (profileBinary ? 'b' : 'a') + ","
                          + "file=" + profileFile + ","
                          + "depth=" + profileDepth + ","
                          + "interval=" + profileInterval + ","
                          + "thread=y,"
                          + "verbose=n");
        }
        return new VmCommandBuilder()
                .userDir(workingDirectory)
                .vmCommand(vmCommand);
    }

    @Override public Task installActionTask(final TaskQueue taskQueue, final Task compileTask,
            final Action action, File jar) {
        Task install = new Task("install " + compileTask) {
            @Override protected Result execute() throws Exception {
                ((EnvironmentHost) environment).prepareUserDir(action);
                return Result.SUCCESS;
            }
            @Override public boolean isRunnable() {
                return compileTask.getResult() != null;
            }
        };
        taskQueue.enqueue(install);
        return install;
    }

    @Override protected Classpath getRuntimeClasspath(Action action) {
        Classpath result = new Classpath();
        result.addAll(classpath);
        result.addAll(environment.hostJar(action));

        /*
         * For javax.net.ssl tests dependency on Bouncy Castle for
         * creating a self-signed X509 certificate. Needs to be run
         * with an openjdk, not a sunjdk, which expects a signed jar
         * to authenticate security providers. For example:
         *
         * --java-home /usr/lib/jvm/java-6-openjdk
         */
        result.addAll(new File("/usr/share/java/bcprov.jar"));
        return result;
    }
}
