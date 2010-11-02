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

package vogar.android;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import vogar.target.Profiler;

public class AndroidProfiler extends Profiler {
    // SamplingProfiler methods
    private final Method newArrayThreadSet;
    private final Method newThreadGroupTheadSet;
    private final Constructor constructor;
    private final Method start;
    private final Method stop;
    private final Method shutdown;
    private final Method writeHprofData;
    {
        try {
            Class<?> ThreadSet = Class.forName("dalvik.system.SamplingProfiler$ThreadSet");
            Class<?> SamplingProfiler = Class.forName("dalvik.system.SamplingProfiler");
            newArrayThreadSet = SamplingProfiler.getMethod("newArrayThreadSet",
                                                           Thread[].class);
            newThreadGroupTheadSet = SamplingProfiler.getMethod("newThreadGroupTheadSet",
                                                                ThreadGroup.class);
            constructor = SamplingProfiler.getConstructor(Integer.TYPE, ThreadSet);
            start = SamplingProfiler.getMethod("start", Integer.TYPE);
            stop = SamplingProfiler.getMethod("stop");
            shutdown = SamplingProfiler.getMethod("shutdown");
            writeHprofData = SamplingProfiler.getMethod("writeHprofData", File.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Thread[] thread = new Thread[1];
    private Object profiler;
    private int interval;

    @Override public void setup(boolean profileThreadGroup, int depth, int interval) {
        try {
            Thread t = Thread.currentThread();
            thread[0] = t;
            Object threadSet;
            if (profileThreadGroup) {

                System.out.println(t.getThreadGroup());
                Thread[] threads = new Thread[10];
                t.enumerate(threads);
                System.out.println(java.util.Arrays.asList(threads));


                threadSet = newThreadGroupTheadSet.invoke(null, t.getThreadGroup());
            } else {
                threadSet = newArrayThreadSet.invoke(null, (Object)thread);
            }
            this.profiler = constructor.newInstance(depth, threadSet);
            this.interval = interval;
        } catch (Exception e) {
            throw new AssertionError(e);
        }

    }

    @Override public void start() {
        try {
            // If using the array thread set, switch to the current
            // thread.  Sometimes for timeout reasons Runners use
            // seperate threads for test execution.
            this.thread[0] = Thread.currentThread();
            start.invoke(profiler, interval);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override public void stop() {
        try {
            stop.invoke(profiler);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override public void shutdown(File file) {
        try {
            shutdown.invoke(profiler);
            writeHprofData.invoke(profiler, file);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
