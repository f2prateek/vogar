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

import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;

import vogar.Result;
import vogar.monitor.TargetMonitor;
import vogar.target.junit4.*;

public class JUnit4RunnerTest {
    private Runner runner;
    private TargetMonitor monitor;

    @Before
    public void before() {
        runner = new JUnit4Runner();
        monitor = mock(TargetMonitor.class);
    }

    @Test
    public void supports_should_judge_whether_Object_is_not_supported() {
        assertEquals(false, runner.supports(new Object().getClass()));
    }

    @Test
    public void supports_should_judge_whether_SimpleTest_has_method_annotated_Test_is_supported() {
        assertEquals(true, runner.supports(SimpleTest.class));
    }

    @Test
    public void supports_should_judge_whether_WrongSuiteTest_not_annotated_RunWith_is_not_supported() {
        assertEquals(false, runner.supports(WrongSuiteTest.class));
    }

    @Test
    public void supports_should_judge_whether_SuiteTest_annotated_RunWith_is_supported() {
        assertEquals(true, runner.supports(SuiteTest.class));
    }

    @Test
    public void init_and_run_for_SimpleTest_should_perform_test() {
        Class<?> target = SimpleTest.class;
        runner.init(monitor, "", null, target);
        runner.run("", target, null, 0);

        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest", "");
        verify(monitor).outcomeFinished(Result.SUCCESS);
    }

    @Test
    public void init_and_run_for_SuiteTest_should_perform_tests() {
        Class<?> target = SuiteTest.class;
        runner.init(monitor, "", null, target);
        runner.run("", target, null, 0);

        verify(monitor).outcomeStarted(runner,
                "vogar.target.junit4.SimpleTest#simpleTest", "");
        verify(monitor).outcomeStarted(runner,
                "vogar.target.junit4.SimpleTest2#simpleTest1", "");
        verify(monitor).outcomeStarted(runner,
                "vogar.target.junit4.SimpleTest2#simpleTest2", "");
        verify(monitor).outcomeStarted(runner,
                "vogar.target.junit4.SimpleTest2#simpleTest3", "");
        verify(monitor, times(4)).outcomeFinished(Result.SUCCESS);
    }

    @Test
    public void init_and_run_for_SimpleTest2_with_ActionName_should_perform_test() {
        Class<?> target = SimpleTest2.class;
        String actionName = "actionName";
        runner.init(monitor, actionName, null, target);
        runner.run("", target, null, 0);

        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest1", actionName);
        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest2", actionName);
        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest3", actionName);
        verify(monitor, times(3)).outcomeFinished(Result.SUCCESS);
    }

    @Test
    public void init_and_run_for_SimpleTest2_limitting_to_1method_should_perform_test() {
        Class<?> target = SimpleTest2.class;
        String actionName = "actionName";
        runner.init(monitor, actionName, null, target);
        runner.run("", target, new String[] { "simpleTest2" }, 0);

        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest2", actionName);
        verify(monitor).outcomeFinished(Result.SUCCESS);
    }

    @Test
    public void init_and_run_for_SimpleTest2_limitting_to_2methods_should_perform_test() {
        Class<?> target = SimpleTest2.class;
        String actionName = "actionName";
        runner.init(monitor, actionName, null, target);
        runner.run("", target, new String[] { "simpleTest2", "simpleTest3" }, 0);

        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest2", actionName);
        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest3", actionName);
        verify(monitor, times(2)).outcomeFinished(Result.SUCCESS);
    }

    @Test
    public void init_limitting_to_1method_and_run_for_SimpleTest2_should_perform_test() {
        Class<?> target = SimpleTest2.class;
        String actionName = "actionName";
        runner.init(monitor, actionName, "simpleTest2", target);
        runner.run("", target, null, 0);

        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest2", actionName);
        verify(monitor).outcomeFinished(Result.SUCCESS);
    }

    // JUnit4 fails test by indicating test method in test suite
    @Test
    public void init_limitting_to_1method_and_run_for_SuiteTest_should_FAIL_test() {
        Class<?> target = SuiteTest.class;
        runner.init(monitor, "", "testSimple", target);
        runner.run("", target, null, 0);

        verify(monitor).outcomeStarted(runner,
                "org.junit.runner.manipulation.Filter#initializationError", "");
        verify(monitor).outcomeFinished(Result.EXEC_FAILED);
    }

    @Test
    public void init_limitting_to_wrong_1method_and_run_for_SimpleTest2_should_fail_test() {
        Class<?> target = SimpleTest2.class;
        String actionName = "actionName";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        runner.init(monitor, actionName, "testSimple5", target);
        runner.run("", target, null, 0);

        verify(monitor).outcomeStarted(runner,
                "org.junit.runner.manipulation.Filter#initializationError",
                actionName);
        verify(monitor).outcomeFinished(Result.EXEC_FAILED);

        String outStr = baos.toString();
        assertThat(
                outStr,
                containsString("java.lang.Exception: No tests found matching Method testSimple5"));
    }

    @Test
    public void init_and_run_for_SimpleTest2_limitting_to_1method_with_both_init_and_run_should_perform_test() {
        Class<?> target = SimpleTest2.class;
        String actionName = "actionName";
        runner.init(monitor, actionName, "simpleTest3", target);
        runner.run("", target, new String[] { "simpleTest2" }, 0);

        verify(monitor).outcomeStarted(runner,
                target.getName() + "#simpleTest2", actionName);
        verify(monitor).outcomeFinished(Result.SUCCESS);
    }

    @Test
    public void init_and_run_for_FailTest_should_perform_test() {
        Class<?> target = FailTest.class;
        String actionName = "actionName";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        runner.init(monitor, actionName, null, target);
        runner.run("", target, null, 0);

        verify(monitor).outcomeStarted(runner,
                target.getName() + "#successTest", actionName);
        verify(monitor).outcomeStarted(runner, target.getName() + "#failTest",
                actionName);
        verify(monitor).outcomeStarted(runner,
                target.getName() + "#throwExceptionTest", actionName);
        verify(monitor).outcomeFinished(Result.SUCCESS);
        verify(monitor, times(2)).outcomeFinished(Result.EXEC_FAILED);

        String outStr = baos.toString();
        assertThat(outStr, containsString("java.lang.AssertionError: failed."));
        assertThat(outStr,
                containsString("java.lang.RuntimeException: exceptrion"));
    }

    // JUnit4 performs tests in each test class although JUnit3 performs tests
    // in each test method
    // So JUnit4Runner can't deal with 'timeoutSeconds' argument
    @Test
    public void init_and_run_for_LongTest_with_time_limit_should_NOT_report_time_out() {
        Class<?> target = LongTest.class;
        String actionName = "actionName";

        runner.init(monitor, actionName, null, target);
        runner.run("", target, null, 1);

        verify(monitor).outcomeStarted(runner, target.getName() + "#longTest",
                actionName);
        verify(monitor).outcomeFinished(Result.SUCCESS);
    }

    @Test
    public void init_and_run_for_LongTest2_with_time_limit_should_perform_test() {
        Class<?> target = LongTest2.class;
        String actionName = "actionName";

        runner.init(monitor, actionName, null, target);
        runner.run("", target, null, 1);

        verify(monitor, times(8)).outcomeFinished(Result.SUCCESS);
    }
}
