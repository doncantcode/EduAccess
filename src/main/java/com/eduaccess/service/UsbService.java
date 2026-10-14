package com.eduaccess.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds USB drives (or any other drive except the system drive) and copies cached PDFs onto them.
 * Files go into an "EduAccess Resources" folder so they never mix with the user's own files.
 */
public final class UsbService {

    static final String FOLDER_NAME = "EduAccess Resources";
    private static final long MEGABYTE = 1024L * 1024L;

    /** Drive roots that can take a copy. The system drive is excluded. */
    public List<Path> detectTargets() {
        Path systemRoot = Path.of(System.getProperty("user.home")).toAbsolutePath().getRoot();
        List<Path> targets = new ArrayList<>();
        for (File root : File.listRoots()) {
            Path path = root.toPath();
            if (path.equals(systemRoot)) {
                continue;
            }
            if (Files.isDirectory(path) && Files.isWritable(path)) {
                targets.add(path);
            }
        }
        return targets;
    }

    /**
     * Copies the source files into {@code targetRoot}/EduAccess Resources. Existing names get a numbered suffix,
     * so nothing is overwritten. Missing or unreadable files are reported, and the rest still copy.
     */
    public CopyResult copyTo(List<Path> sources, Path targetRoot) {
        if (!Files.isDirectory(targetRoot) || !Files.isWritable(targetRoot)) {
            throw new ServiceException("The selected drive or folder is not writable.");
        }
        Path destination = targetRoot.resolve(FOLDER_NAME);
        List<Path> copied = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        try {
            Files.createDirectories(destination);

            List<Path> readable = new ArrayList<>();
            long bytesNeeded = 0;
            for (Path source : sources) {
                if (Files.isRegularFile(source) && Files.isReadable(source)) {
                    readable.add(source);
                    bytesNeeded += Files.size(source);
                } else {
                    failed.add(source.getFileName() + " (file not found)");
                }
            }

            long freeBytes = Files.getFileStore(destination).getUsableSpace();
            if (bytesNeeded > freeBytes) {
                throw new ServiceException("Not enough free space: the files need "
                        + megabytes(bytesNeeded) + " MB and the drive has " + megabytes(freeBytes) + " MB free.");
            }

            for (Path source : readable) {
                try {
                    copied.add(Files.copy(source, uniqueTarget(destination, source.getFileName().toString())));
                } catch (IOException e) {
                    failed.add(source.getFileName() + " (" + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            throw new ServiceException("Copy failed: " + e.getMessage(), e);
        }
        return new CopyResult(destination, copied, failed);
    }

    /** Returns dir/name, or dir/name (2), dir/name (3), ... when that name is taken. */
    static Path uniqueTarget(Path dir, String fileName) {
        Path candidate = dir.resolve(fileName);
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int n = 2; Files.exists(candidate); n++) {
            candidate = dir.resolve(base + " (" + n + ")" + extension);
        }
        return candidate;
    }

    private static long megabytes(long bytes) {
        return Math.max(1, bytes / MEGABYTE);
    }

    public record CopyResult(Path destination, List<Path> copied, List<String> failed) {
    }
}
