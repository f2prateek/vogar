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

package vogar.android;

import java.io.File;
import vogar.Result;
import vogar.Run;
import vogar.Vogar;
import vogar.tasks.Task;

public final class PrepareDeviceTask extends Task {
    private final Run run;
    private final AndroidSdk androidSdk;

    public PrepareDeviceTask(Run run) {
        super("prepare device");
        this.run = run;
        this.androidSdk = run.androidSdk;
    }

    @Override protected Result execute() throws Exception {
        androidSdk.waitForDevice();
        // Even if runner dir is /vogar/run, the grandparent will be / (and non-null)
        androidSdk.waitForNonEmptyDirectory(run.runnerDir.getParentFile().getParentFile(), 5 * 60);
        androidSdk.remount();
        if (run.cleanBefore) {
            androidSdk.rm(run.runnerDir);
        }
        androidSdk.mkdirs(run.runnerDir);
        androidSdk.mkdir(run.vogarTemp());
        androidSdk.mkdir(run.dalvikCache());
        for (int i = 0; i < run.numRunners; i++) {
            androidSdk.forwardTcp(run.firstMonitorPort + i, run.firstMonitorPort + i);
        }
        if (run.debugPort != null) {
            androidSdk.forwardTcp(run.debugPort, run.debugPort);
        }
        androidSdk.mkdirs(run.deviceUserHome);

        // push ~/.caliperrc to device if found
        File hostCaliperRc = Vogar.dotFile(".caliperrc");
        if (hostCaliperRc.exists()) {
            androidSdk.push(hostCaliperRc, new File(run.deviceUserHome, ".caliperrc"));
        }
        return Result.SUCCESS;
    }
}
