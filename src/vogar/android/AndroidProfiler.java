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
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import vogar.target.Profiler;

public class AndroidProfiler extends Profiler {
    // SamplingProfiler methods
    private final Method newArrayThreadSet;
    private final Method newThreadGroupTheadSet;
    private final Constructor newThreadSet;
    private final Method start;
    private final Method stop;
    private final Method shutdown;
    private final Constructor<?> newAsciiHprofWriter;
    private final Method getHprofData;
    private final Method write;

    public AndroidProfiler() throws Exception {
        Class<?> ThreadSet = Class.forName("dalvik.system.SamplingProfiler$ThreadSet");
        Class<?> SamplingProfiler = Class.forName("dalvik.system.SamplingProfiler");
        Class<?> HprofData = Class.forName("dalvik.system.SamplingProfiler$HprofData");
        Class<?> Writer = Class.forName("dalvik.system.SamplingProfiler$AsciiHprofWriter");
        newArrayThreadSet = SamplingProfiler.getMethod("newArrayThreadSet", Thread[].class);
        newThreadGroupTheadSet = SamplingProfiler.getMethod("newThreadGroupTheadSet",
                                                            ThreadGroup.class);
        newThreadSet = SamplingProfiler.getConstructor(Integer.TYPE, ThreadSet);
        start = SamplingProfiler.getMethod("start", Integer.TYPE);
        stop = SamplingProfiler.getMethod("stop");
        shutdown = SamplingProfiler.getMethod("shutdown");
        getHprofData = SamplingProfiler.getMethod("getHprofData");
        newAsciiHprofWriter = Writer.getConstructor(HprofData, OutputStream.class);
        write = Writer.getMethod("write");
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
                threadSet = newThreadGroupTheadSet.invoke(null, t.getThreadGroup());
            } else {
                threadSet = newArrayThreadSet.invoke(null, (Object)thread);
            }
            this.profiler = newThreadSet.newInstance(depth, threadSet);
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

            FileOutputStream out = new FileOutputStream(file);
            Object writer = newAsciiHprofWriter.newInstance(getHprofData.invoke(profiler), out);
            write.invoke(writer);
            out.close();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
