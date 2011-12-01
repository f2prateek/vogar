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

import java.io.IOException;
import vogar.Action;
import vogar.Console;
import vogar.Driver;
import vogar.Mode;
import vogar.Outcome;
import vogar.Result;
import vogar.commands.Command;
import vogar.monitor.HostMonitor;
import vogar.target.CaliperRunner;

/**
 * Executes a single action and then prints the result.
 */
public class RunActionTask extends Task implements HostMonitor.Handler {
    /**
     * Assign each runner thread a unique ID. Necessary so threads don't
     * conflict when selecting a monitor port.
     */
    private final ThreadLocal<Integer> runnerThreadId = new ThreadLocal<Integer>() {
        private int next = 0;
        @Override protected synchronized Integer initialValue() {
            return next++;
        }
    };

    private final Action action;
    private final String actionName;
    private final Console console;
    private final Mode mode;
    private final int timeoutSeconds;
    private final Driver driver;
    private final Task installAction;
    private final Task installRunner;
    private Command currentCommand;
    private String lastStartedOutcome;
    private String lastFinishedOutcome;

    public RunActionTask(Action action, Console console, Mode mode, int timeoutSeconds,
            Driver driver, Task installRunner, Task installAction) {
        super("run " + action.getName());
        this.action = action;
        this.actionName = action.getName();
        this.console = console;
        this.mode = mode;
        this.timeoutSeconds = timeoutSeconds;
        this.driver = driver;
        this.installRunner = installRunner;
        this.installAction = installAction;
    }

    @Override public boolean isRunnable() {
        return installRunner.getResult() != null && installAction.getResult() != null;
    }

    @Override protected Result execute() throws Exception {
        if (installRunner.getResult() != Result.SUCCESS) {
            return installRunner.getResult();
        }
        if (installAction.getResult() != Result.SUCCESS) {
            return installAction.getResult();
        }

        console.action(actionName);

        while (true) {
            /*
             * If the target process failed midway through a set of
             * outcomes, that's okay. We pickup right after the first
             * outcome that wasn't completed.
             */
            String skipPast = lastStartedOutcome;
            lastStartedOutcome = null;

            currentCommand = mode.createActionCommand(action, skipPast, monitorPort(-1));
            try {
                currentCommand.start();
                if (timeoutSeconds != 0) {
                    currentCommand.scheduleTimeout(timeoutSeconds);
                }

                HostMonitor hostMonitor = new HostMonitor(console, this);
                boolean completedNormally = mode.useSocketMonitor()
                        ? hostMonitor.attach(monitorPort(driver.firstMonitorPort))
                        : hostMonitor.followStream(currentCommand.getInputStream());

                if (completedNormally) {
                    return Result.SUCCESS;
                }

                String earlyResultOutcome;
                boolean giveUp;

                if (lastStartedOutcome == null || lastStartedOutcome.equals(actionName)) {
                    earlyResultOutcome = actionName;
                    giveUp = true;
                } else if (!lastStartedOutcome.equals(lastFinishedOutcome)) {
                    earlyResultOutcome = lastStartedOutcome;
                    giveUp = false;
                } else {
                    continue;
                }

                driver.addEarlyResult(new Outcome(earlyResultOutcome, Result.ERROR,
                        "Action " + action + " did not complete normally.\n"
                                + "timedOut=" + currentCommand.timedOut() + "\n"
                                + "lastStartedOutcome=" + lastStartedOutcome + "\n"
                                + "lastFinishedOutcome=" + lastFinishedOutcome + "\n"
                                + "command=" + currentCommand));

                if (giveUp) {
                    return Result.ERROR;
                }
            } catch (IOException e) {
                // if the monitor breaks, assume the worst and don't retry
                driver.addEarlyResult(new Outcome(actionName, Result.ERROR, e));
                return Result.ERROR;
            } finally {
                currentCommand.destroy();
                currentCommand = null;
            }
        }
    }

    public int monitorPort(int defaultValue) {
        return driver.numRunnerThreads == 1
                ? defaultValue
                : driver.firstMonitorPort + (runnerThreadId.get() % driver.numRunnerThreads);
    }

    @Override public void start(String outcomeName, String runnerClass) {
        outcomeName = toQualifiedOutcomeName(outcomeName);
        lastStartedOutcome = outcomeName;
        // TODO add to Outcome knowledge about what class was used to run it
        if (CaliperRunner.class.getName().equals(runnerClass)) {
            if (!driver.benchmark) {
                throw new RuntimeException("you must use --benchmark when running Caliper "
                        + "benchmarks.");
            }
            console.verbose("running " + outcomeName + " with unlimited timeout");
            Command command = currentCommand;
            if (command != null && driver.smallTimeoutSeconds != 0) {
                command.scheduleTimeout(driver.smallTimeoutSeconds);
            }
            driver.recordResults = false;
        } else {
            driver.recordResults = true;
        }
    }

    @Override public void output(String outcomeName, String output) {
        outcomeName = toQualifiedOutcomeName(outcomeName);
        console.outcome(outcomeName);
        console.streamOutput(outcomeName, output);
    }

    @Override public void finish(Outcome outcome) {
        Command command = currentCommand;
        if (command != null && driver.smallTimeoutSeconds != 0) {
            command.scheduleTimeout(driver.smallTimeoutSeconds);
        }
        lastFinishedOutcome = toQualifiedOutcomeName(outcome.getName());
        // TODO: support flexible timeouts for JUnit tests
        driver.recordOutcome(new Outcome(lastFinishedOutcome, outcome.getResult(),
                outcome.getOutputLines()));
    }

    /**
     * Test suites that use main classes in the default package have lame
     * outcome names like "Clear" rather than "com.foo.Bar.Clear". In that
     * case, just replace the outcome name with the action name.
     */
    private String toQualifiedOutcomeName(String outcomeName) {
        if (actionName.endsWith("." + outcomeName)
                && !outcomeName.contains(".") && !outcomeName.contains("#")) {
            return actionName;
        } else {
            return outcomeName;
        }
    }

    @Override public void print(String string) {
        console.streamOutput(string);
    }
}
