package com.dbay.teddy.controller;

import com.dbay.teddy.manager.JarResourceManager;
import com.dbay.teddy.utils.Response;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Supplier;

/**
 * @author AlexanderGuo
 */
@RestController
@RequestMapping("jar")
public class ResourceController {

    private final JarResourceManager resourceManager;

    public ResourceController(JarResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @RequestMapping(value = "upload", method = RequestMethod.POST)
    public Response upload(@RequestParam("file") MultipartFile file) {
        return handle(() -> resourceManager.save(file));
    }

    @RequestMapping("list")
    public Response list() {
        return handle(resourceManager::listJars);
    }

    @RequestMapping(value = "delete", method = RequestMethod.POST)
    public Response delete(String jar) {
        return handle(() -> resourceManager.delete(jar));
    }

    private Response handle(Supplier<List<String>> action) {
        try {
            return Response.SUCCESS(action.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.ERROR(e.getMessage());
        }
    }
}
