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

package vogar.target;

import java.lang.reflect.Method;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import vogar.Result;
import vogar.monitor.TargetMonitor;

/**
 * Adapts a JUnit4 test for use by vogar.
 */
public final class JUnit4Runner implements Runner {
    private JUnitCore jUnitCore;
    private String qualification;

    public boolean supports(Class<?> klass) {
        return JUnitRunner.isJunit3Test(klass) || isJUnit4Test(klass);
    }

    private boolean isJUnit4Test(Class<?> klass) {
        if (klass.getAnnotation(org.junit.runner.RunWith.class) != null) {
            return true;
        }
        for (Method method : klass.getMethods()) {
            if (method.getAnnotation(org.junit.Test.class) != null) {
                return true;
            }
        }
        return false;
    }

    public void init(final TargetMonitor monitor, final String actionName, String qualification,
            Class<?> klass, TestEnvironment testEnvironment, int timeoutSeconds, boolean profile) {
        jUnitCore = new JUnitCore();
        jUnitCore.addListener(new RunListener() {
            private Failure failure;

            @Override
            public void testStarted(Description description) throws Exception {
                monitor.outcomeStarted(
                        JUnit4Runner.this,
                        description.getClassName() + "#" + description.getMethodName(),
                        actionName);
                failure = null;
            }

            @Override
            public void testFinished(Description description) throws Exception {
                monitor.outcomeFinished(failure == null ? Result.SUCCESS
                        : Result.EXEC_FAILED);
            }

            @Override
            public void testFailure(Failure failure) throws Exception {
                System.out.println(failure.getTrace());
                this.failure = failure;
            }
        });
        this.qualification = qualification;
    }

    public boolean run(String actionName, Class<?> klass, String skipPast, Profiler profiler,
                       String[] args) {
        if (profiler != null) {
            profiler.start();
        }
        // ignore the timeoutSeconds parameter because JUnit4 performs tests
        // in test-class unit and it can't specify timeout in test-method unit
        if (args != null && args.length > 0) {
            for (String arg : args) {
                runTests(klass, arg);
            }
            return true;
        }
        if (qualification == null) {
            runTests(klass);
        } else {
            runTests(klass, qualification);
        }
        if (profiler != null) {
            profiler.stop();
        }
        return true;
    }

    private org.junit.runner.Result runTests(Class<?> klass) {
        return jUnitCore.run(klass);
    }

    private org.junit.runner.Result runTests(Class<?> klass, String arg) {
        return jUnitCore.run(Request.method(klass, arg));
    }
}
