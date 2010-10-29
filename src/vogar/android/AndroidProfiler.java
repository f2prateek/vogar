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
            writeHprofData = SamplingProfiler.getMethod("writeHprofData", File.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object profiler;

    @Override public void start(boolean profileThreadGroup, int depth, int interval) {
        try {
            Thread t = Thread.currentThread();
            Object threadSet;
            if (profileThreadGroup) {
                threadSet = newThreadGroupTheadSet.invoke(null, t.getThreadGroup());
            } else {
                threadSet = newArrayThreadSet.invoke(null, (Object)new Thread[] { t });
            }

            profiler = constructor.newInstance(depth, threadSet);
            start.invoke(profiler, interval);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override public void stop(File file) {
        try {
            stop.invoke(profiler);
            writeHprofData.invoke(profiler, file);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
