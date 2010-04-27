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

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.AssertionFailedError;
import junit.framework.Protectable;
import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;
import junit.runner.TestSuiteLoader;
import junit.textui.ResultPrinter;
import vogar.Result;

/**
 * Adapts a JUnit test for use by vogar.
 */
public final class JUnitRunner implements Runner {

    private static final Pattern NAME_THEN_TEST_CLASS = Pattern.compile("(.*)\\(([\\w\\.$]+)\\)");

    private junit.textui.TestRunner testRunner;
    private Test junitTest;

    public void init(TargetMonitor monitor, String actionName, String className) throws Exception {
        final TestSuiteLoader testSuiteLoader = new TestSuiteLoader() {
            public Class load(String suiteClassName) throws ClassNotFoundException {
                return JUnitRunner.class.getClassLoader().loadClass(suiteClassName);
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
                return new TimoutTestResult();
            }
        };

        // perhaps a test name was specified?
        try {
            Class.forName(className);
            this.junitTest = testRunner.getTest(className);
            return;
        } catch (ClassNotFoundException e) {
        }

        // probably a package name. Scan all the classes on the classpath
        this.junitTest = allTestsInPackage(className);
    }

    private TestSuite allTestsInPackage(String className) throws IOException {
        ClassPathScanner classpathScanner = new ClassPathScanner();
        Package aPackage = classpathScanner.scan(className);
        Set<Class<?>> classes = aPackage.getTopLevelClassesRecursive();
        if (classes.isEmpty()) {
            throw new RuntimeException("No classes in package: " + className + "; classpath is "
                    + Arrays.toString(ClassPathScanner.getClassPath()));
        }
        int count = 0;
        TestSuite suite = new TestSuite(className);
        for (Class<?> claz : classes) {
            if (claz.getName().endsWith("Test")) {
                suite.addTestSuite(claz);
                count++;
            }
        }
        if (count == 0) {
            throw new RuntimeException("No classes in package: " + className
                    + "; classes are " + classes);
        }
        return suite;
    }

    public void run(String actionName, String className, String[] args) {
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

    private static class TimoutTestResult extends TestResult {
        final ExecutorService executor = Executors.newCachedThreadPool();

        @Override public void runProtected(Test test, final Protectable p) {
            Future<Throwable> result = executor.submit(new Callable<Throwable>() {
                public Throwable call() throws Exception {
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
                thrown = result.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                thrown = e;
            }

            if (thrown != null) {
                final Throwable finalThrown = thrown;
                super.runProtected(test, new Protectable() {
                    public void protect() throws Throwable {
                        throw finalThrown;
                    }
                });
            }
        }
    }
}
