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

import java.io.File;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.commands.Rm;
import vogar.tasks.TaskQueue;
import vogar.util.Strings;

/**
 * A target runtime environment such as a remote device or the local host
 */
public abstract class Environment {
    @Inject Log log;
    @Inject Rm rm;
    @Inject @Named("cleanBefore") boolean cleanBefore;
    @Inject @Named("cleanAfter") boolean cleanAfter;
    @Inject @Named("debugPort") Integer debugPort;
    @Inject @Named("localTemp") File localTemp;

    public final boolean cleanBefore() {
        return cleanBefore;
    }

    public final boolean cleanAfter() {
        return cleanAfter;
    }

    public final Integer getDebugPort() {
        return debugPort;
    }

    /**
     * Initializes the temporary directories and harness necessary to run
     * actions.
     */
    public void installTasks(TaskQueue taskQueue) {
    }

    /**
     * Deletes files and releases any resources required for the execution of
     * the given action.
     */
    public void cleanup(Action action) {
        if (cleanAfter) {
            rm.directoryTree(file(action));
        }
    }

    public final File file(Object... path) {
        return new File(localTemp + "/" + Strings.join("/", path));
    }

    public final File hostJar(Object nameOrAction) {
        return file(nameOrAction, nameOrAction + ".jar");
    }

    public final File actionUserDir(Action action) {
        File testTemp = new File(localTemp, "userDir");
        return new File(testTemp, action.getName());
    }

    public void shutdown() {
        if (cleanAfter) {
            rm.directoryTree(localTemp);
        }
    }
}
