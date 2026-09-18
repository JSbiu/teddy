package com.dbay.teddy.manager;

import com.dbay.teddy.entity.JarResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockMultipartFile;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JarResourceManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void savesListsAndDeletesOnlyJarFiles() throws Exception {
        Path libHome = temporaryFolder.newFolder("jars").toPath();
        JarResourceManager resources = new JarResourceManager(libHome);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "streaming.jar", "application/java-archive", "jar-data".getBytes(StandardCharsets.UTF_8));

        List<JarResource> afterSave = resources.save(upload);

        assertEquals(1, afterSave.size());
        Path saved = libHome.resolve("streaming.jar");
        assertTrue(Files.exists(saved));
        Files.write(libHome.resolve("notes.txt"), new byte[]{1});
        assertEquals(1, resources.listJars().size());

        List<JarResource> afterDelete = resources.delete(saved.toAbsolutePath().toString());
        assertTrue(afterDelete.isEmpty());
        assertFalse(Files.exists(saved));
    }

    /**
     * 大小与时间在服务端格式化：前端没有测试框架，这两项是页面里唯一能被自动验证的部分。
     */
    @Test
    public void reportsNameSizeAndModifiedTime() throws Exception {
        Path libHome = temporaryFolder.newFolder("listed-jars").toPath();
        Path jar = libHome.resolve("streaming.jar");
        try (RandomAccessFile file = new RandomAccessFile(jar.toFile(), "rw")) {
            file.setLength(12L * 1024 * 1024 + 300L * 1024);
        }
        long modifiedMillis = 1_760_000_000_000L;
        Files.setLastModifiedTime(jar, FileTime.fromMillis(modifiedMillis));

        JarResourceManager resources = new JarResourceManager(libHome);
        List<JarResource> jars = resources.listJars();

        assertEquals(1, jars.size());
        JarResource resource = jars.get(0);
        assertEquals("streaming.jar", resource.getName());
        assertEquals(jar.toAbsolutePath().normalize().toString(), resource.getPath());
        assertEquals("12.3 MB", resource.getSizeText());
        assertEquals(expectedModifiedTime(modifiedMillis), resource.getModifiedTimeText());
        assertEquals(libHome.toRealPath().toString(), resources.getLibHome());
    }

    @Test
    public void formatsSmallAndLargeSizesWithOneDecimal() throws Exception {
        Path libHome = temporaryFolder.newFolder("sizes").toPath();

        assertEquals("0 B", sizeTextOf(libHome, "empty.jar", 0));
        assertEquals("1.5 KB", sizeTextOf(libHome, "small.jar", 1536));
        assertEquals("2.0 GB", sizeTextOf(libHome, "large.jar", 2L * 1024 * 1024 * 1024));
    }

    @Test
    public void rejectsTraversalAndNonJarUploads() throws Exception {
        Path libHome = temporaryFolder.newFolder("safe-jars").toPath();
        JarResourceManager resources = new JarResourceManager(libHome);

        assertRejected(() -> resources.save(new MockMultipartFile(
                "file", "../escape.jar", "application/java-archive", new byte[]{1})));
        assertRejected(() -> resources.save(new MockMultipartFile(
                "file", "notes.txt", "text/plain", new byte[]{1})));
    }

    @Test
    public void refusesToDeleteAnAbsoluteJarOutsideLibHome() throws Exception {
        Path libHome = temporaryFolder.newFolder("managed").toPath();
        Path outside = temporaryFolder.newFile("outside.jar").toPath();
        JarResourceManager resources = new JarResourceManager(libHome);

        assertRejected(() -> resources.delete(outside.toAbsolutePath().toString()));
        assertTrue(Files.exists(outside));
    }

    @Test
    public void resolvesOnlyExistingDirectJarsForSparkLaunch() throws Exception {
        Path libHome = temporaryFolder.newFolder("launch-jars").toPath();
        Path managed = Files.write(libHome.resolve("job.jar"), new byte[]{1});
        Path outside = temporaryFolder.newFile("outside-launch.jar").toPath();
        JarResourceManager resources = new JarResourceManager(libHome);

        assertEquals(managed.toAbsolutePath().normalize(),
                resources.resolveForLaunch("job.jar"));
        assertRejected(() -> resources.resolveForLaunch("missing.jar"));
        assertRejected(() -> resources.resolveForLaunch(
                outside.toAbsolutePath().toString()));
    }

    /** 与实现里的格式保持同一写法：改掉服务端格式时这条断言会失败。 */
    private static String expectedModifiedTime(long epochMillis) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private String sizeTextOf(Path libHome, String name, long bytes) throws Exception {
        Path jar = Files.createFile(libHome.resolve(name));
        Files.setLastModifiedTime(jar, FileTime.fromMillis(1_760_000_000_000L));
        try (RandomAccessFile file = new RandomAccessFile(jar.toFile(), "rw")) {
            file.setLength(bytes);
        }
        for (JarResource resource : new JarResourceManager(libHome).listJars()) {
            if (name.equals(resource.getName())) {
                return resource.getSizeText();
            }
        }
        return "absent";
    }

    private void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Expected the operation to be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }
}
