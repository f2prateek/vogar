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

import vogar.android.AndroidProfiler;
import java.io.File;

public abstract class Profiler {
    public static Profiler getInstance() {
        try {
            return new AndroidProfiler();
        } catch (Exception e) {
            // will fail if AndroidProfiler is unsupported such as in
            // mode jvm
            return null;
        }
    }
    public abstract void start(boolean profileThreadGroup, int depth, int interval);
    public abstract void stop(File file);
}
