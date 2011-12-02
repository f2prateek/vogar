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
import java.util.Collections;
import java.util.Set;
import vogar.Action;
import vogar.Environment;
import vogar.Run;
import vogar.tasks.Task;

public final class EnvironmentDevice extends Environment {
    public EnvironmentDevice(Run run) {
        super(run);
    }

    @Override public Set<Task> prepareTargetTasks() {
        return Collections.<Task>singleton(new PrepareDeviceTask(run));
    }

    @Override public File actionUserDir(Action action) {
        return new File(run.runnerDir, action.getName());
    }

    public Task retrieveFilesTask(Action action) {
        return new RetrieveDeviceFilesTask(run, action.getUserDir());
    }

    public Task cleanupTask(Action action) {
        return new DeleteDeviceFilesTask(run.androidSdk, action.getUserDir());
    }

    @Override public Set<Task> shutdownTasks() {
        return Collections.<Task>singleton(new DeleteDeviceFilesTask(run.androidSdk, run.runnerDir));
    }
}
