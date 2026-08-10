package com.delibera.controller;

import com.delibera.service.EmailHtmlService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class PublicController {

    private static final Logger logger = LoggerFactory.getLogger(PublicController.class);

    private static final int LIMITE_SUPORTE_HORA = 5;

    private final EmailHtmlService emailHtmlService;
    private final com.delibera.config.CotaPublica cotaPublica;

    public PublicController(EmailHtmlService emailHtmlService, com.delibera.config.CotaPublica cotaPublica) {
        this.emailHtmlService = emailHtmlService;
        this.cotaPublica = cotaPublica;
    }

    /**
     * Alvo do forward do {@code RateLimitFilter}. O forward preserva o método da
     * requisição original — que é sempre POST nas rotas protegidas —, então este
     * handler precisa aceitar GET e POST. Restringi-lo a GET fazia toda requisição
     * bloqueada estourar {@code HttpRequestMethodNotSupportedException}, cair no
     * catch-all do GlobalExceptionHandler e gravar um ErroLog + disparar um email
     * de alerta por tentativa — transformando o rate limiter em amplificador.
     */
    @RequestMapping(value = "/rate-limit-consulta", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public String rateLimitConsulta(HttpServletRequest request, Model model) {
        Object minutos = request.getAttribute("minutosRestantes");
        model.addAttribute("minutosRestantes", minutos != null ? minutos : 15);
        return "error/rate-limit";
    }

    @GetMapping("/")
    public String index() {
        return "pages/public/index";
    }

    @GetMapping("/login")
    public String login() {
        return "pages/public/login";
    }

    @GetMapping("/sobre")
    public String sobre() {
        return "pages/public/sobre";
    }

    @GetMapping("/planos")
    public String planos() {
        return "pages/public/planos";
    }

    @GetMapping("/suporte")
    public String suporte() {
        return "pages/public/suporte";
    }

    @PostMapping("/suporte")
    public String suporteEnviar(@RequestParam String nome,
                                 @RequestParam String email,
                                 @RequestParam String assunto,
                                 @RequestParam String mensagem,
                                 HttpServletRequest request,
                                 Model model) {
        boolean isHtmx = "true".equals(request.getHeader("HX-Request"));

        // Sem cota, este endpoint anônimo dispara emails ilimitados pelo SMTP do
        // produto — o caminho mais curto para o remetente ser bloqueado pelo provedor.
        if (!cotaPublica.permitir(IpExtractor.getClientIp(request), "/suporte", LIMITE_SUPORTE_HORA)) {
            model.addAttribute("mensagem",
                    "Recebemos várias mensagens deste acesso. Tente novamente em uma hora.");
            return isHtmx ? "fragments/suporte-resposta :: sucesso" : "pages/public/suporte";
        }

        // Async — dispara e responde imediatamente
        emailHtmlService.enviarContatoSuporte(nome, email, assunto, mensagem);
        logger.info("[SUPORTE] Mensagem de {} ({}) sobre '{}' enfileirada", nome, email, assunto);

        model.addAttribute("mensagem",
                "Retornaremos para " + email + " o mais breve possível.");
        return isHtmx ? "fragments/suporte-resposta :: sucesso" : "pages/public/suporte";
    }

    @GetMapping("/termos")
    public String termos() {
        return "pages/public/termos";
    }

    @GetMapping("/privacidade")
    public String privacidade() {
        return "pages/public/privacidade";
    }
}
