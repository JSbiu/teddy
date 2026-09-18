package com.dbay.teddy.controller;

import com.dbay.teddy.entity.JarResource;
import com.dbay.teddy.entity.Job;
import com.dbay.teddy.manager.JarResourceManager;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.utils.Response;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
        for (String referenced : Arrays.asList("DataLanding.jar", LIB_HOME + "/DataLanding.jar")) {
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

    private static String text(Response response) {
        return String.valueOf(response.getData());
    }
}
