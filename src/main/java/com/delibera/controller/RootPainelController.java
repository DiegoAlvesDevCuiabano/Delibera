package com.delibera.controller;

import com.delibera.model.entity.Instituicao;
import com.delibera.repository.ErroLogRepository;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.SolicitacaoRepository;
import com.delibera.repository.UsuarioRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/root")
public class RootPainelController {

    private final InstituicaoRepository instituicaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final ErroLogRepository erroLogRepository;

    public RootPainelController(InstituicaoRepository instituicaoRepository,
                                UsuarioRepository usuarioRepository,
                                SolicitacaoRepository solicitacaoRepository,
                                ErroLogRepository erroLogRepository) {
        this.instituicaoRepository = instituicaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.erroLogRepository = erroLogRepository;
    }

    @GetMapping({"", "/", "/painel"})
    public String painel(Model model) {
        List<Instituicao> instituicoes = instituicaoRepository.findAll();

        model.addAttribute("totalInstituicoes", instituicoes.size());
        model.addAttribute("totalUsuarios", usuarioRepository.count());
        model.addAttribute("totalSolicitacoes", solicitacaoRepository.count());
        model.addAttribute("errosPendentes", erroLogRepository.countByResolvidoFalse());
        model.addAttribute("instituicoes", instituicoes);
        model.addAttribute("solicitacaoRepo", solicitacaoRepository);
        return "pages/root/painel";
    }
}
