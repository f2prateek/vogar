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
import vogar.commands.AndroidSdk;

class EnvironmentDevice extends Environment {
    final AndroidSdk androidSdk;
    final File runnerDir;
    final File vogarTemp;
    final File dalvikCache;
    final int monitorPort;

    EnvironmentDevice(boolean cleanBefore, boolean cleanAfter, Integer debugPort, int monitorPort,
            File localTemp, File runnerDir, AndroidSdk androidSdk) {
        super(cleanBefore, cleanAfter, debugPort, localTemp);
        this.androidSdk = androidSdk;
        this.runnerDir = runnerDir;
        this.vogarTemp = new File(runnerDir, "tmp");
        this.dalvikCache = new File(runnerDir.getParentFile(), "dalvik-cache");
        this.monitorPort = monitorPort;
    }

    /**
     * Returns an environment variable assignment to configure where the VM will
     * store its dexopt files. This must be set on production devices and is
     * optional for development devices.
     */
    public String getAndroidData() {
        // The VM wants the parent directory of a directory named "dalvik-cache"
        return "ANDROID_DATA=" + dalvikCache.getParentFile();
    }

    @Override void prepare() {
        androidSdk.waitForDevice();
        androidSdk.waitForNonEmptyDirectory(runnerDir.getParentFile(), 5 * 60);
        if (cleanBefore) {
            androidSdk.rm(runnerDir);
        }
        androidSdk.mkdir(runnerDir);
        androidSdk.mkdir(vogarTemp);
        androidSdk.mkdir(dalvikCache);
        androidSdk.forwardTcp(monitorPort, monitorPort);
        if (debugPort != null) {
            androidSdk.forwardTcp(debugPort, debugPort);
        }
    }

    @Override protected void prepareUserDir(Action action) {
        File actionClassesDirOnDevice = actionClassesDirOnDevice(action);
        androidSdk.mkdir(actionClassesDirOnDevice);
        File resourcesDirectory = action.getResourcesDirectory();
        if (resourcesDirectory != null) {
            androidSdk.push(resourcesDirectory, actionClassesDirOnDevice);
        }
        action.setUserDir(actionClassesDirOnDevice);
    }

    private File actionClassesDirOnDevice(Action action) {
        return new File(runnerDir, action.getName());
    }

    @Override void cleanup(Action action) {
        super.cleanup(action);
        if (cleanAfter) {
            androidSdk.rm(actionClassesDirOnDevice(action));
        }
    }

    @Override void shutdown() {
        super.shutdown();
        if (cleanAfter) {
            androidSdk.rm(runnerDir);
        }
    }
}
