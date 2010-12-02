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
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
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
import junit.framework.Protectable;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;
import vogar.ClassAnalyzer;
import vogar.Result;
import vogar.monitor.TargetMonitor;
import vogar.util.Threads;

/**
 * Adapts a JUnit3 test for use by vogar.
 */
public final class JUnitRunner implements Runner {

    private static final Pattern NAME_THEN_TEST_CLASS = Pattern.compile("(.*)\\(([\\w\\.$]+)\\)");

    private static final Comparator<Test> ORDER_BY_OUTCOME_NAME = new Comparator<Test>() {
        @Override public int compare(Test a, Test b) {
            return getOutcomeName(a).compareTo(getOutcomeName(b));
        }
    };

    private TargetMonitor monitor;
    private Class<?> testClass;
    private AtomicReference<String> skipPastReference;
    private String actionName;
    private TestEnvironment testEnvironment;
    private Test junitTest;
    private int timeoutSeconds;
    private boolean vmIsUnstable;

    final ExecutorService executor = Executors.newCachedThreadPool(
            Threads.daemonThreadFactory("junitrunner"));

    public void init(TargetMonitor monitor, String actionName, String qualification,
            Class<?> testClass, AtomicReference<String> skipPastReference,
            TestEnvironment testEnvironment, int timeoutSeconds, boolean profile) {
        this.monitor = monitor;
        this.testClass = testClass;
        this.skipPastReference = skipPastReference;
        this.actionName = actionName;
        this.testEnvironment = testEnvironment;
        this.timeoutSeconds = timeoutSeconds;

        if (qualification == null) {
            junitTest = new junit.textui.TestRunner().getTest(testClass.getName());
        } else {
            junitTest = TestSuite.createTest(testClass, qualification);
        }
    }

    public boolean run(String actionName, Profiler profiler, String[] args) {
        // if target args were specified, perhaps only a few tests should be run?
        if (args != null && args.length > 0 && TestCase.class.isAssignableFrom(testClass)) {
            TestSuite testSuite = new TestSuite();
            for (String arg : args) {
                testSuite.addTest(TestSuite.createTest(testClass, arg));
            }
            this.junitTest = testSuite;
        }

        List<Test> tests = new ArrayList<Test>();
        flatten(junitTest, tests);
        Collections.sort(tests, ORDER_BY_OUTCOME_NAME);

        for (Test test : tests) {
            String skipPast = skipPastReference.get();
            if (skipPast != null) {
                if (skipPast.equals(getOutcomeName(test))) {
                    skipPastReference.set(null);
                }
                continue;
            }

            test.run(new TimeoutTestResult(profiler));

            if (vmIsUnstable) {
                return false;
            }
        }

        return true;
    }

    private void flatten(Test test, List<Test> flattened) {
        if (test instanceof TestSuite) {
            TestSuite suite = (TestSuite) test;
            for (Enumeration<Test> e = suite.tests(); e.hasMoreElements(); ) {
                flatten(e.nextElement(), flattened);
            }
        } else {
            flattened.add(test);
        }
    }

    /**
     * Returns the vogar name like {@code tests.xml.DomTest#testFoo} for a test
     * with a JUnit name like {@code testFoo(tests.xml.DomTest)}.
     */
    private static String getOutcomeName(Test test) {
        String testToString = test.toString();

        Matcher matcher = NAME_THEN_TEST_CLASS.matcher(testToString);
        if (matcher.matches()) {
            return matcher.group(2) + "#" + matcher.group(1);
        }

        return testToString;
    }

    /**
     * Runs the test on another thread. If the test completes before the
     * timeout, this reports the result normally. But if the test times out,
     * this reports the timeout stack trace and begins the process of killing
     * this no-longer-trustworthy process.
     */
    private class TimeoutTestResult extends TestResult {

        private final Profiler profiler;

        private TimeoutTestResult(Profiler profiler) {
            this.profiler = profiler;
        }

        @Override public void runProtected(Test test, final Protectable p) {
            testEnvironment.reset();
            monitor.outcomeStarted(JUnitRunner.this, getOutcomeName(test), actionName);

            // Start the test on a background thread.
            final AtomicReference<Thread> executingThreadReference = new AtomicReference<Thread>();
            Future<Throwable> result = executor.submit(new Callable<Throwable>() {
                public Throwable call() throws Exception {
                    executingThreadReference.set(Thread.currentThread());
                    try {
                        if (profiler != null) {
                            profiler.start();
                        }
                        p.protect();
                        if (profiler != null) {
                            profiler.stop();
                        }
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
            } catch (TimeoutException e) {
                vmIsUnstable = true;
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
                System.out.println(BaseTestRunner.getFilteredTrace(prepareForDisplay(thrown)));
                monitor.outcomeFinished(Result.EXEC_FAILED);
            } else {
                monitor.outcomeFinished(Result.SUCCESS);
            }
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
                    && !className.startsWith("sun.reflect")
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
