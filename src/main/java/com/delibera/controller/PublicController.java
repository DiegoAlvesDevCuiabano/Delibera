package com.delibera.controller;

import com.delibera.model.dto.SuporteContatoDTO;
import com.delibera.service.SuporteService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class PublicController {

    private final SuporteService suporteService;

    public PublicController(SuporteService suporteService) {
        this.suporteService = suporteService;
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
    public String suporte(Model model) {
        model.addAttribute("suporte", new SuporteContatoDTO());
        return "pages/public/suporte";
    }

    @PostMapping("/suporte")
    public String suporteEnviar(@Valid @ModelAttribute("suporte") SuporteContatoDTO contato,
                                 BindingResult bindingResult,
                                 HttpServletRequest request,
                                 Model model) {
        boolean isHtmx = "true".equals(request.getHeader("HX-Request"));

        if (bindingResult.hasErrors()) {
            model.addAttribute("mensagem", "Confira os campos e informe um email válido.");
            model.addAttribute("erroSuporte", true);
            return isHtmx ? "fragments/suporte-resposta :: erro" : "pages/public/suporte";
        }

        try {
            suporteService.enviar(contato, IpExtractor.getClientIp(request));
        } catch (SuporteService.CotaExcedidaException e) {
            model.addAttribute("mensagem",
                    "Recebemos várias mensagens deste acesso. Tente novamente em uma hora.");
            model.addAttribute("erroSuporte", true);
            return isHtmx ? "fragments/suporte-resposta :: erro" : "pages/public/suporte";
        }

        model.addAttribute("mensagem",
                "Retornaremos para " + contato.getEmail() + " o mais breve possível.");
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
