package com.delibera.service;

import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.EmailLog;
import com.delibera.model.entity.ParecerProfessor;
import com.delibera.model.entity.Solicitacao;
import com.delibera.repository.AnexoRepository;
import com.delibera.repository.EmailLogRepository;
import com.delibera.repository.ParecerProfessorRepository;
import com.delibera.repository.SolicitacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Job diário que anonimiza dados pessoais em solicitações marcadas para exclusão.
 * Política: 90 dias após excluidoEm, dados são substituídos por [ANONIMIZADO].
 * Também anonimiza solicitações finalizadas com mais de 5 anos automaticamente.
 *
 * <p>Alcança os quatro lugares onde há dado pessoal: a solicitação, o histórico, os
 * emails enviados (o corpo guarda nome, protocolo e parecer) e o parecer do professor.
 * Os arquivos de anexo são apagados do disco — atestado médico é dado sensível
 * (LGPD art. 11) e não há motivo para sobreviver à anonimização.
 */
@Component
public class AnonimizacaoScheduledJob {

    private static final Logger logger = LoggerFactory.getLogger(AnonimizacaoScheduledJob.class);
    private static final String ANONIMIZADO = "[ANONIMIZADO]";

    private final SolicitacaoRepository solicitacaoRepository;
    private final EmailLogRepository emailLogRepository;
    private final ParecerProfessorRepository parecerProfessorRepository;
    private final AnexoRepository anexoRepository;
    private final AnexoService anexoService;

    public AnonimizacaoScheduledJob(SolicitacaoRepository solicitacaoRepository,
                                     EmailLogRepository emailLogRepository,
                                     ParecerProfessorRepository parecerProfessorRepository,
                                     AnexoRepository anexoRepository,
                                     AnexoService anexoService) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.emailLogRepository = emailLogRepository;
        this.parecerProfessorRepository = parecerProfessorRepository;
        this.anexoRepository = anexoRepository;
        this.anexoService = anexoService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void executar() {
        logger.info("[ANONIMIZACAO] Iniciando verificação...");
        LocalDateTime agora = LocalDateTime.now();

        List<Solicitacao> excluidas = solicitacaoRepository.findParaAnonimizar(agora.minusDays(90));
        List<Solicitacao> antigas = solicitacaoRepository.findAntigasParaAnonimizar(agora.minusYears(5));

        Set<Solicitacao> candidatas = new LinkedHashSet<>(excluidas);
        candidatas.addAll(antigas);

        for (Solicitacao s : candidatas) {
            anonimizar(s);
        }

        int total = candidatas.size();
        if (total > 0) {
            logger.info("[ANONIMIZACAO] {} solicitações anonimizadas", total);
        } else {
            logger.info("[ANONIMIZACAO] Nenhuma solicitação para anonimizar");
        }
    }

    private void anonimizar(Solicitacao s) {
        s.setAlunoNome(ANONIMIZADO);
        s.setAlunoEmail(ANONIMIZADO + "@anonimizado.local");
        s.setAlunoWhatsapp(null);
        s.setAlunoCurso(null);
        s.setAlunoTurma(null);
        s.setDisciplina(null);
        s.setProfessor(null);
        s.setDataOcorrencia(null);
        s.setDatasFalta(null);
        s.setNotaAtual(null);
        s.setNotaEsperada(null);
        s.setJustificativa(ANONIMIZADO);
        s.setParecer(s.getParecer() != null ? ANONIMIZADO : null);
        s.setParecerInterno(s.getParecerInterno() != null ? ANONIMIZADO : null);
        s.getHistorico().forEach(h -> h.setObservacao(
                h.getObservacao() != null ? ANONIMIZADO : null));
        s.setAnonimizado(true);
        solicitacaoRepository.save(s);

        anonimizarEmails(s.getId());
        anonimizarPareceres(s.getId());
        apagarAnexos(s.getId());

        logger.debug("[ANONIMIZACAO] Solicitação {} anonimizada", s.getProtocolo());
    }

    /**
     * O corpo do email guarda nome do aluno, protocolo e parecer em texto integral.
     * Preserva-se o registro de que houve envio — é a comprovação para a coordenação —
     * sem preservar o conteúdo.
     */
    private void anonimizarEmails(Long solicitacaoId) {
        List<EmailLog> logs = emailLogRepository.findBySolicitacaoIdOrderByEnviadoEmDesc(solicitacaoId);
        logs.forEach(log -> {
            log.setDestinatario(ANONIMIZADO);
            log.setAssunto(ANONIMIZADO);
            log.setCorpo(ANONIMIZADO);
            log.setErro(log.getErro() != null ? ANONIMIZADO : null);
        });
        emailLogRepository.saveAll(logs);
    }

    /** Email e comentário do professor também são dado pessoal — dele, não do aluno. */
    private void anonimizarPareceres(Long solicitacaoId) {
        List<ParecerProfessor> pareceres =
                parecerProfessorRepository.findBySolicitacaoIdOrderByCriadoEmDesc(solicitacaoId);
        pareceres.forEach(p -> {
            p.setEmailProfessor(ANONIMIZADO + "@anonimizado.local");
            p.setComentarioCoordenador(p.getComentarioCoordenador() != null ? ANONIMIZADO : null);
            p.setParecerProfessor(p.getParecerProfessor() != null ? ANONIMIZADO : null);
            // Queima o código: um token de acesso sobrevivente daria entrada num
            // registro anonimizado.
            p.setUsado(true);
        });
        parecerProfessorRepository.saveAll(pareceres);
    }

    /**
     * Remove o arquivo do disco e o registro. Falha em um anexo não interrompe o job:
     * o resto da anonimização é mais importante que um arquivo órfão, que fica logado
     * para limpeza manual.
     */
    private void apagarAnexos(Long solicitacaoId) {
        List<Anexo> anexos = anexoRepository.findBySolicitacaoId(solicitacaoId);
        for (Anexo anexo : anexos) {
            try {
                Path arquivo = anexoService.getArquivo(anexo);
                boolean apagado = Files.deleteIfExists(arquivo);
                if (!apagado) {
                    logger.warn("[ANONIMIZACAO] Arquivo já ausente no disco: {}", anexo.getNomeArmazenado());
                }
            } catch (IOException e) {
                logger.error("[ANONIMIZACAO] Falha ao apagar anexo {}: {}",
                        anexo.getNomeArmazenado(), e.getMessage());
            }
        }
        anexoRepository.deleteAll(anexos);
    }
}
