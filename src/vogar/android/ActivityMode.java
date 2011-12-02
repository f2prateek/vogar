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

import java.io.File;
import java.util.Properties;
import java.util.Set;
import vogar.Action;
import vogar.Mode;
import vogar.Run;
import vogar.TestProperties;
import vogar.tasks.ExtractJarResourceTask;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;

/**
 * Runs an action in the context of an android.app.Activity on a device
 */
public final class ActivityMode extends Mode {
    public ActivityMode(Run run) {
        super(run);
    }

    @Override protected void installTasks(Set<Task> tasks) {
        tasks.add(new ExtractJarResourceTask("/vogar/vogar.keystore", run.keystore));
    }

    @Override public void installActionTask(Set<Task> tasks, Task compileTask,
            Action action, File jar) {
        tasks.add(new PrepareUserDirTask(run.androidSdk, action)
                .afterSuccess(run.environmentDevice.prepareDeviceTask));
        tasks.add(new InstallApkTask(run, action, jar)
                .afterSuccess(compileTask)
                .afterSuccess(run.environmentDevice.prepareDeviceTask));
    }

    @Override public Task createRunActionTask(Action action, boolean useLargeTimeout) {
        return new RunActivityTask(run, action, useLargeTimeout);
    }

    @Override public void fillInProperties(Properties properties, Action action) {
        super.fillInProperties(properties, action);
        properties.setProperty(TestProperties.DEVICE_RUNNER_DIR, run.runnerDir.getPath());
    }

    @Override public void cleanup(TaskQueue taskQueue, Action action, Task runActionTask) {
        super.cleanup(taskQueue, action, runActionTask);
        if (run.cleanAfter) {
            taskQueue.enqueue(new UninstallApkTask(run.androidSdk, action.getName())
                    .after(runActionTask));
        }
    }
}
