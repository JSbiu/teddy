package com.dbay.teddy.config;

import com.dbay.teddy.security.AuthInterceptor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 应用主配置，为所有的请求增加跨域配置
 * @author AlexanderGuo
 */
@Configuration
@EnableAutoConfiguration
public class AppConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    public AppConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/job/**", "/jar/**", "/system/client-config",
                        "/system/notify-config/**");
    }
}
