package com.delibera.service;

import com.delibera.model.entity.ErroLog;
import com.delibera.model.entity.Instituicao;
import com.delibera.repository.ErroLogRepository;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.SolicitacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Job que roda a cada 1 hora verificando expiração de pilotos e vouchers.
 * Dispara emails de retenção conforme atividade da instituição.
 */
@Component
public class PilotoScheduledJob {

    private static final Logger logger = LoggerFactory.getLogger(PilotoScheduledJob.class);

    private final InstituicaoRepository instituicaoRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final EmailHtmlService emailHtmlService;
    private final ErroLogRepository erroLogRepository;
    private final AlertaErroService alertaErroService;

    public PilotoScheduledJob(InstituicaoRepository instituicaoRepository,
                               SolicitacaoRepository solicitacaoRepository,
                               EmailHtmlService emailHtmlService,
                               ErroLogRepository erroLogRepository,
                               AlertaErroService alertaErroService) {
        this.instituicaoRepository = instituicaoRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.emailHtmlService = emailHtmlService;
        this.erroLogRepository = erroLogRepository;
        this.alertaErroService = alertaErroService;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000) // 1 hora
    public void verificarPilotosExpirados() {
        try {
            logger.info("[PILOTO JOB] Verificando pilotos expirados...");
            LocalDateTime agora = LocalDateTime.now();

            // 0. Feedback dia ~15 do piloto
            List<Instituicao> aptosFeedback = instituicaoRepository.findAptosFeedback();
            int feedbackEnviados = 0;
            for (Instituicao inst : aptosFeedback) {
                long diasDesdeInicio = java.time.Duration.between(
                        inst.getPilotoExpiraEm().minusDays(30),
                        agora
                ).toDays();
                if (diasDesdeInicio >= 12 && diasDesdeInicio <= 18) {
                    emailHtmlService.enviarFeedbackDia15(inst);
                    feedbackEnviados++;
                    logger.info("[PILOTO JOB] Feedback dia 15 enviado para {}", inst.getNome());
                }
            }

            // 1. Piloto expirado há 1-2 dias, sem voucher
            List<Instituicao> pilotoExpirado = instituicaoRepository.findPilotoExpirado(agora);
            for (Instituicao inst : pilotoExpirado) {
                if (inst.getPilotoExpiraEm().plusDays(1).isAfter(agora)) continue;

                boolean teveAtividade = solicitacaoRepository.countByInstituicaoId(inst.getId()) > 0;

                if (teveAtividade) {
                    emailHtmlService.enviarVoucherExtensao(inst);
                    logger.info("[PILOTO JOB] Voucher enviado para {} (teve atividade)", inst.getNome());
                } else {
                    emailHtmlService.enviarConviteRetorno(inst);
                    logger.info("[PILOTO JOB] Convite de retorno enviado para {} (sem atividade)", inst.getNome());
                }

                inst.setEmailPilotoEnviado(true);
                instituicaoRepository.save(inst);
            }

            // 2. Voucher expirado
            List<Instituicao> comVoucher = instituicaoRepository.findComVoucher();
            for (Instituicao inst : comVoucher) {
                if (inst.getVoucherAtivadoEm().plusDays(30).isAfter(agora)) continue;

                emailHtmlService.enviarContatoFinal(inst);
                logger.info("[PILOTO JOB] Email de contato final enviado para {}", inst.getNome());

                inst.setEmailVoucherEnviado(true);
                instituicaoRepository.save(inst);
            }

            logger.info("[PILOTO JOB] Concluído. {} feedbacks, {} pilotos, {} vouchers processados",
                    feedbackEnviados, pilotoExpirado.size(), comVoucher.size());
        } catch (Exception e) {
            logger.error("[PILOTO JOB ERRO] {}", e.getMessage(), e);
            registrarErroScheduled(e, "PilotoScheduledJob.verificarPilotosExpirados");
        }
    }

    private void registrarErroScheduled(Exception e, String origem) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stack = sw.toString();
            if (stack.length() > 10000) stack = stack.substring(0, 10000) + "\n... (truncado)";

            ErroLog erro = new ErroLog();
            erro.setTipo(e.getClass().getName());
            erro.setMensagem(e.getMessage());
            erro.setStacktrace(stack);
            erro.setUrl("@Scheduled: " + origem);
            erro.setMetodoHttp("JOB");
            erroLogRepository.save(erro);

            alertaErroService.alertarExcecao(erro);
            erro.setAlertaEnviado(true);
            erroLogRepository.save(erro);
        } catch (Exception logError) {
            logger.error("[PILOTO JOB] Falha ao registrar erro: {}", logError.getMessage());
        }
    }
}
