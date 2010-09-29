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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import junit.textui.ResultPrinter;
import vogar.ClassAnalyzer;
import vogar.Result;
import vogar.monitor.TargetMonitor;
import vogar.util.Threads;

/**
 * Adapts a JUnit3 test for use by vogar.
 */
public final class JUnitRunner implements Runner {

    private static final Pattern NAME_THEN_TEST_CLASS = Pattern.compile("(.*)\\(([\\w\\.$]+)\\)");

    private junit.textui.TestRunner testRunner;
    private Test junitTest;
    private int timeoutSeconds;
    private TestEnvironment testEnvironment;

    public void init(TargetMonitor monitor, String actionName, String qualification,
            Class<?> klass, TestEnvironment testEnvironment) {
        this.testEnvironment = testEnvironment;
        testRunner = new junit.textui.TestRunner(
                new MonitoringResultPrinter(monitor, actionName)) {
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
            monitor.outcomeStarted(JUnitRunner.this, getOutcomeName(test), actionName);
        }

        @Override protected void printHeader(long runTime) {}
        @Override protected void printErrors(TestResult result) {}
        @Override protected void printFailures(TestResult result) {}
        @Override protected void printFooter(TestResult result) {}
    }

    private class TimeoutTestResult extends TestResult {
        final ExecutorService executor = Executors.newCachedThreadPool(
                Threads.daemonThreadFactory("junitrunner"));

        @Override public void runProtected(Test test, final Protectable p) {
            testEnvironment.reset();

            // Start the test on a background thread.
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

            // Wait until either the result arrives or the test times out.
            Throwable thrown;
            try {
                thrown = timeoutSeconds == 0
                        ? result.get()
                        : result.get(timeoutSeconds, TimeUnit.SECONDS);
                if (thrown == null) {
                    return;
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

            final Throwable reportableThrown = prepareForDisplay(thrown);

            // Report failures to the superclass' runProtected method.
            super.runProtected(test, new Protectable() {
                public void protect() throws Throwable {
                    throw reportableThrown;
                }
            });
        }
    }

    /**
     * Strip vogar's lines from the stack trace. For example, we'd strip everything
     * after the testFoo() line in this stack trace:
     *
     *     at junit.framework.Assert.fail(Assert.java:47)
     *     at junit.framework.Assert.assertTrue(Assert.java:20)
     *     at junit.framework.Assert.assertFalse(Assert.java:34)
     *     at junit.framework.Assert.assertFalse(Assert.java:41)
     *     at com.foo.FooTest.baz(FooTest.java:370)
     *     at com.foo.FooTest.testFoo(FooTest.java:361)
     *     at java.lang.reflect.Method.invokeNative(Native Method)
     *     at java.lang.reflect.Method.invoke(Method.java:515)
     *     at junit.framework.TestCase.runTest(TestCase.java:154)
     *     at junit.framework.TestCase.runBare(TestCase.java:127)
     *     at vogar.target.JUnitRunner$TimeoutTestResult$1.call(JUnitRunner.java:163)
     *     at vogar.target.JUnitRunner$TimeoutTestResult$1.call(JUnitRunner.java:159)
     *     at java.util.concurrent.FutureTask$Sync.innerRun(FutureTask.java:306)
     *     at java.util.concurrent.FutureTask.run(FutureTask.java:138)
     *     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1088)
     *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:581)
     *     at java.lang.Thread.run(Thread.java:1019)
     *
     * Note that JUnit does its own stripping which takes care of the assert lines.
     */
    public Throwable prepareForDisplay(Throwable t) {
        StackTraceElement[] stackTraceElements = t.getStackTrace();
        List<StackTraceElement> result = new ArrayList<StackTraceElement>();
        result.addAll(Arrays.asList(stackTraceElements));
        boolean foundVogar = false;
        for (int i = result.size() - 1; i >= 0; i--) {
            String className = result.get(i).getClassName();
            if (className.startsWith("vogar.target")) {
                foundVogar = true;
            } else if (foundVogar
                    && !className.startsWith("java.lang.reflect")
                    && !className.startsWith("junit.framework")) {
                StackTraceElement[] newTrace = result.subList(0, i + 1).toArray(new StackTraceElement[i + 1]);
                t.setStackTrace(newTrace);
                break;
            }
        }
        return t;
    }

    public boolean supports(Class<?> klass) {
        return isJunit3Test(klass);
    }

    static boolean isJunit3Test(Class<?> klass) {
        // public class FooTest extends TestCase {...}
        //   or
        // public class FooSuite {
        //    public static Test suite() {...}
        // }
        return (TestCase.class.isAssignableFrom(klass) && !Modifier.isAbstract(klass.getModifiers()))
                || new ClassAnalyzer(klass).hasMethod(true, Test.class, "suite");
    }
}
