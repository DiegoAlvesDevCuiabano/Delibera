package com.delibera.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthSuccessHandler authSuccessHandler;

    public SecurityConfig(AuthSuccessHandler authSuccessHandler) {
        this.authSuccessHandler = authSuccessHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Recursos estáticos e erro
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/error").permitAll()
                // Páginas públicas (site)
                .requestMatchers("/", "/login", "/sobre", "/planos", "/suporte", "/termos", "/privacidade").permitAll()
                // Fluxo do aluno (sem login)
                .requestMatchers("/nova-solicitacao", "/consultar-protocolo", "/status/**").permitAll()
                // Coordenação
                .requestMatchers("/coordenacao/**").hasAnyRole("COORDENADOR", "ADMIN_INSTITUICAO", "GESTAO", "ROOT")
                // Admin da instituição
                .requestMatchers("/admin/**").hasAnyRole("ADMIN_INSTITUICAO", "ROOT")
                // Gestão cross-tenant
                .requestMatchers("/gestao/**").hasAnyRole("GESTAO", "ROOT")
                // Root global
                .requestMatchers("/root/**").hasRole("ROOT")
                // Qualquer outra rota — permitir acesso (404 será tratado pelo ErrorController)
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authSuccessHandler)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(content -> {})
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**")
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
