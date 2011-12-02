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

import java.io.File;
import vogar.Action;
import vogar.Environment;
import vogar.Run;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;

public final class EnvironmentDevice extends Environment {
    /** Prepares the device (but doesn't install vogar */
    public final Task prepareDeviceTask = new PrepareDeviceTask(run);

    public EnvironmentDevice(Run run) {
        super(run);
    }

    /**
     * Returns an environment variable assignment to configure where the VM will
     * store its dexopt files. This must be set on production devices and is
     * optional for development devices.
     */
    public String getAndroidData() {
        // The VM wants the parent directory of a directory named "dalvik-cache"
        return "ANDROID_DATA=" + run.dalvikCache().getParentFile();
    }

    @Override public void installTasks(TaskQueue taskQueue) {
        taskQueue.enqueue(prepareDeviceTask);
    }

    @Override public File actionUserDir(Action action) {
        return new File(run.runnerDir, action.getName());
    }

    @Override public void cleanup(TaskQueue taskQueue, Action action, Task runActionTask) {
        super.cleanup(taskQueue, action, runActionTask);

        File actionUserDir = action.getUserDir();
        Task retrieveFilesTask = new RetrieveDeviceFilesTask(run, actionUserDir)
                .after(runActionTask);
        taskQueue.enqueue(retrieveFilesTask);

        if (run.cleanAfter) {
            taskQueue.enqueue(new DeleteDeviceFilesTask(run.androidSdk, actionUserDir)
                    .after(retrieveFilesTask));
        }
    }

    @Override public void shutdown() {
        super.shutdown();
        if (run.cleanAfter) {
            // TODO: convert shutdown to tasks
            // taskQueue.enqueue(new DeleteDeviceFilesTask(run, runnerDir)
            //         .after(retrieveFilesTask));
            run.androidSdk.rm(run.runnerDir);
        }
    }
}
