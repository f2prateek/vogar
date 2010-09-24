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

package vogar.target;

import java.io.File;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * This class resets the VM to a relatively pristine state. Useful to defend
 * against tests that muck with system properties and other global state.
 */
public final class TestEnvironment {

    private final Properties systemProperties;

    public TestEnvironment() {
        systemProperties = new Properties();
        systemProperties.putAll(System.getProperties());

        String tmpDir = systemProperties.getProperty("java.io.tmpdir");
        if (tmpDir == null) {
            throw new NullPointerException("tmpDir == null");
        }

        // paths with writable values for testing
        String userHome = tmpDir + "/user.home";
        String javaHome = tmpDir + "/java.home";
        String userDir = tmpDir + "/user.dir";
        makeDirectory(new File(userHome));
        makeDirectory(new File(javaHome));
        makeDirectory(new File(userDir));
        systemProperties.put("java.home", javaHome);
        systemProperties.put("user.dir", userDir);
        systemProperties.put("user.home", userHome);
    }

    private void makeDirectory(File path) {
        boolean success;
        if (!path.exists()) {
            success = path.mkdirs();
        } else if (!path.isDirectory()) {
            success = path.delete() && path.mkdirs();
        } else {
            success = true;
        }

        if (!success) {
            throw new RuntimeException("Failed to make directory " + path);
        }
    }

    public void reset() {
        Properties propertiesCopy = new Properties();
        propertiesCopy.putAll(systemProperties);
        System.setProperties(propertiesCopy);
        Locale.setDefault(Locale.US);
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        resetPreferences(Preferences.systemRoot());
        resetPreferences(Preferences.userRoot());
    }

    private static void resetPreferences(Preferences root) {
        try {
            for (String child : root.childrenNames()) {
                root.node(child).removeNode();
            }
            root.clear();
            root.flush();
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }
}
