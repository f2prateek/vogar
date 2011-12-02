/*
 * Copyright (C) 2011 The Android Open Source Project
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

package vogar.tasks;

import java.io.File;
import vogar.Action;
import vogar.Log;
import vogar.Result;
import vogar.commands.Command;
import vogar.commands.Mkdir;

public final class PrepareUserDirTask extends Task {
    private final Log log;
    private final Mkdir mkdir;
    private final Action action;

    public PrepareUserDirTask(Log log, Mkdir mkdir, Action action) {
        super("install " + action);
        this.log = log;
        this.mkdir = mkdir;
        this.action = action;
    }

    @Override protected Result execute() throws Exception {
        File actionUserDir = action.getUserDir();

        // if the user dir exists, cp would copy the files to the wrong place
        if (actionUserDir.exists()) {
            throw new IllegalStateException();
        }

        File resourcesDirectory = action.getResourcesDirectory();
        if (resourcesDirectory != null) {
            mkdir.mkdirs(actionUserDir.getParentFile());
            new Command(log, "cp", "-r", resourcesDirectory.toString(),
                    actionUserDir.toString()).execute();
        } else {
            mkdir.mkdirs(actionUserDir);
        }
        return Result.SUCCESS;
    }
}
