package com.dbay.teddy.controller;

import com.dbay.teddy.entity.JarResource;
import com.dbay.teddy.entity.JarSaveResult;
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

    /**
     * 上传 JAR。
     *
     * name 为可选的目标文件名，留空时使用上传时的原始文件名。让上传时就能定名，
     * 是因为文件名就是任务引用 JAR 的键：事后再改名会连带影响任务配置。
     */
    @RequestMapping(value = "upload", method = RequestMethod.POST)
    public Response upload(@RequestParam("file") MultipartFile file,
                           @RequestParam(value = "name", required = false) String name) {
        return handle(() -> {
            JarSaveResult result = resourceManager.save(file, name);
            Map<String, Object> data = listing(result.getJars());
            String notice = overwriteNotice(result);
            if (notice != null) {
                data.put("notice", notice);
            }
            return data;
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
        return listing(resourceManager.listJars());
    }

    private Map<String, Object> listing(List<JarResource> jars) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("directory", resourceManager.getLibHome());
        data.put("jars", jars);
        return data;
    }

    /**
     * 覆盖同名文件必须说出来。
     *
     * 替换是更新 JAR 的正常途径，所以不阻断；但使用者得知道替换掉了什么、
     * 有没有牵连到正引用该文件名的任务——这正是删除要拒绝、覆盖却只提示的原因：
     * 删除是让任务彻底跑不起来，覆盖是用新包继续跑。
     */
    private String overwriteNotice(JarSaveResult result) {
        JarResource overwritten = result.getOverwritten();
        if (overwritten == null) {
            return null;
        }
        StringBuilder notice = new StringBuilder()
                .append("已覆盖同名文件 ").append(overwritten.getName())
                .append("（").append(overwritten.getSizeText())
                .append(" → ").append(sizeTextOf(result.getJars(), overwritten.getName()))
                .append("）。");
        List<String> referencedBy = referencedBy(overwritten.getName());
        if (referencedBy.isEmpty()) {
            notice.append("没有任务引用它。");
        } else {
            notice.append("该文件名正被 ").append(referencedBy.size()).append(" 个任务引用：")
                    .append(String.join("、", referencedBy))
                    .append("，请确认新包与这些任务兼容。");
        }
        return notice.toString();
    }

    private String sizeTextOf(List<JarResource> jars, String name) {
        for (JarResource jar : jars) {
            if (name.equals(jar.getName())) {
                return jar.getSizeText();
            }
        }
        return "大小未知";
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
        List<String> referencedBy = referencedBy(requestedName);
        if (!referencedBy.isEmpty()) {
            throw new IllegalStateException("该 JAR 正被任务引用，不能删除："
                    + String.join("、", referencedBy)
                    + "。请先停止并删除这些任务，或改在服务器上直接处理文件。");
        }
    }

    private List<String> referencedBy(String fileName) {
        List<String> referencedBy = new ArrayList<>();
        for (Job job : jobService.findAll()) {
            if (fileName.equals(fileNameOf(job.getAppResource()))) {
                String name = job.getName();
                referencedBy.add(name == null || name.trim().isEmpty() ? "(未命名任务)" : name);
            }
        }
        return referencedBy;
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
