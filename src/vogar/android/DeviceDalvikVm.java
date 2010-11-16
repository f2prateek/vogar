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

package vogar.android;

import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.Action;
import vogar.Classpath;
import vogar.Console;
import vogar.Vm;
import vogar.Vogar;

/**
 * Execute actions on a Dalvik VM using an Android device or emulator.
 */
public final class DeviceDalvikVm extends Vm {

    @Inject @Named("benchmark") boolean fastMode;
    @Inject @Named("deviceUserHome") File deviceUserHome;

    private EnvironmentDevice getEnvironmentDevice() {
        return (EnvironmentDevice) environment;
    }

    private AndroidSdk getSdk() {
        return getEnvironmentDevice().androidSdk;
    }

    @Override protected void prepare() {
        super.prepare();
        getSdk().mkdirs(deviceUserHome);

        // push ~/.caliperrc to device if found
        File hostCaliperRc = Vogar.dotFile(".caliperrc");
        if (hostCaliperRc.exists()) {
            getSdk().push(hostCaliperRc, new File(deviceUserHome, ".caliperrc"));
        }
    }

    @Override protected void installRunner() {
        // dex everything on the classpath and push it to the device.
        for (File classpathElement : classpath.getElements()) {
            dexAndPush(getSdk().basenameOfJar(classpathElement), classpathElement, false);
        }
    }

    @Override protected void postCompile(Action action, File jar) {
        dexAndPush(action.getName(), jar, true);
    }

    private void dexAndPush(String name, File jar, boolean forAction) {
        Console.getInstance().verbose("dex and push " + name);

        // make the local dex (inside a jar)
        File localDex = environment.file(name, name + ".dx.jar");
        Classpath cp = Classpath.of(jar);
        if (fastMode && forAction) {
            cp.addAll(this.classpath);
        }
        getSdk().dex(localDex, cp);

        // post the local dex to the device
        getSdk().push(localDex, deviceDexFile(name));
    }

    private File deviceDexFile(String name) {
        return new File(getEnvironmentDevice().runnerDir, name + ".jar");
    }

    @Override protected VmCommandBuilder newVmCommandBuilder() {
        List<String> vmCommand = new ArrayList<String>();
        Collections.addAll(vmCommand, "adb", "shell", getEnvironmentDevice().getAndroidData());
        Iterables.addAll(vmCommand, invokeWith());
        vmCommand.add("dalvikvm");

        // If you edit this, see also HostDalvikVm...
        VmCommandBuilder vmCommandBuilder = new VmCommandBuilder()
                .vmCommand(vmCommand)
                .vmArgs("-Duser.home=" + deviceUserHome)
                .vmArgs("-Duser.name=" + AndroidSdk.getDeviceUserName())
                .vmArgs("-Duser.language=en")
                .vmArgs("-Duser.region=US")
                .vmArgs("-Djavax.net.ssl.trustStore=/system/etc/security/cacerts.bks")
                .maxLength(1024);
        if (!fastMode) {
            vmCommandBuilder.vmArgs("-Xverify:none");
            vmCommandBuilder.vmArgs("-Xdexopt:none");
            vmCommandBuilder.vmArgs("-Xcheck:jni");
        }
        // dalvikvm defaults to no limit, but the framework sets the limit at 2000.
        vmCommandBuilder.vmArgs("-Xjnigreflimit:2000");
        return vmCommandBuilder;
    }

    @Override protected Classpath getRuntimeClasspath(Action action) {
        Classpath result = new Classpath();
        result.addAll(deviceDexFile(action.getName()));
        if (!fastMode) {
            for (File classpathElement : classpath.getElements()) {
                result.addAll(deviceDexFile(getSdk().basenameOfJar(classpathElement)));
            }
        }
        return result;
    }
}
