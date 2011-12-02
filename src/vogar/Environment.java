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
import vogar.tasks.DeleteDirectoryTask;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;

/**
 * A target runtime environment such as a remote device or the local host
 */
public abstract class Environment {
    protected final Run run;

    protected Environment(Run run) {
        this.run = run;
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
    public void cleanup(TaskQueue taskQueue, Action action, Task runActionTask) {
        if (run.cleanAfter) {
            taskQueue.enqueue(new DeleteDirectoryTask(run.rm, run.localFile(action))
                    .after(runActionTask));
        }
    }

    public final File hostJar(Object nameOrAction) {
        return run.localFile(nameOrAction, nameOrAction + ".jar");
    }

    public abstract File actionUserDir(Action action);

    public void shutdown() {
        if (run.cleanAfter) {
            run.rm.directoryTree(run.localTemp);
        }
    }
}
