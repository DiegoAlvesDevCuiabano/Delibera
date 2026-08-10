package com.delibera.config;

import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.Role;
import com.delibera.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstrap do usuário ROOT em produção via env vars.
 * Só executa se DELIBERA_ROOT_EMAIL e DELIBERA_ROOT_PASSWORD estiverem definidas.
 * Ausência das vars = skip silencioso (deploys subsequentes sem as vars).
 */
@Component
@Profile("prod")
public class RootBootstrap implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RootBootstrap.class);

    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String SENHA_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,}$";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public RootBootstrap(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String email = System.getenv("DELIBERA_ROOT_EMAIL");
        String senha = System.getenv("DELIBERA_ROOT_PASSWORD");

        // Ausência das vars = skip silencioso
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            return;
        }

        // Idempotência: se já existe usuário com esse email, não faz nada
        if (usuarioRepository.findByEmail(email).isPresent()) {
            logger.info("[BOOTSTRAP] Usuário ROOT já existe, pulando bootstrap");
            return;
        }

        // Validação — falha cedo se config ruim
        if (!email.matches(EMAIL_REGEX)) {
            logger.error("[BOOTSTRAP] DELIBERA_ROOT_EMAIL inválido. Formato esperado: usuario@dominio.com");
            throw new IllegalStateException("DELIBERA_ROOT_EMAIL inválido — abortando inicialização");
        }

        if (!senha.matches(SENHA_REGEX)) {
            logger.error("[BOOTSTRAP] DELIBERA_ROOT_PASSWORD não atende requisitos: "
                    + "mínimo 12 caracteres, 1 maiúscula, 1 minúscula, 1 número, 1 caractere especial");
            throw new IllegalStateException("DELIBERA_ROOT_PASSWORD não atende requisitos de segurança — abortando inicialização");
        }

        Usuario root = new Usuario("Administrador", email, passwordEncoder.encode(senha), Role.ROOT);
        usuarioRepository.save(root);

        logger.info("[BOOTSTRAP] Usuário ROOT criado com email: {}", email);
    }
}
