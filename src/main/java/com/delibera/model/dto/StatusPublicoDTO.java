package com.delibera.model.dto;

import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO minimizado para a consulta pública de status.
 * Nunca expõe dados pessoais, justificativa, disciplina, etc.
 */
@Getter
public class StatusPublicoDTO {

    private final String protocolo;
    private final String statusNome;
    private final String statusDescricao;
    private final LocalDateTime dataEnvio;
    private final String parecer;
    private final boolean decidida;
    private final List<HistoricoPublicoDTO> historico;

    public StatusPublicoDTO(Solicitacao s) {
        this.protocolo = s.getProtocolo();
        this.statusNome = s.getStatus().name();
        this.statusDescricao = s.getStatus().getDescricao();
        this.dataEnvio = s.getCriadoEm();

        this.decidida = s.getStatus() == StatusSolicitacao.RESOLVIDO
                || s.getStatus() == StatusSolicitacao.NEGADO;

        // Parecer só visível quando decidida
        this.parecer = this.decidida ? s.getParecer() : null;

        // Histórico minimizado: só status + data, sem observação/nomes
        this.historico = s.getHistorico().stream()
                .map(h -> new HistoricoPublicoDTO(
                        h.getStatusNovo().getDescricao(),
                        h.getCriadoEm()
                ))
                .toList();
    }

    @Getter
    public static class HistoricoPublicoDTO {
        private final String statusDescricao;
        private final LocalDateTime data;

        public HistoricoPublicoDTO(String statusDescricao, LocalDateTime data) {
            this.statusDescricao = statusDescricao;
            this.data = data;
        }
    }
}
