package com.delibera.controller;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Usuario;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.service.EmailHtmlService;
import com.delibera.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class FeedbackController {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);

    private final UsuarioService usuarioService;
    private final InstituicaoRepository instituicaoRepository;
    private final EmailHtmlService emailHtmlService;

    public FeedbackController(UsuarioService usuarioService,
                               InstituicaoRepository instituicaoRepository,
                               EmailHtmlService emailHtmlService) {
        this.usuarioService = usuarioService;
        this.instituicaoRepository = instituicaoRepository;
        this.emailHtmlService = emailHtmlService;
    }

    @PostMapping("/feedback")
    @ResponseBody
    public String enviarFeedback(@RequestParam int nota,
                                  @RequestParam(required = false) String comentario,
                                  Authentication auth) {
        Usuario user = usuarioService.buscarPorEmail(auth.getName());
        Instituicao inst = user.getInstituicao();

        if (inst != null) {
            inst.setFeedbackRespondido(true);
            instituicaoRepository.save(inst);
        }

        // Envia feedback por email pro admin
        String assunto = "Feedback — " + (inst != null ? inst.getNome() : "ROOT") + " — Nota " + nota + "/5";
        String mensagem = "Nota: " + nota + "/5"
                + (comentario != null && !comentario.isBlank() ? "\nComentário: " + comentario : "")
                + "\nUsuário: " + user.getNome() + " (" + user.getEmail() + ")"
                + "\nInstituição: " + (inst != null ? inst.getNome() : "N/A");

        emailHtmlService.enviarContatoSuporte("Feedback Automático", user.getEmail(), assunto, mensagem);

        logger.info("[FEEDBACK] Nota {} de {} ({})", nota, user.getNome(),
                inst != null ? inst.getNome() : "ROOT");

        return "ok";
    }
}
