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
    private final LoginFailureHandler loginFailureHandler;

    public SecurityConfig(AuthSuccessHandler authSuccessHandler, LoginFailureHandler loginFailureHandler) {
        this.authSuccessHandler = authSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Recursos estáticos, erro e actuator
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Console H2 — só existe quando o perfil h2 está ativo
                .requestMatchers("/h2-console/**").permitAll()
                // Páginas públicas (site)
                .requestMatchers("/", "/login", "/sobre", "/planos", "/suporte", "/termos", "/privacidade").permitAll()
                // Fluxo do aluno (sem login)
                .requestMatchers("/nova-solicitacao", "/consultar-protocolo", "/cancelar-solicitacao",
                        "/status/**").permitAll()
                // Área do aluno por link assinado — a autorização é o token, verificada
                // em cada handler; sem sessão não há o que o Spring Security possa checar.
                .requestMatchers("/acompanhar", "/meus-pedidos", "/meus-pedidos/**", "/meus-dados/**").permitAll()
                // Fluxo do professor (sem login)
                .requestMatchers("/parecer-professor", "/parecer-professor/**").permitAll()
                // Voucher (ativação via link assinado no email)
                .requestMatchers("/voucher/**").permitAll()
                // Página de rate limit (alvo do forward do RateLimitFilter)
                .requestMatchers("/rate-limit-consulta").permitAll()
                // Feedback do piloto exige sessão autenticada
                .requestMatchers("/feedback").authenticated()
                // Coordenação
                .requestMatchers("/coordenacao/**").hasAnyRole("COORDENADOR", "ADMIN_INSTITUICAO")
                // Admin da instituição
                .requestMatchers("/admin/**").hasAnyRole("ADMIN_INSTITUICAO", "ROOT")
                // Gestão cross-tenant
                .requestMatchers("/gestao/**").hasAnyRole("GESTAO", "ROOT")
                // Root global
                .requestMatchers("/root/**").hasRole("ROOT")
                // Default-deny: rota nova sem matcher explícito exige autenticação.
                // NUNCA trocar por permitAll() — ver docs/TODO.md.
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(content -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
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
