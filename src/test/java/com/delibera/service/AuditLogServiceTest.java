package com.delibera.service;

import com.delibera.model.entity.AuditLog;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.Role;
import com.delibera.model.enums.TipoAcaoAuditoria;
import com.delibera.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService service;

    @Test
    void registrar_deveSalvarAuditLogComCamposCorretos() {
        Instituicao inst = new Instituicao("Faculdade Teste", "FT");
        inst.setId(1L);
        Usuario usuario = new Usuario("Coord", "coord@teste.edu.br", "hash", Role.COORDENADOR, inst);
        usuario.setId(1L);

        service.registrar(usuario, TipoAcaoAuditoria.MUDANCA_STATUS, "Solicitacao", 42L, "192.168.1.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog salvo = captor.getValue();
        assertEquals(usuario, salvo.getUsuario());
        assertEquals(TipoAcaoAuditoria.MUDANCA_STATUS, salvo.getAcao());
        assertEquals("Solicitacao", salvo.getRecursoTipo());
        assertEquals(42L, salvo.getRecursoId());
        assertEquals("192.168.1.1", salvo.getIp());
        assertNotNull(salvo.getTimestamp());
    }

    @Test
    void registrar_deveFuncionarComDiferentesTiposDeAcao() {
        Usuario usuario = new Usuario("Root", "root@appdelibera.com.br", "hash", Role.ROOT);
        usuario.setId(1L);

        for (TipoAcaoAuditoria acao : TipoAcaoAuditoria.values()) {
            service.registrar(usuario, acao, "Recurso", 1L, "10.0.0.1");
        }

        // Deve ter sido chamado uma vez para cada tipo de acao
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, org.mockito.Mockito.times(TipoAcaoAuditoria.values().length))
                .save(captor.capture());

        java.util.List<AuditLog> todos = captor.getAllValues();
        assertEquals(TipoAcaoAuditoria.values().length, todos.size());

        // Verifica que cada tipo de acao aparece exatamente uma vez
        java.util.Set<TipoAcaoAuditoria> acoesSalvas = new java.util.HashSet<>();
        for (AuditLog log : todos) {
            acoesSalvas.add(log.getAcao());
        }
        assertEquals(TipoAcaoAuditoria.values().length, acoesSalvas.size());
    }
}
