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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.AssertionFailedError;
import junit.framework.Protectable;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;
import junit.runner.TestSuiteLoader;
import junit.textui.ResultPrinter;
import vogar.ClassAnalyzer;
import vogar.Result;
import vogar.Threads;

/**
 * Adapts a JUnit test for use by vogar.
 */
public final class JUnitRunner implements Runner {

    private static final Pattern NAME_THEN_TEST_CLASS = Pattern.compile("(.*)\\(([\\w\\.$]+)\\)");

    private junit.textui.TestRunner testRunner;
    private Test junitTest;
    private int timeoutSeconds;

    public void init(TargetMonitor monitor, String actionName, String qualification,
            Class<?> klass) {
        final TestSuiteLoader testSuiteLoader = new TestSuiteLoader() {
            public Class load(String suiteClassName) throws ClassNotFoundException {
                return Class.forName(suiteClassName);
            }

            public Class reload(Class c) {
                return c;
            }
        };

        testRunner = new junit.textui.TestRunner(
                new MonitoringResultPrinter(monitor, actionName)) {
            @Override public TestSuiteLoader getLoader() {
                return testSuiteLoader;
            }

            @Override protected TestResult createTestResult() {
                return new TimeoutTestResult();
            }
        };

        if (qualification == null) {
            junitTest = testRunner.getTest(klass.getName());
        } else {
            junitTest = TestSuite.createTest(klass, qualification);
        }
    }

    public void run(String actionName, Class<?> klass, String[] args, int timeoutSeconds) {
        // if target args were specified, perhaps only a few tests should be run?
        this.timeoutSeconds = timeoutSeconds;
        if (args != null && args.length > 0 && TestCase.class.isAssignableFrom(klass)) {
            TestSuite testSuite = new TestSuite();
            for (String arg : args) {
                testSuite.addTest(TestSuite.createTest(klass, arg));
            }
            this.junitTest = testSuite;
        }
        testRunner.doRun(junitTest);
    }

    /**
     * Returns the vogar name like {@code tests.xml.DomTest#testFoo} for a test
     * with a JUnit name like {@code testFoo(tests.xml.DomTest)}.
     */
    private String getOutcomeName(Test test) {
        String testToString = test.toString();

        Matcher matcher = NAME_THEN_TEST_CLASS.matcher(testToString);
        if (matcher.matches()) {
            return matcher.group(2) + "#" + matcher.group(1);
        }

        return testToString;
    }

    /**
     * This result printer posts test names, output and exceptions to the
     * hosting process.
     */
    private class MonitoringResultPrinter extends ResultPrinter {
        private final TargetMonitor monitor;
        private final String actionName;

        private Test current;
        private Throwable failure;

        public MonitoringResultPrinter(TargetMonitor monitor, String actionName) {
            super(System.out);
            this.monitor = monitor;
            this.actionName = actionName;
        }

        @Override public void addError(Test test, Throwable t) {
            System.out.println(BaseTestRunner.getFilteredTrace(t));
            failure = t;
        }

        @Override public void addFailure(Test test, AssertionFailedError t) {
            System.out.println(BaseTestRunner.getFilteredTrace(t));
            failure = t;
        }

        @Override public void endTest(Test test) {
            if (current == null) {
                throw new IllegalStateException();
            }
            monitor.outcomeFinished(
                    failure == null ? Result.SUCCESS : Result.EXEC_FAILED);
            current = null;
            failure = null;
        }

        @Override public void startTest(Test test) {
            if (current != null) {
                throw new IllegalStateException();
            }
            current = test;
            monitor.outcomeStarted(getOutcomeName(test), actionName);
        }

        @Override protected void printHeader(long runTime) {}
        @Override protected void printErrors(TestResult result) {}
        @Override protected void printFailures(TestResult result) {}
        @Override protected void printFooter(TestResult result) {}
    }

    private class TimeoutTestResult extends TestResult {
        final ExecutorService executor = Executors.newCachedThreadPool(
                Threads.daemonThreadFactory());

        @Override public void runProtected(Test test, final Protectable p) {
            final AtomicReference<Thread> executingThreadReference = new AtomicReference<Thread>();
            Future<Throwable> result = executor.submit(new Callable<Throwable>() {
                public Throwable call() throws Exception {
                    executingThreadReference.set(Thread.currentThread());
                    try {
                        p.protect();
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                }
            });

            Throwable thrown;
            try {
                if (timeoutSeconds == 0) {
                    thrown = result.get();
                } else {
                    thrown = result.get(timeoutSeconds, TimeUnit.SECONDS);
                }
            } catch (TimeoutException e) {
                Thread executingThread = executingThreadReference.get();
                if (executingThread != null) {
                    executingThread.interrupt();
                    e.setStackTrace(executingThread.getStackTrace());
                }
                thrown = e;
            } catch (Exception e) {
                thrown = e;
            }

            if (thrown != null) {
                final Throwable finalThrown = thrown;
                super.runProtected(test, new Protectable() {
                    public void protect() throws Throwable {
                        // TODO: remove the unnecessary wrapping when b/2700761 is fixed
                        Exception wrapped = new Exception();
                        wrapped.setStackTrace(new StackTraceElement[0]);
                        wrapped.initCause(finalThrown);
                        throw wrapped;
                    }
                });
            }
        }
    }

    public boolean supports(Class<?> klass) {
        // public class FooTest extends TestCase {...}
        //   or
        // public class FooSuite {
        //    public static Test suite() {...}
        // }
        return TestCase.class.isAssignableFrom(klass)
                || new ClassAnalyzer(klass).hasMethod(true, Test.class, "suite");
    }
}
