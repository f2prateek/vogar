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

package vogar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import vogar.commands.Command;
import vogar.commands.CommandFailedException;
import vogar.commands.Mkdir;

/**
 * Compiles, installs, runs and reports on actions.
 */
final class Driver implements HostMonitor.Handler {
    private final File localTemp;
    private final ExpectationStore expectationStore;
    private final Mode mode;
    private final XmlReportPrinter reportPrinter;
    private final int monitorPort;
    private final HostMonitor monitor;
    private final long timeoutSeconds;
    private int successes = 0;
    private int failures = 0;
    private List<String> failureNames = new ArrayList<String>();

    private Timer actionTimeoutTimer = new Timer("action timeout", true);

    private final Map<String, Action> actions = Collections.synchronizedMap(
            new LinkedHashMap<String, Action>());
    private final Map<String, Outcome> outcomes = Collections.synchronizedMap(
            new LinkedHashMap<String, Outcome>());

    /**
     * The number of tests that weren't run because they aren't supported by
     * this runner.
     */
    private int unsupportedActions = 0;

    public Driver(File localTemp, Mode mode, ExpectationStore expectationStore,
            XmlReportPrinter reportPrinter, HostMonitor monitor, int monitorPort,
            long timeoutSeconds) {
        this.localTemp = localTemp;
        this.expectationStore = expectationStore;
        this.mode = mode;
        this.reportPrinter = reportPrinter;
        this.monitor = monitor;
        this.monitorPort = monitorPort;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Builds and executes the actions in the given files.
     */
    public void buildAndRun(Collection<File> files, Collection<String> classes) {
        if (!actions.isEmpty()) {
            throw new IllegalStateException("Drivers are not reusable");
        }

        new Mkdir().mkdirs(localTemp);

        filesToActions(files);
        classesToActions(classes);

        if (actions.isEmpty()) {
            Console.getInstance().info("Nothing to do.");
            return;
        }

        Console.getInstance().info("Actions: " + actions.size());
        final long t0 = System.currentTimeMillis();

        // mode.prepare before mode.buildAndInstall to ensure the runner is
        // built. packaging of activity APK files needs the runner along with
        // the action-specific files.
        mode.prepare();

        // build and install actions in a background thread. Using lots of
        // threads helps for packages that contain many unsupported actions
        final BlockingQueue<Action> readyToRun = new ArrayBlockingQueue<Action>(4);

        ExecutorService builders = Threads.threadPerCpuExecutor();
        int t = 0;

        for (final Action action : actions.values()) {
            final String name = action.getName();
            final int runIndex = t++;
            builders.submit(new Runnable() {
                public void run() {
                    try {
                        Console.getInstance().verbose("installing action " + runIndex + "; "
                                + readyToRun.size() + " are runnable");

                        if (expectationStore.get(name).getResult() == Result.UNSUPPORTED) {
                            outcomes.put(name, new Outcome(name, Result.UNSUPPORTED,
                                    "Unsupported according to expectations file"));

                        } else {
                            Outcome outcome = mode.buildAndInstall(action);
                            if (outcome != null) {
                                outcomes.put(name, outcome);
                            }
                        }

                        readyToRun.put(action);
                    } catch (InterruptedException e) {
                        outcomes.put(name, new Outcome(name, Result.ERROR, e));
                    }
                }
            });
        }
        builders.shutdown();

        for (int i = 0; i < actions.size(); i++) {
            Console.getInstance().verbose("executing action " + i + "; "
                    + readyToRun.size() + " are ready to run");

            // if it takes 5 minutes for build and install, something is broken
            Action action;
            try {
                action = readyToRun.poll(5 * 60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpected interruption waiting for build and install", e);
            }

            if (action == null) {
                outcome(new Outcome("vogar.Vogar", Result.ERROR,
                        "Expected " + actions.size() + " actions but found only " + i));
                break;
            }

            execute(action);
            mode.cleanup(action);
        }

        if (reportPrinter != null) {
            Console.getInstance().info("Printing XML Reports... ");
            int numFiles = reportPrinter.generateReports(outcomes.values());
            Console.getInstance().info(numFiles + " XML files written.");
        }

        mode.shutdown();
        final long t1 = System.currentTimeMillis();

        if (failures > 0 || unsupportedActions > 0) {
            Collections.sort(failureNames);
            Console.getInstance().summarizeFailures(failureNames);
            Console.getInstance().info(String.format("Outcomes: %s. Passed: %d, Failed: %d, Skipped: %d. Took %s.",
                    (successes + failures), successes, failures, unsupportedActions,
                    TimeUtilities.msToString(t1 - t0)));
        } else {
            Console.getInstance().info(String.format("Outcomes: %s. All successful. Took %s.",
                    (successes + failures), TimeUtilities.msToString(t1 - t0)));
        }
    }

    private void classesToActions(Collection<String> classes) {
        for (String clazz : classes) {
            Action action = new Action(clazz, clazz, null, null, null);
            actions.put(action.getName(), action);
        }
    }

    private void filesToActions(Collection<File> files) {
        for (File file : files) {
            for (Action action : new ActionFinder().findActions(file)) {
                actions.put(action.getName(), action);
            }
        }
    }

    /**
     * Executes a single action and then prints the result.
     */
    private void execute(final Action action) {
        Console.getInstance().action(action.getName());

        Outcome earlyFailure = outcomes.get(action.getName());
        if (earlyFailure == null) {
            final Command command = mode.createActionCommand(action);
            Future<List<String>> consoleOut = command.executeLater();
            final AtomicReference<Result> result = new AtomicReference<Result>();

            if (timeoutSeconds != 0) {
                actionTimeoutTimer.schedule(new TimerTask() {
                    @Override public void run() {
                        if (result.compareAndSet(null, Result.EXEC_TIMEOUT)) {
                            Console.getInstance().verbose("killing " + action.getName() + " because it "
                                    + "timed out after " + timeoutSeconds + " seconds");
                            command.destroy();
                        }
                    }
                }, timeoutSeconds * 1000);
            }

            boolean completedNormally = monitor.monitor(monitorPort, this);
            if (completedNormally) {
                if (result.compareAndSet(null, Result.SUCCESS)) {
                    command.destroy();
                }
                return; // outcomes will have been reported via outcome()
            }

            if (result.compareAndSet(null, Result.ERROR)) {
                Console.getInstance().verbose("killing " + action.getName() + " because it could not be monitored.");
                command.destroy();
            }
            try {
                earlyFailure = new Outcome(action.getName(), action.getName(),
                        result.get(), consoleOut.get());
            } catch (Exception e) {
                if (e.getCause() instanceof CommandFailedException) {
                    earlyFailure = new Outcome(action.getName(), action.getName(), result.get(),
                            ((CommandFailedException) e.getCause()).getOutputLines());
                } else {
                    earlyFailure = new Outcome(action.getName(), result.get(), e);
                }
            }
        }

        if (earlyFailure.getResult() == Result.UNSUPPORTED) {
            Console.getInstance().verbose("skipping " + action.getName());
            unsupportedActions++;
        } else {
            for (String line : earlyFailure.getOutputLines()) {
                Console.getInstance().streamOutput(line + "\n");
            }
            outcome(earlyFailure);
        }
    }

    public void outcome(Outcome outcome) {
        outcomes.put(outcome.getName(), outcome);
        Expectation expectation = expectationStore.get(outcome);
        boolean ok = expectation.matches(outcome);
        if (ok) {
            successes++;
        } else {
            failures++;
            failureNames.add(outcome.getName());
        }
        Console.getInstance().outcome(outcome.getName());
        Console.getInstance().printResult(outcome.getResult(), ok);
    }

    public void output(String outcomeName, String output) {
        Console.getInstance().outcome(outcomeName);
        Console.getInstance().streamOutput(output);
    }
}
