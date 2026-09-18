package com.dbay.teddy.manager;

import com.dbay.teddy.entity.JarResource;
import com.dbay.teddy.entity.JarSaveResult;
import com.dbay.teddy.utils.TeddyConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JarResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(JarResourceManager.class);

    /** 上传中转文件的前缀与后缀，同时也是启动清理的匹配条件。 */
    private static final String UPLOAD_TEMP_PREFIX = ".teddy-upload-";
    private static final String UPLOAD_TEMP_SUFFIX = ".tmp";

    private static final long KILOBYTE = 1024L;
    private static final long MEGABYTE = KILOBYTE * 1024L;
    private static final long GIGABYTE = MEGABYTE * 1024L;
    private static final DateTimeFormatter MODIFIED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

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
        discardAbandonedUploads();
    }

    /** 当前管理的目录，供接口回传给页面，让使用者知道文件从哪里来。 */
    public String getLibHome() {
        return libHome.toString();
    }

    public List<JarResource> listJars() {
        try (Stream<Path> paths = Files.list(libHome)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isJar)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::toResource)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to list JAR resources", e);
        }
    }

    private JarResource toResource(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    absolute, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new JarResource(
                    path.getFileName().toString(),
                    absolute.toString(),
                    formatSize(attributes.size()),
                    formatTime(attributes.lastModifiedTime().toMillis()));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read JAR attributes", e);
        }
    }

    /** 1024 进制换算并保留一位小数，例如 {@code 12.3 MB}。 */
    private static String formatSize(long bytes) {
        if (bytes >= GIGABYTE) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / (double) GIGABYTE);
        }
        if (bytes >= MEGABYTE) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (double) MEGABYTE);
        }
        if (bytes >= KILOBYTE) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / (double) KILOBYTE);
        }
        return bytes + " B";
    }

    /** 服务器本地时区，保证显示结果与浏览者所在时区无关。 */
    private static String formatTime(long epochMillis) {
        return MODIFIED_TIME_FORMAT.format(
                Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /**
     * 保存上传的 JAR。
     *
     * requestedName 为空时使用上传时的原始文件名。允许调用方指定名字，是因为
     * 文件名就是任务引用的键：上传前定好，比上传后再改名省事——改名会同时影响
     * 已经保存了那个名字的任务配置。
     *
     * 同名文件会被替换（这是更新 JAR 的正常途径，不阻断），但替换前的内容随结果
     * 一并返回，由调用方决定如何提示。
     */
    public JarSaveResult save(MultipartFile file, String requestedName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty JAR file is required");
        }
        String targetName = firstNonEmpty(requestedName, file.getOriginalFilename());
        validateJarName(targetName);
        Path target = resolveDirectChild(targetName);
        if (Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("Symbolic-link targets are not allowed");
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("A directory already exists with the requested name");
        }
        JarResource overwritten = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                ? toResource(target)
                : null;

        Path temporary = null;
        try {
            if (!Files.isWritable(libHome)) {
                throw new IllegalStateException("lib.home is not writable");
            }
            temporary = Files.createTempFile(libHome, UPLOAD_TEMP_PREFIX, UPLOAD_TEMP_SUFFIX);
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
            return new JarSaveResult(listJars(), overwritten);
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

    /**
     * 丢弃上次进程留下的上传中转文件。
     *
     * 上传是先写中转文件再原子移动，进程被 kill 或磁盘写满时中转文件会留下。
     * 它不以 .jar 结尾，因此不会出现在资源列表里，也就没人会去清理——只能在这里收。
     * 启动时不可能有正在进行的上传，所以此刻删除是安全的。
     */
    private void discardAbandonedUploads() {
        List<Path> abandoned;
        try (Stream<Path> paths = Files.list(libHome)) {
            abandoned = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isAbandonedUpload)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.warn("Unable to scan {} for abandoned upload files", libHome, e);
            return;
        }
        for (Path path : abandoned) {
            try {
                Files.deleteIfExists(path);
                logger.warn("Discarded abandoned upload file: {}", path.getFileName());
            } catch (IOException e) {
                logger.warn("Unable to discard abandoned upload file: {}", path, e);
            }
        }
    }

    private boolean isAbandonedUpload(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith(UPLOAD_TEMP_PREFIX) && fileName.endsWith(UPLOAD_TEMP_SUFFIX);
    }

    private static String firstNonEmpty(String preferred, String fallback) {
        if (preferred != null && !preferred.trim().isEmpty()) {
            return preferred.trim();
        }
        return fallback;
    }

    public Path resolveForLaunch(String jar) {
        Path target = resolveRequestedJar(jar);
        if (Files.isSymbolicLink(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Spark application JAR must be a regular file under lib.home");
        }
        return target;
    }

    public List<JarResource> delete(String jar) {
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
