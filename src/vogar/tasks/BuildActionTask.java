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
import java.io.IOException;
import vogar.Action;
import vogar.Driver;
import vogar.Mode;
import vogar.Outcome;
import vogar.Result;
import vogar.commands.CommandFailedException;

/**
 * Compiles classes for the given action and makes them ready for execution.
 */
public final class BuildActionTask extends Task {
    private final Action action;
    private final Mode mode;
    private final Driver driver;
    private final File jar;

    public BuildActionTask(Action action, Mode mode, Driver driver, File jar) {
        super("build " + action.getName());
        this.action = action;
        this.mode = mode;
        this.driver = driver;
        this.jar = jar;
    }

    @Override protected Result execute() throws Exception {
        try {
            mode.compile(action, jar);
            return Result.SUCCESS;
        } catch (CommandFailedException e) {
            driver.addEarlyResult(new Outcome(action.getName(), Result.COMPILE_FAILED,
                    e.getOutputLines()));
            return Result.COMPILE_FAILED;
        } catch (IOException e) {
            driver.addEarlyResult(new Outcome(action.getName(), Result.ERROR, e));
            return Result.ERROR;
        }
    }

    @Override public boolean isRunnable() {
        return true;
    }
}
