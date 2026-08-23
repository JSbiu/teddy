package com.dbay.teddy.manager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

        List<String> afterSave = resources.save(upload);

        assertEquals(1, afterSave.size());
        Path saved = libHome.resolve("streaming.jar");
        assertTrue(Files.exists(saved));
        Files.write(libHome.resolve("notes.txt"), new byte[]{1});
        assertEquals(1, resources.listJars().size());

        List<String> afterDelete = resources.delete(saved.toAbsolutePath().toString());
        assertTrue(afterDelete.isEmpty());
        assertFalse(Files.exists(saved));
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

    private void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Expected the operation to be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }
}
