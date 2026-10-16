package com.dbay.teddy.controller;

import com.dbay.teddy.entity.JarResource;
import com.dbay.teddy.entity.JarSaveResult;
import com.dbay.teddy.entity.Job;
import com.dbay.teddy.manager.JarResourceManager;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.utils.Response;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资源删除的引用校验：删掉被任务引用的 JAR 会让该任务下次提交或重启失败，
 * 因此这里必须以拒绝收场，且不能落到真正的文件删除。
 */
public class ResourceControllerTest {

    private static final String LIB_HOME = "/var/lib/teddy/jars";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void referencedJarPathAliasesCannotBeDeleted() throws Exception {
        Path directory = temporaryFolder.newFolder("jars").toPath();
        Path jar = Files.write(directory.resolve("DataLanding.jar"), new byte[]{1});
        ResourceController controller = new ResourceController(new JarResourceManager(directory),
                jobs(job("DataLanding", "DataLanding.jar")));

        for (String suffix : Arrays.asList("", "/.", "/child/..")) {
            assertEquals("error", controller.delete(jar.toAbsolutePath() + suffix).getState());
            assertTrue(Files.exists(jar));
        }
    }

    @Test
    public void referencedJarsCannotBeDeleted() {
        JarResourceManager resources = mock(JarResourceManager.class);
        ResourceController controller = new ResourceController(
                resources, jobs(job("DataLanding", "DataLanding.jar")));

        Response response = controller.delete(LIB_HOME + "/DataLanding.jar");

        assertEquals("error", response.getState());
        assertTrue(text(response).contains("DataLanding"));
        verify(resources, never()).delete(anyString());
    }

    @Test
    public void referenceMatchesWhetherStoredAsNameOrAbsolutePath() {
        for (String referenced : Arrays.asList("DataLanding.jar", LIB_HOME + "/DataLanding.jar",
                LIB_HOME + "/child/../DataLanding.jar", LIB_HOME + "/DataLanding.jar/.")) {
            JarResourceManager resources = mock(JarResourceManager.class);
            ResourceController controller = new ResourceController(
                    resources, jobs(job("DataLanding", referenced)));

            assertEquals("error", controller.delete(LIB_HOME + "/DataLanding.jar").getState());
            assertEquals("error", controller.delete("DataLanding.jar").getState());
            verify(resources, never()).delete(anyString());
        }
    }

    @Test
    public void unreferencedJarsAreDeleted() {
        JarResourceManager resources = mock(JarResourceManager.class);
        when(resources.delete(anyString())).thenReturn(Collections.emptyList());
        when(resources.listJars()).thenReturn(Collections.emptyList());
        when(resources.getLibHome()).thenReturn(LIB_HOME);
        ResourceController controller = new ResourceController(
                resources, jobs(job("DataLanding", "DataLanding.jar")));

        Response response = controller.delete(LIB_HOME + "/Unused.jar");

        assertEquals("success", response.getState());
        verify(resources).delete(LIB_HOME + "/Unused.jar");
    }

    @Test
    public void listingCarriesDirectoryAndJars() {
        List<JarResource> jars = Collections.singletonList(new JarResource(
                "DataLanding.jar", LIB_HOME + "/DataLanding.jar", "12.3 MB", "2026-09-11 16:15:56"));
        JarResourceManager resources = mock(JarResourceManager.class);
        when(resources.listJars()).thenReturn(jars);
        when(resources.getLibHome()).thenReturn(LIB_HOME);
        ResourceController controller = new ResourceController(resources, jobs());

        Response response = controller.list();

        assertEquals("success", response.getState());
        Map<?, ?> data = (Map<?, ?>) response.getData();
        assertEquals(LIB_HOME, data.get("directory"));
        assertEquals(jars, data.get("jars"));
    }

    /**
     * 覆盖同名文件不阻断（那是更新 JAR 的正常途径），但必须让使用者看到替换掉了什么、
     * 且知道有哪些任务在引用这个文件名。
     */
    @Test
    public void uploadReportsTheReplacedJarAndItsReferencingJobs() {
        JarResourceManager resources = mock(JarResourceManager.class);
        when(resources.save(any(), any())).thenReturn(new JarSaveResult(
                Collections.singletonList(new JarResource(
                        "DataLanding.jar", LIB_HOME + "/DataLanding.jar", "13.1 MB", "2026-09-18 10:30:00")),
                new JarResource(
                        "DataLanding.jar", LIB_HOME + "/DataLanding.jar", "12.3 MB", "2026-09-11 16:15:56")));
        when(resources.getLibHome()).thenReturn(LIB_HOME);
        ResourceController controller = new ResourceController(
                resources, jobs(job("DataLanding", "DataLanding.jar")));

        Response response = controller.upload(file(), "DataLanding.jar");

        assertEquals("success", response.getState());
        String notice = String.valueOf(((Map<?, ?>) response.getData()).get("notice"));
        assertTrue(notice.contains("12.3 MB"));
        assertTrue(notice.contains("13.1 MB"));
        assertTrue(notice.contains("DataLanding"));
    }

    @Test
    public void uploadWithoutReplacementCarriesNoNotice() {
        JarResourceManager resources = mock(JarResourceManager.class);
        when(resources.save(any(), any())).thenReturn(new JarSaveResult(Collections.emptyList(), null));
        when(resources.getLibHome()).thenReturn(LIB_HOME);
        ResourceController controller = new ResourceController(resources, jobs());

        Response response = controller.upload(file(), "");

        assertEquals("success", response.getState());
        assertFalse(((Map<?, ?>) response.getData()).containsKey("notice"));
    }

    @Test
    public void referenceLookupFailureStillReportsCompletedReplacement() throws Exception {
        Path directory = temporaryFolder.newFolder("overwrites").toPath();
        Path jar = directory.resolve("DataLanding.jar");
        for (RuntimeException failure : Arrays.asList(
                new DataAccessResourceFailureException("database unavailable"),
                new IllegalStateException("reference lookup failed"))) {
            Files.write(jar, new byte[]{0, 0});
            JobService jobs = mock(JobService.class);
            when(jobs.findAll()).thenThrow(failure);
            ResourceController controller = new ResourceController(new JarResourceManager(directory), jobs);

            Response response = controller.upload(file(), null);

            assertEquals("success", response.getState());
            assertEquals(1L, Files.size(jar));
            String notice = String.valueOf(((Map<?, ?>) response.getData()).get("notice"));
            assertTrue(notice.contains("已覆盖同名文件 DataLanding.jar"));
            assertTrue(notice.contains("未能查询任务引用"));
            assertFalse(notice.contains("没有任务引用"));
        }
    }

    private static JobService jobs(Job... jobs) {
        JobService jobService = mock(JobService.class);
        when(jobService.findAll()).thenReturn(Arrays.asList(jobs));
        return jobService;
    }

    /**
     * 用 mock 而不是 new Job()：Job 的字段初始化器会读 TeddyConf，
     * 脱离 Spring 引导时那是个 null，直接构造会 NPE。
     * JobControllerTest 也是同样的写法。
     */
    private static Job job(String name, String appResource) {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn(name);
        when(job.getAppResource()).thenReturn(appResource);
        return job;
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile(
                "file", "DataLanding.jar", "application/java-archive", new byte[]{1});
    }

    private static String text(Response response) {
        return String.valueOf(response.getData());
    }
}
