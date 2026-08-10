package com.delibera.controller;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.enums.Role;
import com.delibera.model.enums.TipoAcaoAuditoria;
import com.delibera.repository.SolicitacaoRepository;
import com.delibera.service.AuditLogService;
import com.delibera.service.ProvisionamentoService;
import com.delibera.service.UsuarioService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gestão cross-tenant: cadastrar instituição, criar o primeiro coordenador, ligar
 * e desligar acesso, estender prazo.
 *
 * <p>Restrito a GESTAO e ROOT pelo SecurityConfig. É o que permite operar o produto
 * sem abrir o banco — e, junto com o AcessoInterceptor, fecha o ciclo comercial:
 * o piloto expira sozinho e a renovação passa por aqui.
 */
@Controller
@RequestMapping("/gestao")
public class GestaoController {

    private final ProvisionamentoService provisionamento;
    private final UsuarioService usuarioService;
    private final SolicitacaoRepository solicitacaoRepository;
    private final AuditLogService auditLogService;

    public GestaoController(ProvisionamentoService provisionamento,
                             UsuarioService usuarioService,
                             SolicitacaoRepository solicitacaoRepository,
                             AuditLogService auditLogService) {
        this.provisionamento = provisionamento;
        this.usuarioService = usuarioService;
        this.solicitacaoRepository = solicitacaoRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping({"", "/", "/instituicoes"})
    public String painel(Model model) {
        model.addAttribute("instituicoes", provisionamento.listarInstituicoes());
        model.addAttribute("solicitacaoRepo", solicitacaoRepository);
        model.addAttribute("roles", new Role[]{Role.COORDENADOR, Role.ADMIN_INSTITUICAO});
        return "pages/gestao/instituicoes";
    }

    @GetMapping("/instituicoes/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        Instituicao inst = provisionamento.listarInstituicoes().stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

        model.addAttribute("instituicao", inst);
        model.addAttribute("usuarios", provisionamento.listarUsuarios(id));
        model.addAttribute("totalSolicitacoes", solicitacaoRepository.countByInstituicaoId(id));
        model.addAttribute("roles", new Role[]{Role.COORDENADOR, Role.ADMIN_INSTITUICAO});
        return "pages/gestao/instituicao-detalhe";
    }

    @PostMapping("/instituicoes")
    public String criarInstituicao(@RequestParam String nome,
                                    @RequestParam(required = false) String sigla,
                                    @RequestParam(required = false) String dominio,
                                    Authentication auth, HttpServletRequest request,
                                    RedirectAttributes redirect) {
        try {
            Instituicao inst = provisionamento.criarInstituicao(nome, sigla, dominio);
            auditLogService.registrar(usuarioService.buscarPorEmail(auth.getName()),
                    TipoAcaoAuditoria.MUDANCA_STATUS, "INSTITUICAO", inst.getId(),
                    IpExtractor.getClientIp(request));
            redirect.addFlashAttribute("mensagem",
                    "Instituição \"" + inst.getNome() + "\" criada. Piloto de 30 dias já iniciado.");
            return "redirect:/gestao/instituicoes/" + inst.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/gestao";
        }
    }

    @PostMapping("/instituicoes/{id}/usuarios")
    public String criarUsuario(@PathVariable Long id,
                                @RequestParam String nome,
                                @RequestParam String email,
                                @RequestParam Role role,
                                Authentication auth, HttpServletRequest request,
                                RedirectAttributes redirect) {
        try {
            String senha = provisionamento.criarUsuario(nome, email, role, id);
            auditLogService.registrar(usuarioService.buscarPorEmail(auth.getName()),
                    TipoAcaoAuditoria.MUDANCA_STATUS, "USUARIO", id,
                    IpExtractor.getClientIp(request));
            // Mostrada uma única vez: não fica gravada em lugar nenhum em texto claro.
            redirect.addFlashAttribute("senhaGerada", senha);
            redirect.addFlashAttribute("emailCriado", email.trim().toLowerCase());
            redirect.addFlashAttribute("mensagem", "Usuário criado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/instituicoes/" + id;
    }

    @PostMapping("/instituicoes/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, Authentication auth,
                                 HttpServletRequest request, RedirectAttributes redirect) {
        Instituicao inst = provisionamento.alternarAtivo(id);
        auditLogService.registrar(usuarioService.buscarPorEmail(auth.getName()),
                TipoAcaoAuditoria.MUDANCA_STATUS, "INSTITUICAO", id,
                IpExtractor.getClientIp(request));
        redirect.addFlashAttribute("mensagem",
                "Instituição agora está " + (inst.isAtivo() ? "ativa" : "inativa") + ".");
        return "redirect:/gestao/instituicoes/" + id;
    }

    @PostMapping("/instituicoes/{id}/estender")
    public String estender(@PathVariable Long id, @RequestParam int dias,
                            Authentication auth, HttpServletRequest request,
                            RedirectAttributes redirect) {
        try {
            Instituicao inst = provisionamento.estenderAcesso(id, dias);
            auditLogService.registrar(usuarioService.buscarPorEmail(auth.getName()),
                    TipoAcaoAuditoria.MUDANCA_STATUS, "INSTITUICAO", id,
                    IpExtractor.getClientIp(request));
            redirect.addFlashAttribute("mensagem",
                    "Acesso estendido até " + inst.getPilotoExpiraEm().toLocalDate() + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/instituicoes/" + id;
    }
}
