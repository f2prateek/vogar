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

package vogar.target;

import com.google.caliper.Benchmark;
import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;
import vogar.Result;

/**
 * Runs a <a href="http://code.google.com/p/caliper/">Caliper</a> benchmark.
 */
public final class CaliperRunner implements vogar.target.Runner {

    private TargetMonitor monitor;
    private Class<?> testClass;

    public void init(TargetMonitor monitor, String actionName, Class<?> klass) {
        this.monitor = monitor;
        testClass = klass;
    }

    public void run(String actionName, Class<?> klass, String[] args, int timeoutSeconds) {
        monitor.outcomeStarted(actionName, actionName);
        try {
            Runner.main(testClass.asSubclass(Benchmark.class), args);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        monitor.outcomeFinished(Result.SUCCESS);
    }

    public boolean supports(Class<?> klass) {
        return SimpleBenchmark.class.isAssignableFrom(klass);
    }
}
