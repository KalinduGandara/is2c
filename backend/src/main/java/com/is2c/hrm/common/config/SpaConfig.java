package com.is2c.hrm.common.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React SPA from {@code classpath:/static/} (D8). Paths without a file extension that are not
 * API/actuator paths fall back to index.html so client-side routes survive a reload; missing assets stay 404.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/").resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource resource = location.createRelative(path);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        boolean clientRoute = !path.startsWith("api/") && !path.startsWith("actuator/")
                                && !path.substring(path.lastIndexOf('/') + 1).contains(".");
                        Resource index = new ClassPathResource("static/index.html");
                        return clientRoute && index.exists() ? index : null;
                    }
                });
    }
}
