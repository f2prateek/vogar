package vogar;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

import vogar.commands.AndroidSdk;

public class DeviceCacheFileInterface implements CacheFileInterface {
    private final File CACHE_ROOT = new File("/sdcard/tmp/vogar-md5-cache/");
    private final AndroidSdk androidSdk;
    private Set<File> cachedFiles;

    public DeviceCacheFileInterface(AndroidSdk androidSdk) {
        this.androidSdk = androidSdk;
        // filled lazily
        this.cachedFiles = null;
    }

    public boolean existsInCache(String key) {
        if (cachedFiles == null) {
            try {
                cachedFiles = androidSdk.ls(CACHE_ROOT);
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

    public void prepareDestination(File destination) {
        androidSdk.mkdirs(destination.getParentFile());
    }
}
