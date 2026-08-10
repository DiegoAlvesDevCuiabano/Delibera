package com.delibera.model.dto;

import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Solicitacao;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Portabilidade de dados (LGPD, art. 18, V) — tudo que o Delibera guarda sobre um
 * aluno, em formato legível por máquina.
 *
 * <p>Inclui o parecer interno? Não. Ele é anotação de trabalho da coordenação, não
 * dado fornecido pelo titular, e expô-lo entregaria deliberação interna da instituição.
 * O parecer publicado ao aluno vai junto.
 */
@Getter
public class ExportacaoAlunoDTO {

    private final String geradoEm;
    private final String titular;
    private final String aviso;
    private final List<SolicitacaoExportada> solicitacoes;

    public ExportacaoAlunoDTO(String email, List<Solicitacao> solicitacoes,
                              Map<Long, List<Anexo>> anexosPorSolicitacao) {
        this.geradoEm = LocalDateTime.now().toString();
        this.titular = email;
        this.aviso = "Exportação gerada a pedido do titular. Contém os dados pessoais e "
                + "acadêmicos registrados no Delibera. Pareceres internos da coordenação "
                + "não são dados do titular e não estão incluídos.";
        this.solicitacoes = solicitacoes.stream()
                .map(s -> new SolicitacaoExportada(s, anexosPorSolicitacao.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Getter
    public static class SolicitacaoExportada {
        private final String protocolo;
        private final String tipo;
        private final String status;
        private final String alunoNome;
        private final String alunoEmail;
        private final String alunoWhatsapp;
        private final String alunoCurso;
        private final String alunoTurma;
        private final String instituicao;
        private final String disciplina;
        private final String professor;
        private final LocalDate dataOcorrencia;
        private final String datasFalta;
        private final String notaAtual;
        private final String notaEsperada;
        private final String justificativa;
        private final String parecerDaCoordenacao;
        private final LocalDateTime criadoEm;
        private final LocalDateTime atualizadoEm;
        private final LocalDateTime aceiteDosTermos;
        private final String versaoDosTermos;
        private final List<String> anexosEnviados;
        private final List<EventoExportado> historico;

        SolicitacaoExportada(Solicitacao s, List<Anexo> anexos) {
            this.protocolo = s.getProtocolo();
            this.tipo = s.getTipo() != null ? s.getTipo().getDescricao() : null;
            this.status = s.getStatus() != null ? s.getStatus().getDescricao() : null;
            this.alunoNome = s.getAlunoNome();
            this.alunoEmail = s.getAlunoEmail();
            this.alunoWhatsapp = s.getAlunoWhatsapp();
            this.alunoCurso = s.getAlunoCurso();
            this.alunoTurma = s.getAlunoTurma();
            this.instituicao = s.getInstituicao() != null ? s.getInstituicao().getNome() : null;
            this.disciplina = s.getDisciplina();
            this.professor = s.getProfessor();
            this.dataOcorrencia = s.getDataOcorrencia();
            this.datasFalta = s.getDatasFalta();
            this.notaAtual = s.getNotaAtual();
            this.notaEsperada = s.getNotaEsperada();
            this.justificativa = s.getJustificativa();
            this.parecerDaCoordenacao = s.getParecer();
            this.criadoEm = s.getCriadoEm();
            this.atualizadoEm = s.getAtualizadoEm();
            this.aceiteDosTermos = s.getDataAceiteTermos();
            this.versaoDosTermos = s.getVersaoTermosAceita();
            this.anexosEnviados = anexos.stream().map(Anexo::getNomeOriginal).toList();
            this.historico = s.getHistorico().stream().map(EventoExportado::new).toList();
        }
    }

    @Getter
    public static class EventoExportado {
        private final String de;
        private final String para;
        private final String observacao;
        private final LocalDateTime quando;

        EventoExportado(com.delibera.model.entity.HistoricoSolicitacao h) {
            this.de = h.getStatusAnterior() != null ? h.getStatusAnterior().getDescricao() : null;
            this.para = h.getStatusNovo() != null ? h.getStatusNovo().getDescricao() : null;
            this.observacao = h.getObservacao();
            this.quando = h.getCriadoEm();
        }
    }
}
