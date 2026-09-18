package com.dbay.teddy.controller;

import com.dbay.teddy.entity.Job;
import com.dbay.teddy.manager.JarResourceManager;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.utils.Response;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author AlexanderGuo
 */
@RestController
@RequestMapping("jar")
public class ResourceController {

    private final JarResourceManager resourceManager;
    private final JobService jobService;

    public ResourceController(JarResourceManager resourceManager, JobService jobService) {
        this.resourceManager = resourceManager;
        this.jobService = jobService;
    }

    @RequestMapping(value = "upload", method = RequestMethod.POST)
    public Response upload(@RequestParam("file") MultipartFile file) {
        return handle(() -> {
            resourceManager.save(file);
            return listing();
        });
    }

    @RequestMapping("list")
    public Response list() {
        return handle(this::listing);
    }

    @RequestMapping(value = "delete", method = RequestMethod.POST)
    public Response delete(String jar) {
        return handle(() -> {
            rejectWhenReferenced(jar);
            resourceManager.delete(jar);
            return listing();
        });
    }

    /** 上传、删除成功后也返回最新列表，页面不必再发一次请求。 */
    private Map<String, Object> listing() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("directory", resourceManager.getLibHome());
        data.put("jars", resourceManager.listJars());
        return data;
    }

    /**
     * 被任何任务引用的 JAR 不允许在这里删除。
     *
     * 任务提交时经 JarResourceManager#resolveForLaunch 解析 appResource，
     * 文件一旦被删掉，该任务下次提交或重启就会失败，所以这里直接拒绝，
     * 而不是给出"确认后仍可删除"的口子——真要删应该去服务器上处理。
     *
     * 比对按文件名规范化：appResource 可能是简单文件名，也可能是绝对路径。
     */
    private void rejectWhenReferenced(String jar) {
        String requestedName = fileNameOf(jar);
        if (requestedName == null) {
            return;
        }
        List<String> referencedBy = new ArrayList<>();
        for (Job job : jobService.findAll()) {
            if (requestedName.equals(fileNameOf(job.getAppResource()))) {
                referencedBy.add(job.getName());
            }
        }
        if (!referencedBy.isEmpty()) {
            throw new IllegalStateException("该 JAR 正被任务引用，不能删除："
                    + String.join("、", referencedBy)
                    + "。请先停止并删除这些任务，或改在服务器上直接处理文件。");
        }
    }

    private String fileNameOf(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        Path fileName = Paths.get(value.trim()).getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private Response handle(Supplier<?> action) {
        try {
            return Response.SUCCESS(action.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.ERROR(e.getMessage());
        }
    }
}
