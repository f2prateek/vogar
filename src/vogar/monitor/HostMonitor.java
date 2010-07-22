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

package vogar.monitor;

import vogar.Outcome;

/**
 * Attaches to a target process to handle output.
 */
public interface HostMonitor {

    boolean connect();

    boolean monitor(Handler handler);

    void close();

    /**
     * Handles updates on the outcomes of a target process.
     */
    public interface Handler {

        /**
         * @param runnerClass can be null, indicating nothing is actually being run. This will
         *        happen in the event of an impending error.
         */
        void runnerClass(String outcomeName, String runnerClass);

        /**
         * Receive a completed outcome.
         */
        void outcome(Outcome outcome);

        /**
         * Receive partial output from an action being executed.
         */
        void output(String outcomeName, String output);
    }
}
