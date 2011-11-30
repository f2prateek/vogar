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

import com.google.common.base.Splitter;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.commands.Command;
import vogar.target.TestRunner;

/**
 * A Java-like virtual machine for compiling and running tests.
 */
public abstract class Vm extends Mode {

    @Inject @Named("invokeWith") String invokeWith;
    @Inject @Named("additionalVmArgs") List<String> additionalVmArgs;
    @Inject @Named("targetArgs") List<String> targetArgs;

    /**
     * Returns a parsed list of the --invoke-with command and its
     * arguments, or an empty list if no --invoke-with was provided.
     */
    protected Iterable<String> invokeWith() {
        if (invokeWith == null) {
            return Collections.emptyList();
        }
        return Splitter.onPattern("\\s+").omitEmptyStrings().split(invokeWith);
    }

    /**
     * Returns a VM for action execution.
     */
    @Override public Command createActionCommand(
            Action action, String skipPast, int monitorPort) {
        File workingDirectory = action.getUserDir();
        VmCommandBuilder vmCommandBuilder = newVmCommandBuilder(action, workingDirectory);
        if (useBootClasspath) {
            vmCommandBuilder.bootClasspath(getRuntimeClasspath(action));
        } else {
            vmCommandBuilder.classpath(getRuntimeClasspath(action));
        }

        if (monitorPort != -1) {
            vmCommandBuilder.args("--monitorPort", Integer.toString(monitorPort));
        }
        if (skipPast != null) {
            vmCommandBuilder.args("--skipPast", skipPast);
        }

        return vmCommandBuilder
                .temp(workingDirectory)
                .debugPort(environment.debugPort)
                .vmArgs(additionalVmArgs)
                .mainClass(TestRunner.class.getName())
                .args(targetArgs)
                .build();
    }

    /**
     * Returns a VM for action execution.
     *
     * @param workingDirectory the working directory of the target process. If
     *     the process runs on another device, this is the working directory of
     *     the device.
     */
    protected abstract VmCommandBuilder newVmCommandBuilder(Action action, File workingDirectory);

    /**
     * Returns the classpath containing JUnit and the dalvik annotations
     * required for action execution.
     */
    protected abstract Classpath getRuntimeClasspath(Action action);

    /**
     * Builds a virtual machine command.
     */
    public class VmCommandBuilder {
        private File temp;
        private boolean classpathViaProperty;
        private Classpath bootClasspath = new Classpath();
        private Classpath classpath = new Classpath();
        private File userDir;
        private Integer debugPort;
        private String mainClass;
        private PrintStream output;
        private int maxLength = -1;
        private List<String> vmCommand = Collections.singletonList("java");
        private List<String> vmArgs = new ArrayList<String>();
        private List<String> args = new ArrayList<String>();
        private Map<String, String> env = new LinkedHashMap<String, String>();

        public VmCommandBuilder vmCommand(List<String> vmCommand) {
            this.vmCommand = new ArrayList<String>(vmCommand);
            return this;
        }

        public VmCommandBuilder temp(File temp) {
            this.temp = temp;
            return this;
        }

        public VmCommandBuilder bootClasspath(Classpath bootClasspath) {
            this.bootClasspath.addAll(bootClasspath);
            return this;
        }

        public VmCommandBuilder classpath(Classpath classpath) {
            this.classpath.addAll(classpath);
            return this;
        }

        public VmCommandBuilder classpathViaProperty(boolean classpathViaProperty) {
            this.classpathViaProperty = classpathViaProperty;
            return this;
        }

        /**
         * The user dir on the target. This directory might not exist on the
         * local disk.
         */
        public VmCommandBuilder userDir(File userDir) {
            this.userDir = userDir;
            return this;
        }

        public VmCommandBuilder env(String key, String value) {
            this.env.put(key, value);
            return this;
        }

        public VmCommandBuilder debugPort(Integer debugPort) {
            this.debugPort = debugPort;
            return this;
        }

        public VmCommandBuilder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public VmCommandBuilder output(PrintStream output) {
            this.output = output;
            return this;
        }

        public VmCommandBuilder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public VmCommandBuilder vmArgs(String... vmArgs) {
            return vmArgs(Arrays.asList(vmArgs));
        }

        public VmCommandBuilder vmArgs(Collection<String> vmArgs) {
            this.vmArgs.addAll(vmArgs);
            return this;
        }

        public VmCommandBuilder args(String... args) {
            return args(Arrays.asList(args));
        }

        public VmCommandBuilder args(Collection<String> args) {
            this.args.addAll(args);
            return this;
        }

        public Command build() {
            Command.Builder builder = new Command.Builder(log);

            for (Map.Entry<String, String> entry : env.entrySet()) {
                builder.env(entry.getKey(), entry.getValue());
            }

            builder.args(vmCommand);
            if (classpathViaProperty) {
                builder.args("-Djava.class.path=" + classpath);
            } else {
                builder.args("-classpath", classpath.toString());
            }
            // Only output this if there's something on the boot classpath,
            // otherwise dalvikvm gets upset.
            if (!bootClasspath.isEmpty()) {
                builder.args("-Xbootclasspath/a:" + bootClasspath);
            }
            if (userDir != null) {
                builder.args("-Duser.dir=" + userDir);
            }

            if (temp != null) {
                builder.args("-Djava.io.tmpdir=" + temp);
            }

            if (debugPort != null) {
                builder.args("-Xrunjdwp:transport=dt_socket,address="
                        + debugPort + ",server=y,suspend=y");
            }

            builder.args(vmArgs);
            builder.args(mainClass);
            builder.args(args);
            builder.tee(output);
            builder.maxLength(maxLength);
            return builder.build();
        }
    }
}
