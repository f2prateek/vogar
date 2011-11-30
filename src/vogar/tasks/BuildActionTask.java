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

import vogar.Action;
import vogar.Driver;
import vogar.Mode;
import vogar.Outcome;
import vogar.Result;

/**
 * Compile using the current mode.
 */
public final class BuildActionTask extends Task {
    private final Action action;
    private final Mode mode;
    private final Driver driver;

    public BuildActionTask(Action action, Mode mode, Driver driver) {
        super("build " + action.getName());
        this.action = action;
        this.mode = mode;
        this.driver = driver;
    }

    @Override public boolean isRunnable() {
        return true;
    }

    @Override protected Result execute() throws Exception {
        Outcome outcome = mode.buildAndInstall(action);
        if (outcome != null) {
            driver.addEarlyResult(outcome);
            return outcome.getResult();
        }
        return Result.SUCCESS;
    }
}
