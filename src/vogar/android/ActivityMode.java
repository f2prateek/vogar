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
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import vogar.Action;
import vogar.Mode;
import vogar.Run;
import vogar.TestProperties;
import vogar.tasks.ExtractJarResourceTask;
import vogar.tasks.Task;

/**
 * Runs an action in the context of an android.app.Activity on a device
 */
public final class ActivityMode extends Mode {
    public ActivityMode(Run run) {
        super(run);
    }

    @Override protected Set<Task> installTasks() {
        return Collections.<Task>singleton(
                new ExtractJarResourceTask("/vogar/vogar.keystore", run.keystore));
    }

    @Override public Task prepareUserDirTask(Action action) {
        return new PrepareUserDirTask(run.androidSdk, action);
    }

    @Override public Set<Task> installActionTasks(Action action, File jar) {
        return Collections.<Task>singleton(new InstallApkTask(run, action, jar));
    }

    @Override public Task executeActionTask(Action action, boolean useLargeTimeout) {
        return new RunActivityTask(run, action, useLargeTimeout);
    }

    @Override public void fillInProperties(Properties properties, Action action) {
        super.fillInProperties(properties, action);
        properties.setProperty(TestProperties.DEVICE_RUNNER_DIR, run.runnerDir.getPath());
    }

    @Override public Task retrieveFilesTask(Action action) {
        return run.environmentDevice.retrieveFilesTask(action);
    }

    @Override public Set<Task> cleanupTasks(Action action) {
        Set<Task> result = new HashSet<Task>();
        result.add(run.environmentDevice.cleanupTask(action));
        result.add(new UninstallApkTask(run.androidSdk, action.getName()));
        return result;
    }
}
