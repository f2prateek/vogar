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
import vogar.commands.Command;
import vogar.tasks.RetrieveFilesTask;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;

public final class EnvironmentHost extends Environment {
    public EnvironmentHost(Run run) {
        super(run);
    }

    public void prepareUserDir(Action action) {
        File actionUserDir = action.getUserDir();

        // if the user dir exists, cp would copy the files to the wrong place
        if (actionUserDir.exists()) {
            throw new IllegalStateException();
        }

        File resourcesDirectory = action.getResourcesDirectory();
        if (resourcesDirectory != null) {
            run.mkdir.mkdirs(actionUserDir.getParentFile());
            new Command(run.log, "cp", "-r", resourcesDirectory.toString(),
                    actionUserDir.toString()).execute();
        } else {
            run.mkdir.mkdirs(actionUserDir);
        }
    }

    public File actionUserDir(Action action) {
        File testTemp = new File(run.localTemp, "userDir");
        return new File(testTemp, action.getName());
    }

    @Override public void cleanup(TaskQueue taskQueue, Action action, Task runActionTask) {
        Task task = new RetrieveFilesTask(
                run, new File("./vogar-results"), action.getUserDir(), run.retrievedFiles);
        taskQueue.enqueue(task.after(runActionTask));
        super.cleanup(taskQueue, action, runActionTask);
    }
}
