package com.sevenb.auth.auth_service.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${auth.service.recipeFrontUtl}")
    private String recipeFrontUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/auth/**") // Ajusta la ruta según sea necesario
                .allowedOrigins(recipeFrontUrl)    // Ajusta esto si tu frontend está en otro lugar
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}