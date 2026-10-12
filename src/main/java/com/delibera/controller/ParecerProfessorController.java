package com.delibera.controller;

import com.delibera.config.GlobalRateLimiter;
import com.delibera.model.entity.ParecerProfessor;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.RespostaProfessor;
import com.delibera.repository.ParecerProfessorRepository;
import com.delibera.service.EmailService;
import com.delibera.service.RespostaProfessorService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class ParecerProfessorController {

    private static final Logger logger = LoggerFactory.getLogger(ParecerProfessorController.class);

    private final ParecerProfessorRepository parecerRepo;
    private final RespostaProfessorService respostaProfessorService;
    private final EmailService emailService;
    private final GlobalRateLimiter rateLimiter;

    public ParecerProfessorController(ParecerProfessorRepository parecerRepo,
                                       RespostaProfessorService respostaProfessorService,
                                       EmailService emailService,
                                       GlobalRateLimiter rateLimiter) {
        this.parecerRepo = parecerRepo;
        this.respostaProfessorService = respostaProfessorService;
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

        Solicitacao solicitacao;
        try {
            solicitacao = respostaProfessorService.responder(parecerId, codigoConfirmacao, resposta, comentario);
        } catch (RespostaProfessorService.CodigoIndisponivelException e) {
            rateLimiter.registrarFalha(ip, "/parecer-professor/responder");
            model.addAttribute("erro", "Código inválido ou prazo expirado");
            return "pages/public/parecer-professor";
        } catch (RespostaProfessorService.SolicitacaoConcluidaException e) {
            model.addAttribute("erro",
                    "Esta solicitação já foi concluída pela coordenação e não aguarda mais seu parecer.");
            return "pages/public/parecer-professor";
        }

        emailService.notificarMudancaStatus(solicitacao);

        logger.info("[PARECER PROFESSOR] Solicitação {} respondida por {} com: {}",
                solicitacao.getProtocolo(), parecerId, resposta);

        model.addAttribute("sucesso", true);
        return "pages/public/parecer-professor-sucesso";
    }
}
