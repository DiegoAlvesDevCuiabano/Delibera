package com.delibera.config;

import com.delibera.service.UsuarioService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UsuarioService usuarioService;

    public WebConfig(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AcessoInterceptor(usuarioService))
                .addPathPatterns("/coordenacao/**", "/admin/**", "/painel", "/feedback");
    }
}
