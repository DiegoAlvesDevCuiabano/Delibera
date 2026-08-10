package com.delibera.controller;

import com.delibera.config.GlobalRateLimiter;
import com.delibera.model.entity.ParecerProfessor;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.RespostaProfessor;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.repository.ParecerProfessorRepository;
import com.delibera.repository.SolicitacaoRepository;
import com.delibera.service.EmailService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Controller
public class ParecerProfessorController {

    private static final Logger logger = LoggerFactory.getLogger(ParecerProfessorController.class);

    private final ParecerProfessorRepository parecerRepo;
    private final SolicitacaoRepository solicitacaoRepo;
    private final EmailService emailService;
    private final GlobalRateLimiter rateLimiter;

    public ParecerProfessorController(ParecerProfessorRepository parecerRepo,
                                       SolicitacaoRepository solicitacaoRepo,
                                       EmailService emailService,
                                       GlobalRateLimiter rateLimiter) {
        this.parecerRepo = parecerRepo;
        this.solicitacaoRepo = solicitacaoRepo;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
    }

    /** Page with form: protocol + code */
    @GetMapping("/parecer-professor")
    public String formulario() {
        return "pages/public/parecer-professor";
    }

    /** Validate protocol + code and show request data */
    @PostMapping("/parecer-professor")
    public String consultar(@RequestParam(required = false) String protocolo,
                             @RequestParam(required = false) String codigo,
                             HttpServletRequest request,
                             Model model) {

        String ip = IpExtractor.getClientIp(request);
        if (rateLimiter.isBlocked(ip)) {
            return "error/rate-limit";
        }

        if (protocolo == null || protocolo.isBlank() || codigo == null || codigo.isBlank()) {
            model.addAttribute("erro", "Informe o protocolo e o código de acesso");
            return "pages/public/parecer-professor";
        }

        ParecerProfessor parecer = parecerRepo.findByCodigoAcesso(codigo.trim().toUpperCase())
                .orElse(null);

        if (parecer == null
                || !parecer.getSolicitacao().getProtocolo().equalsIgnoreCase(protocolo.trim())
                || !parecer.isDisponivel()) {
            rateLimiter.registrarFalha(ip, "/parecer-professor");
            model.addAttribute("erro", "Protocolo ou código inválido, ou prazo expirado");
            return "pages/public/parecer-professor";
        }

        model.addAttribute("parecer", parecer);
        model.addAttribute("solicitacao", parecer.getSolicitacao());
        return "pages/public/parecer-professor-resposta";
    }

    /** Process professor's response */
    @PostMapping("/parecer-professor/responder")
    public String responder(@RequestParam Long parecerId,
                             @RequestParam String codigoConfirmacao,
                             @RequestParam RespostaProfessor resposta,
                             @RequestParam(required = false) String comentario,
                             HttpServletRequest request,
                             Model model) {

        String ip = IpExtractor.getClientIp(request);
        if (rateLimiter.isBlocked(ip)) {
            return "error/rate-limit";
        }

        ParecerProfessor parecer = parecerRepo.findById(parecerId).orElse(null);

        // Validate: exists, code matches, still available
        if (parecer == null
                || !parecer.getCodigoAcesso().equals(codigoConfirmacao)
                || !parecer.isDisponivel()) {
            // Sem registrar a falha, o código podia ser tentado indefinidamente:
            // o isBlocked() acima só bloqueia quem já falhou em outra rota.
            rateLimiter.registrarFalha(ip, "/parecer-professor/responder");
            model.addAttribute("erro", "Código inválido ou prazo expirado");
            return "pages/public/parecer-professor";
        }

        // A solicitação precisa continuar aguardando este parecer. Sem esta checagem
        // um parecer que chegasse depois de a coordenação já ter decidido reabria o
        // caso, levando RESOLVIDO/NEGADO/CANCELADA de volta para EM_ANALISE.
        if (parecer.getSolicitacao().getStatus() != StatusSolicitacao.AGUARDANDO_PROFESSOR) {
            logger.info("[PARECER PROFESSOR] Solicitação {} não aguarda mais parecer (status {})",
                    parecer.getSolicitacao().getProtocolo(), parecer.getSolicitacao().getStatus());
            model.addAttribute("erro",
                    "Esta solicitação já foi concluída pela coordenação e não aguarda mais seu parecer.");
            return "pages/public/parecer-professor";
        }

        // Save professor's response
        parecer.setResposta(resposta);
        parecer.setParecerProfessor(comentario != null ? comentario.trim() : null);
        parecer.setRespondidoEm(LocalDateTime.now());
        parecer.setUsado(true);
        parecerRepo.save(parecer);

        // Update solicitacao status back to EM_ANALISE
        Solicitacao s = parecer.getSolicitacao();
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.EM_ANALISE);
        String obs = "Resposta do professor: " + resposta.getDescricao();
        if (comentario != null && !comentario.isBlank()) {
            obs += " — " + comentario.trim();
        }
        s.adicionarHistorico(anterior, StatusSolicitacao.EM_ANALISE, obs);
        solicitacaoRepo.save(s);

        // Notify student of status change
        emailService.notificarMudancaStatus(s);

        logger.info("[PARECER PROFESSOR] Solicitação {} respondida por {} com: {}",
                s.getProtocolo(), parecer.getEmailProfessor(), resposta);

        model.addAttribute("sucesso", true);
        return "pages/public/parecer-professor-sucesso";
    }
}
