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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import vogar.Console;
import vogar.util.Threads;

public final class TaskQueue {
    private static final int FOREVER = 60 * 60 * 24 * 28; // four weeks
    @Inject Console console;
    private int runningTasks;
    private final LinkedList<Task> runnableTasks = new LinkedList<Task>();
    private final LinkedList<Task> blockedTasks = new LinkedList<Task>();

    /**
     * Adds a task to the queue.
     */
    public synchronized void enqueue(Task task) {
        if (task.isRunnable()) {
            runnableTasks.add(task);
        } else {
            blockedTasks.add(task);
        }
    }

    public void runTasks() {
        ExecutorService runners = Threads.threadPerCpuExecutor(console, "TaskQueue");
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            runners.execute(new Runnable() {
                @Override public void run() {
                    while (runOneTask()) {
                    }
                }
            });
        }

        runners.shutdown();
        try {
            runners.awaitTermination(FOREVER, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new AssertionError();
        }
    }

    /**
     * Returns the tasks that cannot be executed.
     */
    public synchronized List<Task> getBlockedTasks() {
        return new ArrayList<Task>(blockedTasks);
    }

    /**
     * Returns all tasks currently enqueued.
     */
    public synchronized List<Task> getTasks() {
        ArrayList<Task> result = new ArrayList<Task>();
        result.addAll(runnableTasks);
        result.addAll(blockedTasks);
        return result;
    }

    private boolean runOneTask() {
        Task task = takeTask();
        if (task == null) {
            return false;
        }
        String threadName = Thread.currentThread().getName();
        Thread.currentThread().setName(task.toString());
        try {
            task.run(console);
        } finally {
            doneTask();
            Thread.currentThread().setName(threadName);
        }
        return true;
    }

    private synchronized Task takeTask() {
        while (true) {
            Task task = runnableTasks.poll();
            if (task != null) {
                runningTasks++;
                return task;
            }

            if (isExhausted()) {
                return null;
            }

            try {
                wait();
            } catch (InterruptedException e) {
                throw new AssertionError();
            }
        }
    }

    private synchronized void doneTask() {
        runningTasks--;

        for (Iterator<Task> it = blockedTasks.iterator(); it.hasNext(); ) {
            Task potentiallyUnblocked = it.next();
            if (potentiallyUnblocked.isRunnable()) {
                it.remove();
                runnableTasks.add(potentiallyUnblocked);
                notifyAll();
            }
        }

        if (isExhausted()) {
            notifyAll();
        }
    }

    /**
     * Returns true if there are no tasks to run and no tasks currently running.
     */
    private boolean isExhausted() {
        return runnableTasks.isEmpty() && runningTasks == 0;
    }
}
