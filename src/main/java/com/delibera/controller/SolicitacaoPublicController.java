package com.delibera.controller;

import com.delibera.model.dto.NovaSolicitacaoDTO;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.service.AnexoService;
import com.delibera.service.EmailService;
import com.delibera.service.SolicitacaoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

/**
 * Controller público para o fluxo do aluno (sem login).
 * Rotas: /nova-solicitacao, /consultar-protocolo, /status/{protocolo}
 */
@Controller
public class SolicitacaoPublicController {

    private final SolicitacaoService solicitacaoService;
    private final InstituicaoRepository instituicaoRepository;
    private final AnexoService anexoService;
    private final EmailService emailService;

    public SolicitacaoPublicController(SolicitacaoService solicitacaoService,
                                        InstituicaoRepository instituicaoRepository,
                                        AnexoService anexoService,
                                        EmailService emailService) {
        this.solicitacaoService = solicitacaoService;
        this.instituicaoRepository = instituicaoRepository;
        this.anexoService = anexoService;
        this.emailService = emailService;
    }

    @GetMapping("/nova-solicitacao")
    public String formulario(Model model) {
        model.addAttribute("tipos", TipoSolicitacao.values());
        model.addAttribute("instituicoes", instituicaoRepository.findAll());
        return "pages/aluno/nova-solicitacao";
    }

    @PostMapping("/nova-solicitacao")
    public String criar(@Valid @ModelAttribute NovaSolicitacaoDTO dto,
                         BindingResult bindingResult,
                         @RequestParam(required = false) MultipartFile anexo,
                         Model model,
                         RedirectAttributes redirectAttributes) throws IOException {

        if (bindingResult.hasErrors()) {
            model.addAttribute("tipos", TipoSolicitacao.values());
            model.addAttribute("instituicoes", instituicaoRepository.findAll());
            model.addAttribute("dto", dto);
            return "pages/aluno/nova-solicitacao";
        }

        Instituicao inst = instituicaoRepository.findById(dto.getInstituicaoId())
                .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

        Solicitacao s = new Solicitacao();
        s.setTipo(dto.getTipo());
        s.setAlunoNome(dto.getAlunoNome().trim());
        s.setAlunoEmail(dto.getAlunoEmail().trim().toLowerCase());
        s.setAlunoWhatsapp(dto.getAlunoWhatsapp());
        s.setAlunoCurso(dto.getAlunoCurso());
        s.setAlunoTurma(dto.getAlunoTurma());
        s.setDisciplina(dto.getDisciplina().trim());
        s.setProfessor(dto.getProfessor());
        s.setDataOcorrencia(dto.getDataOcorrencia());
        s.setDatasFalta(dto.getDatasFalta());
        s.setNotaAtual(dto.getNotaAtual());
        s.setNotaEsperada(dto.getNotaEsperada());
        s.setJustificativa(dto.getJustificativa().trim());

        Solicitacao criada = solicitacaoService.criar(s, inst);

        // Upload de anexo (se enviado)
        if (anexo != null && !anexo.isEmpty()) {
            anexoService.salvar(anexo, criada);
        }

        // Notificação por email
        emailService.notificarCriacao(criada);

        redirectAttributes.addFlashAttribute("solicitacao", criada);
        return "redirect:/status/" + criada.getProtocolo() + "?criada=true";
    }

    @GetMapping("/consultar-protocolo")
    public String consultarForm(@RequestParam(required = false) String protocolo,
                                 @RequestParam(required = false) String email,
                                 Model model) {
        if (protocolo != null && !protocolo.isBlank()) {
            try {
                Solicitacao s = solicitacaoService.buscarPorProtocolo(protocolo.trim().toUpperCase());
                return "redirect:/status/" + s.getProtocolo();
            } catch (IllegalArgumentException e) {
                model.addAttribute("erro", "Protocolo não encontrado: " + protocolo);
            }
        }

        if (email != null && !email.isBlank()) {
            List<Solicitacao> lista = solicitacaoService.listarPorEmail(email.trim());
            model.addAttribute("solicitacoes", lista);
            model.addAttribute("emailBusca", email);
        }

        return "pages/aluno/consultar-protocolo";
    }

    @GetMapping("/status/{protocolo}")
    public String status(@PathVariable String protocolo,
                          @RequestParam(required = false) Boolean criada,
                          Model model) {
        Solicitacao s = solicitacaoService.buscarPorProtocolo(protocolo);
        model.addAttribute("solicitacao", s);
        model.addAttribute("recemCriada", criada != null && criada);
        return "pages/aluno/status-solicitacao";
    }
}
