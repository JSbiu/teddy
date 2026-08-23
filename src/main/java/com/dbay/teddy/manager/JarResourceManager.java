package com.dbay.teddy.manager;

import com.dbay.teddy.utils.TeddyConf;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JarResourceManager {
    private final Path libHome;

    public JarResourceManager() {
        this(Paths.get(TeddyConf.get("lib.home")));
    }

    public JarResourceManager(Path libHome) {
        if (libHome == null) {
            throw new IllegalArgumentException("lib.home is required");
        }
        try {
            Path normalized = libHome.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("lib.home is not an existing directory");
            }
            this.libHome = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to resolve lib.home", e);
        }
    }

    public List<String> listJars() {
        try (Stream<Path> paths = Files.list(libHome)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isJar)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to list JAR resources", e);
        }
    }

    public List<String> save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty JAR file is required");
        }
        String originalName = file.getOriginalFilename();
        validateJarName(originalName);
        Path target = resolveDirectChild(originalName);
        if (Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("Symbolic-link targets are not allowed");
        }

        Path temporary = null;
        try {
            if (!Files.isWritable(libHome)) {
                throw new IllegalStateException("lib.home is not writable");
            }
            temporary = Files.createTempFile(libHome, ".teddy-upload-", ".tmp");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            return listJars();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save JAR resource", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public List<String> delete(String jar) {
        Path target = resolveRequestedJar(jar);
        if (Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("Symbolic-link targets are not allowed");
        }
        try {
            Files.deleteIfExists(target);
            return listJars();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete JAR resource", e);
        }
    }

    private Path resolveRequestedJar(String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            throw new IllegalArgumentException("JAR path is required");
        }
        String trimmed = requested.trim();
        Path input = Paths.get(trimmed);
        Path candidate;
        if (input.isAbsolute()) {
            candidate = input.toAbsolutePath().normalize();
        } else {
            validateJarName(trimmed);
            candidate = libHome.resolve(trimmed).normalize();
        }
        validateDirectChild(candidate);
        return candidate;
    }

    private Path resolveDirectChild(String fileName) {
        Path candidate = libHome.resolve(fileName).normalize();
        validateDirectChild(candidate);
        return candidate;
    }

    private void validateDirectChild(Path candidate) {
        if (!libHome.equals(candidate.getParent()) || !isJar(candidate)) {
            throw new IllegalArgumentException("Only direct JAR files under lib.home are allowed");
        }
    }

    private void validateJarName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()
                || fileName.contains("/") || fileName.contains("\\")
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("Only simple .jar file names are allowed");
        }
    }

    private boolean isJar(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }
}
