package com.delibera.controller;

import com.delibera.model.entity.Usuario;
import com.delibera.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Roteador de entrada pós-login. Exige autenticação (ver SecurityConfig) — sem isso
 * {@code auth} chega null e o NPE vira uma página 500 servida a anônimos, gravando
 * ErroLog e disparando alerta a cada acesso.
 */
@Controller
public class PainelController {

    private final UsuarioService usuarioService;

    public PainelController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/painel")
    public String painel(Authentication auth) {
        Usuario usuario = usuarioService.buscarPorEmail(auth.getName());
        // Mesmo destino do AuthSuccessHandler — os dois precisam concordar.
        return switch (usuario.getRole()) {
            case ROOT -> "redirect:/root/painel";
            case GESTAO, ADMIN_INSTITUICAO, COORDENADOR -> "redirect:/coordenacao";
        };
    }
}
