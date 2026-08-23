package com.dbay.teddy.controller;

import com.dbay.teddy.manager.JarResourceManager;
import com.dbay.teddy.utils.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Autowired
    private JarResourceManager resourceManager;

    @RequestMapping("upload")
    public Response upload(@RequestParam("file") MultipartFile file){
        return handle(() -> resourceManager.save(file));
    }

    @RequestMapping("list")
    public Response list(){
        return handle(resourceManager::listJars);
    }

    @RequestMapping("delete")
    public Response delete(String jar){
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
