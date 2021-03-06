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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vogar.android.DeviceFilesystem;
import vogar.commands.Command;

/**
 * Runs actions on a remote host using SSH.
 */
public final class SshTarget extends Target {
    private final Log log;
    private final String host;
    private final int port;
    private final DeviceFilesystem deviceFilesystem;

    public SshTarget(String hostAndPort, Log log) {
        this.log = log;
        int colon = hostAndPort.indexOf(":");
        if (colon != -1) {
            host = hostAndPort.substring(0, colon);
            port = Integer.parseInt(hostAndPort.substring(colon + 1));
        } else {
            host = hostAndPort;
            port = 22;
        }
        deviceFilesystem = new DeviceFilesystem(log, "ssh", "-p", Integer.toString(port), host, "-C");
    }

    @Override public File defaultDeviceDir() {
        return new File("/data/local/tmp/vogar");
    }

    @Override public List<String> targetProcessPrefix(File workingDirectory) {
        // TODO: drop the LD_LIBRARY_PATH env value; it's needed for third-parth sshd servers
        return Arrays.asList("ssh", "-p", Integer.toString(port), host, "-C",
                "cd", workingDirectory.getAbsolutePath(), "&&",
                "LD_LIBRARY_PATH=/vendor/lib:/system/lib");
    }

    @Override public void await(File nonEmptyDirectory) {
    }

    @Override public void rm(File file) {
        new Command.Builder(log)
                .args("ssh", "-p", Integer.toString(port), host, "-C", "rm", "-r", file.getPath())
                .permitNonZeroExitStatus()
                .execute();
    }

    @Override public String getDeviceUserName() {
        // TODO: move this to device set up
        // The default environment doesn't include $USER, so dalvikvm doesn't set "user.name".
        // DeviceDalvikVm uses this to set "user.name" manually with -D.
        String line = new Command(log, "ssh", "-p", Integer.toString(port), host, "-C", "id")
                .execute().get(0);
        Matcher m = Pattern.compile("uid=\\d+\\((\\S+)\\) gid=\\d+\\(\\S+\\)").matcher(line);
        return m.matches() ? m.group(1) : "root";
    }

    @Override public void mkdirs(File file) {
        deviceFilesystem.mkdirs(file);
    }

    @Override public void forwardTcp(final int port) {
        try {
            new Command(log, "ssh", "-p", Integer.toString(port), host,
                    "-L", port + ":" + host + ":" + port, "-N").start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public void push(File local, File remote) {
        new Command(log, "scp", "-r", "-P", Integer.toString(port),
                local.getPath(), host + ":" + remote.getPath()).execute();
    }

    @Override public List<File> ls(File directory) throws FileNotFoundException {
        return deviceFilesystem.ls(directory);
    }

    @Override public void pull(File remote, File local) {
        new Command(log, "scp", "-r", "-P", Integer.toString(port),
                host + ":" + remote.getPath(), local.getPath()).execute();
    }
}
