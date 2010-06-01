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
import java.util.HashSet;
import java.util.Set;

import vogar.commands.AndroidSdk;

public class DeviceFileCache implements FileCache {
    private final File CACHE_ROOT = new File("/sdcard/tmp/vogar-md5-cache/");
    private final AndroidSdk androidSdk;
    private Set<File> cachedFiles;

    public DeviceFileCache(AndroidSdk androidSdk) {
        this.androidSdk = androidSdk;
        // filled lazily
        this.cachedFiles = null;
    }

    public boolean existsInCache(String key) {
        if (cachedFiles == null) {
            try {
                cachedFiles = androidSdk.ls(CACHE_ROOT);
                Console.getInstance().verbose("indexed on-device cache: " + cachedFiles.size()
                        + " entries.");
            } catch (FileNotFoundException e) {
                // CACHE_ROOT probably just hasn't been created yet.
                cachedFiles = new HashSet<File>();
            }
        }
        File cachedFile = new File(CACHE_ROOT, key);
        return cachedFiles.contains(cachedFile);
    }

    public void copyFromCache(String key, File destination) {
        File cachedFile = new File(CACHE_ROOT, key);
        androidSdk.cp(cachedFile, destination);
    }

    public void copyToCache(File source, String key) {
        File cachedFile = new File(CACHE_ROOT, key);
        androidSdk.mkdirs(CACHE_ROOT);
        // Copy it onto the same file system first, then atomically move it into place.
        // That way, if we fail, we don't leave anything dangerous lying around.
        File temporary = new File(cachedFile + ".tmp");
        androidSdk.cp(source, temporary);
        androidSdk.mv(temporary, cachedFile);
    }
}
