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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ResponseCache;
import java.util.Locale;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import vogar.util.IoUtils;

/**
 * This class resets the VM to a relatively pristine state. Useful to defend
 * against tests that muck with system properties and other global state.
 */
public final class TestEnvironment {

    private final HostnameVerifier defaultHostnameVerifier;
    private final SSLSocketFactory defaultSSLSocketFactory;

    private static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version"); 
    private static final String JAVA_VM_INFO = System.getProperty("java.vm.info"); 
    private static final String JAVA_VM_VERSION = System.getProperty("java.vm.version"); 
    private static final String JAVA_VM_VENDOR = System.getProperty("java.vm.vendor"); 
    private static final String JAVA_VM_NAME = System.getProperty("java.vm.name"); 

    public TestEnvironment() {
        System.setProperties(null); // Reset.
        String tmpDir = System.getProperty("java.io.tmpdir");
        String userHome = System.getProperty("user.home");
        String userDir = System.getProperty("user.dir");
        if (tmpDir == null || userHome == null || userDir == null) {
            throw new NullPointerException("java.io.tmpdir=" + tmpDir + ", user.home="
                    + userHome + "user.dir=" + userDir);
        }

        defaultHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        defaultSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();

        disableSecurity();
    }

    public void reset() {
        // Reset system properties.
        System.setProperties(null);

        if (JAVA_RUNTIME_VERSION != null) {
            System.setProperty("java.runtime.version", JAVA_RUNTIME_VERSION);
        }
        if (JAVA_VM_INFO != null) {
            System.setProperty("java.vm.info", JAVA_VM_INFO);
        }
        if (JAVA_VM_VERSION != null) {
            System.setProperty("java.vm.version", JAVA_VM_VERSION);
        }
        if (JAVA_VM_VENDOR != null) {
            System.setProperty("java.vm.vendor", JAVA_VM_VENDOR);
        }
        if (JAVA_VM_NAME != null) {
            System.setProperty("java.vm.name", JAVA_VM_NAME);
        }

        // Require writable java.home and user.dir directories for preferences
        String tmpDir = System.getProperty("java.io.tmpdir");
        if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
            String javaHome = tmpDir + "/java.home";
            IoUtils.safeMkdirs(new File(javaHome));
            System.setProperty("java.home", javaHome);
        }
        String userHome = System.getProperty("user.home");
        if (userHome.length() == 0) {
            userHome = tmpDir + "/user.home";
            IoUtils.safeMkdirs(new File(userHome));
            System.setProperty("user.home", userHome);
        }

        // Localization
        Locale.setDefault(Locale.US);
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

        // Preferences
        // Temporarily silence the java.util.prefs logger, which otherwise emits
        // an unactionable warning. See RI bug 4751540.
        Logger loggerToMute = Logger.getLogger("java.util.prefs");
        boolean usedParentHandlers = loggerToMute.getUseParentHandlers();
        loggerToMute.setUseParentHandlers(false);
        try {
            // resetPreferences(Preferences.systemRoot());
            resetPreferences(Preferences.userRoot());
        } finally {
            loggerToMute.setUseParentHandlers(usedParentHandlers);
        }

        // HttpURLConnection
        Authenticator.setDefault(null);
        CookieHandler.setDefault(null);
        ResponseCache.setDefault(null);
        HttpsURLConnection.setDefaultHostnameVerifier(defaultHostnameVerifier);
        HttpsURLConnection.setDefaultSSLSocketFactory(defaultSSLSocketFactory);

        // Logging
        LogManager.getLogManager().reset();
        Logger.getLogger("").addHandler(new ConsoleHandler());

        // Cleanup to force CloseGuard warnings etc
        System.gc();
        System.runFinalization();
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

    /** A class that always returns TRUE. */
    @SuppressWarnings("serial")
    public static class LyingMap extends HashMap<Object, Boolean> {
        @Override
        public Boolean get(Object key) {
            return Boolean.TRUE;
        }
    }

    /**
     * Does what is necessary to disable security checks for testing security-related classes.
     */
    @SuppressWarnings("unchecked")
    private static void disableSecurity() {
        try {
            Class<?> securityBrokerClass = Class.forName("javax.crypto.JceSecurity");

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);

            Field verifyMapField = securityBrokerClass.getDeclaredField("verificationResults");
            modifiersField.setInt(verifyMapField, verifyMapField.getModifiers() & ~Modifier.FINAL);
            verifyMapField.setAccessible(true);
            verifyMapField.set(null, new LyingMap());

            Field restrictedField = securityBrokerClass.getDeclaredField("isRestricted");
            restrictedField.setAccessible(true);
            restrictedField.set(null, Boolean.FALSE);
        } catch (Exception ignored) {
        }
    }
}
