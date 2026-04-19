package com.delibera.controller;

import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.service.AnexoService;
import com.delibera.service.EmailService;
import com.delibera.service.SolicitacaoService;
import com.delibera.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/coordenacao")
public class CoordenacaoController {

    private final SolicitacaoService solicitacaoService;
    private final UsuarioService usuarioService;
    private final AnexoService anexoService;
    private final EmailService emailService;

    public CoordenacaoController(SolicitacaoService solicitacaoService, UsuarioService usuarioService,
                                  AnexoService anexoService, EmailService emailService) {
        this.solicitacaoService = solicitacaoService;
        this.usuarioService = usuarioService;
        this.anexoService = anexoService;
        this.emailService = emailService;
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Authentication auth, Model model) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Long instId = coord.getInstituicao().getId();

        long total = solicitacaoService.contarTotal(instId);
        long pendentes = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.EM_ANALISE)
                + solicitacaoService.contarPorStatus(instId, StatusSolicitacao.AGUARDANDO_PROFESSOR);
        long resolvidas = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.RESOLVIDO);
        long negadas = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.NEGADO);

        List<Solicitacao> pendentesList = solicitacaoService.listarPendentesPorInstituicao(instId);
        List<Solicitacao> todas = solicitacaoService.listarPorInstituicao(instId);

        model.addAttribute("coord", coord);
        model.addAttribute("total", total);
        model.addAttribute("pendentes", pendentes);
        model.addAttribute("resolvidas", resolvidas);
        model.addAttribute("negadas", negadas);
        model.addAttribute("pendentesList", pendentesList);
        model.addAttribute("todasSolicitacoes", todas);
        return "pages/coordenacao/dashboard";
    }

    @GetMapping("/solicitacoes/{id}")
    public String analisar(@PathVariable Long id, Authentication auth, Model model) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Solicitacao solicitacao = solicitacaoService.buscarPorId(id);
        solicitacaoService.verificarOwnership(solicitacao, coord);

        model.addAttribute("solicitacao", solicitacao);
        model.addAttribute("coord", coord);
        model.addAttribute("anexos", anexoService.listarPorSolicitacao(id));
        return "pages/coordenacao/analisar-solicitacao";
    }

    @PostMapping("/solicitacoes/{id}/deferir")
    public String deferir(@PathVariable Long id, @RequestParam String parecer,
                           Authentication auth, RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Solicitacao s = solicitacaoService.deferir(id, parecer, coord);
        emailService.notificarMudancaStatus(s);
        redirectAttributes.addFlashAttribute("mensagem", "Solicitação deferida com sucesso.");
        return "redirect:/coordenacao";
    }

    @PostMapping("/solicitacoes/{id}/encaminhar")
    public String encaminhar(@PathVariable Long id, @RequestParam String parecer,
                              Authentication auth, RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Solicitacao s = solicitacaoService.encaminhar(id, parecer, coord);
        emailService.notificarMudancaStatus(s);
        redirectAttributes.addFlashAttribute("mensagem", "Solicitação encaminhada ao professor.");
        return "redirect:/coordenacao";
    }

    @PostMapping("/solicitacoes/{id}/indeferir")
    public String indeferir(@PathVariable Long id, @RequestParam String parecer,
                             Authentication auth, RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Solicitacao s = solicitacaoService.indeferir(id, parecer, coord);
        emailService.notificarMudancaStatus(s);
        redirectAttributes.addFlashAttribute("mensagem", "Solicitação indeferida.");
        return "redirect:/coordenacao";
    }
}
