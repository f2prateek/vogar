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

import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import vogar.Action;
import vogar.Run;
import vogar.commands.VmCommandBuilder;

/**
 * Execute actions using the app_process command on using an Android device or emulator.
 */
public final class AppProcessMode extends DeviceDalvikVm {
    public AppProcessMode(Run run) {
        super(run);
    }

    @Override public VmCommandBuilder newVmCommandBuilder(Action action, File workingDirectory) {
        List<String> vmCommand = new ArrayList<String>();
        vmCommand.addAll(run.target.targetProcessPrefix(workingDirectory));
        vmCommand.add(run.getAndroidData());
        Iterables.addAll(vmCommand, run.invokeWith());
        vmCommand.add(run.vmCommand);

        return new VmCommandBuilder(run.log)
                .vmCommand(vmCommand)
                .vmArgs(action.getUserDir().getPath())
                .classpathViaProperty(true)
                .maxLength(1024);
    }
}
