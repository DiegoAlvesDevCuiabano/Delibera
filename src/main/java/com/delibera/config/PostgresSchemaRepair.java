package com.delibera.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repara incompatibilidades criadas pelo {@code ddl-auto=update} em bancos já existentes.
 *
 * <p>O Hibernate adicionou {@code solicitacao.anonimizado} como coluna anulável. Registros
 * anteriores ficaram com {@code NULL}, que não pode ser carregado no campo Java primitivo.
 */
@Component
@Profile({"dev", "prod"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostgresSchemaRepair implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PostgresSchemaRepair.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresSchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int corrigidos = jdbcTemplate.update(
                "UPDATE solicitacao SET anonimizado = FALSE WHERE anonimizado IS NULL");
        jdbcTemplate.execute(
                "ALTER TABLE solicitacao ALTER COLUMN anonimizado SET DEFAULT FALSE");
        jdbcTemplate.execute(
                "ALTER TABLE solicitacao ALTER COLUMN anonimizado SET NOT NULL");

        if (corrigidos > 0) {
            logger.info("[SCHEMA] {} solicitações antigas tiveram anonimizado corrigido para false", corrigidos);
        }
    }
}
