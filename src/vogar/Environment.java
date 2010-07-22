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

package vogar;

import java.io.File;
import vogar.commands.Rm;
import vogar.monitor.HostMonitor;
import vogar.monitor.SocketHostMonitor;
import vogar.util.Strings;

/**
 * A target runtime environment such as a remote device or the local host
 */
public abstract class Environment {
    final boolean cleanBefore;
    final boolean cleanAfter;
    final Integer debugPort;
    private final File localTemp;
    private final int monitorTimeoutSeconds;

    protected Environment(boolean cleanBefore, boolean cleanAfter, Integer debugPort,
            File localTemp, int monitorTimeoutSeconds) {
        this.cleanBefore = cleanBefore;
        this.cleanAfter = cleanAfter;
        this.debugPort = debugPort;
        this.localTemp = localTemp;
        this.monitorTimeoutSeconds = monitorTimeoutSeconds;
    }

    public boolean cleanBefore() {
        return cleanBefore;
    }

    public boolean cleanAfter() {
        return cleanAfter;
    }

    public Integer getDebugPort() {
        return debugPort;
    }

    /**
     * Initializes the temporary directories and harness necessary to run
     * actions.
     */
    public abstract void prepare();

    /**
     * Prepares the directory from which the action will be executed. Some
     * actions expect to read data files from the current working directory;
     * this step should ensure such files are available.
     */
    public abstract void prepareUserDir(Action action);

    /**
     * Deletes files and releases any resources required for the execution of
     * the given action.
     */
    public void cleanup(Action action) {
        if (cleanAfter) {
            Console.getInstance().verbose("clean " + action.getName());
            new Rm().directoryTree(file(action));
        }
    }

    protected HostMonitor newHostMonitor(int monitorPort) {
        return new SocketHostMonitor(monitorTimeoutSeconds, monitorPort);
    }

    public final File file(Object... path) {
        return new File(localTemp + "/" + Strings.join(path, "/"));
    }

    public final File hostJar(Object nameOrAction) {
        return file(nameOrAction, nameOrAction + ".jar");
    }

    public final File actionUserDir(Action action) {
        File testTemp = new File(localTemp, "userDir");
        return new File(testTemp, action.getName());
    }

    public void shutdown() {
        if (cleanAfter) {
            new Rm().directoryTree(localTemp);
        }
    }
}
