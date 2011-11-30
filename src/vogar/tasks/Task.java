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

import vogar.Console;
import vogar.Result;

/**
 * A task necessary to accomplish the user's requested actions. Tasks have
 * prerequisites; a task must not be run until it reports that it is runnable.
 * Tasks may be run at most once; running a task produces a result.
 */
public abstract class Task {
    private final String name;
    private Result result;

    protected Task(String name) {
        this.name = name;
    }

    /**
     * Returns the result of this task; null if this task has not yet completed.
     */
    public Result getResult() {
        return result;
    }

    protected abstract Result execute() throws Exception;

    public abstract boolean isRunnable();

    public final void run(Console console) {
        if (result != null) {
            throw new IllegalStateException();
        }
        try {
            console.verbose("running " + this);
            result = execute();
        } catch (Exception e) {
            // TODO: print stack trace?
            result = Result.ERROR;
        }

        if (result != Result.SUCCESS) {
            console.verbose("warning " + this + " " + result);
        } else {
            console.verbose("success " + this);
        }
    }

    @Override public final String toString() {
        return name;
    }
}
