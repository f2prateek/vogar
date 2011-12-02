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

package vogar;

import com.google.common.base.Splitter;
import java.io.File;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import vogar.commands.VmCommandBuilder;
import vogar.tasks.RunActionTask;
import vogar.tasks.Task;

/**
 * A Mode for running actions. Examples including running in a virtual machine
 * either on the host or a device or within a specific context such as within an
 * Activity.
 */
public abstract class Mode {
    protected final Run run;

    protected Mode(Run run) {
        this.run = run;
    }

    /**
     * Returns a parsed list of the --invoke-with command and its
     * arguments, or an empty list if no --invoke-with was provided.
     */
    protected Iterable<String> invokeWith() {
        if (run.invokeWith == null) {
            return Collections.emptyList();
        }
        return Splitter.onPattern("\\s+").omitEmptyStrings().split(run.invokeWith);
    }

    /**
     * Initializes the temporary directories and harness necessary to run
     * actions.
     */
    protected abstract Set<Task> installTasks();

    public abstract Task prepareUserDirTask(Action action);

    public Task executeActionTask(Action action, boolean useLargeTimeout) {
        return new RunActionTask(run, action, useLargeTimeout);
    }

    /**
     * Hook method called after action compilation.
     */
    public abstract Set<Task> installActionTasks(Action action, File jar);

    public abstract Task retrieveFilesTask(Action action);

    /**
     * Deletes files and releases any resources required for the execution of
     * the given action.
     */
    public abstract Set<Task> cleanupTasks(Action action);

    public void fillInProperties(Properties properties, Action action) {
    }

    public Classpath getClasspath() {
        return run.classpath;
    }

    /**
     * Returns a VM for action execution.
     *
     * @param workingDirectory the working directory of the target process. If
     *     the process runs on another device, this is the working directory of
     *     the device.
     */
    public VmCommandBuilder newVmCommandBuilder(Action action, File workingDirectory) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the classpath containing JUnit and the dalvik annotations
     * required for action execution.
     */
    public Classpath getRuntimeClasspath(Action action) {
        throw new UnsupportedOperationException();
    }
}
