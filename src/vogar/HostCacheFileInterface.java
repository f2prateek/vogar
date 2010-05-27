package vogar;

import java.io.File;
import java.util.List;

import vogar.commands.Command;

public class HostCacheFileInterface implements CacheFileInterface {
    private final File CACHE_ROOT = new File("/tmp/vogar-md5-cache/");

    private void mkdirs(File dir) {
        dir.mkdirs();
        if (!(dir.exists() && dir.isDirectory())) {
            throw new RuntimeException("Couldn't create directory " + dir.getPath());
        }
    }

    public void prepareDestination(File destination) {
        mkdirs(destination.getParentFile());
    }

    private void cp(File source, File destination) {
        List<String> rawResult = new Command.Builder().args("cp", source, destination).execute();
        // A successful copy returns no results.
        if (!rawResult.isEmpty()) {
            throw new RuntimeException("Couldn't copy " + source + " to " + destination
                    + ": " + rawResult.get(0));
        }
    }

    private void mv(File source, File destination) {
        List<String> rawResult = new Command.Builder().args("mv", source, destination).execute();
        // A successful move returns no results.
        if (!rawResult.isEmpty()) {
            throw new RuntimeException("Couldn't move " + source + " to " + destination
                    + ": " + rawResult.get(0));
        }
    }

    public void copyFromCache(String key, File destination) {
        File cachedFile = new File(CACHE_ROOT, key);
        cp(cachedFile, destination);
    }

    public void copyToCache(File source, String key) {
        File cachedFile = new File(CACHE_ROOT, key);
        mkdirs(CACHE_ROOT);
        // Copy it onto the same file system first, then atomically move it into place.
        // That way, if we fail, we don't leave anything dangerous lying around.
        File temporary = new File(cachedFile + ".tmp");
        cp(source, temporary);
        mv(temporary, cachedFile);
    }

    public boolean existsInCache(String key) {
        return new File(CACHE_ROOT, key).exists();
    }
}
