package com.delibera.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresSchemaRepairTest {

    @Test
    void run_dadosLegados_normalizaAntesDeAplicarRestricao() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update("UPDATE solicitacao SET anonimizado = FALSE WHERE anonimizado IS NULL"))
                .thenReturn(6);

        new PostgresSchemaRepair(jdbcTemplate).run(new DefaultApplicationArguments());

        var ordem = inOrder(jdbcTemplate);
        ordem.verify(jdbcTemplate).update(
                "UPDATE solicitacao SET anonimizado = FALSE WHERE anonimizado IS NULL");
        ordem.verify(jdbcTemplate).execute(
                "ALTER TABLE solicitacao ALTER COLUMN anonimizado SET DEFAULT FALSE");
        ordem.verify(jdbcTemplate).execute(
                "ALTER TABLE solicitacao ALTER COLUMN anonimizado SET NOT NULL");
    }
}
