package com.delibera.controller;

import com.delibera.config.GlobalRateLimiter;
import com.delibera.model.TermosConstants;
import com.delibera.util.IpExtractor;
import com.delibera.model.dto.NovaSolicitacaoDTO;
import com.delibera.model.dto.StatusPublicoDTO;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.service.AnexoService;
import com.delibera.service.EmailService;
import com.delibera.service.SolicitacaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Controller público para o fluxo do aluno (sem login).
 * Rotas: /nova-solicitacao, /consultar-protocolo, /status/{protocolo}
 */
@Controller
public class SolicitacaoPublicController {

    /** Generoso: uma faculdade inteira pode estar atrás de um único IP (NAT). */
    private static final int LIMITE_SOLICITACOES_HORA = 20;

    private final SolicitacaoService solicitacaoService;
    private final InstituicaoRepository instituicaoRepository;
    private final AnexoService anexoService;
    private final EmailService emailService;
    private final GlobalRateLimiter rateLimiter;
    private final com.delibera.config.CotaPublica cotaPublica;

    public SolicitacaoPublicController(SolicitacaoService solicitacaoService,
                                        InstituicaoRepository instituicaoRepository,
                                        AnexoService anexoService,
                                        EmailService emailService,
                                        GlobalRateLimiter rateLimiter,
                                        com.delibera.config.CotaPublica cotaPublica) {
        this.solicitacaoService = solicitacaoService;
        this.instituicaoRepository = instituicaoRepository;
        this.anexoService = anexoService;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.cotaPublica = cotaPublica;
    }

    @GetMapping("/nova-solicitacao")
    public String formulario(Model model) {
        model.addAttribute("tipos", TipoSolicitacao.values());
        model.addAttribute("instituicoes", instituicaoRepository.findAll());
        model.addAttribute("versaoTermos", TermosConstants.VERSAO_ATUAL);
        if (!model.containsAttribute("novaSolicitacaoDTO")) {
            model.addAttribute("novaSolicitacaoDTO", new NovaSolicitacaoDTO());
        }
        return "pages/aluno/nova-solicitacao";
    }

    @PostMapping("/nova-solicitacao")
    public String criar(@Valid @ModelAttribute NovaSolicitacaoDTO dto,
                         BindingResult bindingResult,
                         @RequestParam(required = false) MultipartFile anexo,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) throws IOException {

        validarCamposCondicionais(dto, anexo, bindingResult);

        if (anexo != null && !anexo.isEmpty()) {
            try {
                anexoService.validar(anexo);
            } catch (IllegalArgumentException e) {
                bindingResult.reject("anexoInvalido", e.getMessage());
            } catch (IOException e) {
                bindingResult.reject("anexoInvalido", "Não foi possível ler o anexo enviado.");
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("tipos", TipoSolicitacao.values());
            model.addAttribute("instituicoes", instituicaoRepository.findAll());
            model.addAttribute("versaoTermos", TermosConstants.VERSAO_ATUAL);
            model.addAttribute("dto", dto);
            return "pages/aluno/nova-solicitacao";
        }

        // Dados inválidos não consomem a cota de criações. A cota é conferida
        // antes de persistir o pedido, salvar o anexo ou enviar emails.
        if (!cotaPublica.permitir(IpExtractor.getClientIp(request), "/nova-solicitacao", LIMITE_SOLICITACOES_HORA)) {
            model.addAttribute("minutosRestantes", 60);
            return "error/rate-limit";
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
        s.setDataAceiteTermos(LocalDateTime.now());
        s.setVersaoTermosAceita(TermosConstants.VERSAO_ATUAL);

        Solicitacao criada = solicitacaoService.criar(s, inst);

        // Upload de anexo (se enviado)
        if (anexo != null && !anexo.isEmpty()) {
            anexoService.salvar(anexo, criada);
        }

        // Notificação por email
        emailService.notificarCriacao(criada);

        redirectAttributes.addFlashAttribute("solicitacaoCriada", criada);
        return "redirect:/status/" + criada.getProtocolo();
    }

    private void validarCamposCondicionais(NovaSolicitacaoDTO dto, MultipartFile anexo,
                                             BindingResult bindingResult) {
        if (dto.getTipo() == null) {
            return;
        }

        switch (dto.getTipo()) {
            case SEGUNDA_CHAMADA -> {
                if (dto.getDataOcorrencia() == null) {
                    bindingResult.rejectValue("dataOcorrencia", "dataObrigatoria",
                            "Informe a data da prova.");
                }
                if (anexo == null || anexo.isEmpty()) {
                    bindingResult.reject("anexoObrigatorio",
                            "Anexe o atestado ou a justificativa oficial da segunda chamada.");
                }
            }
            case ABONO_FALTA, AJUSTE_PRESENCA -> {
                if (dto.getDataOcorrencia() == null) {
                    bindingResult.rejectValue("dataOcorrencia", "dataObrigatoria",
                            "Informe a data da falta.");
                }
            }
            case CONTESTACAO_NOTA -> {
                if (dto.getNotaAtual() == null || dto.getNotaAtual().isBlank()) {
                    bindingResult.rejectValue("notaAtual", "notaObrigatoria",
                            "Informe a nota atual.");
                }
                if (dto.getNotaEsperada() == null || dto.getNotaEsperada().isBlank()) {
                    bindingResult.rejectValue("notaEsperada", "notaObrigatoria",
                            "Informe a nota esperada.");
                }
            }
        }
    }

    @GetMapping("/consultar-protocolo")
    public String consultarForm() {
        return "pages/aluno/consultar-protocolo";
    }

    @PostMapping("/consultar-protocolo")
    public String consultarSubmit(@RequestParam(required = false) String protocolo,
                                   @RequestParam(required = false) String email,
                                   HttpServletRequest request,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (protocolo != null && !protocolo.isBlank() && email != null && !email.isBlank()) {
            try {
                Solicitacao s = solicitacaoService.buscarPorProtocoloEEmail(protocolo, email);
                redirectAttributes.addFlashAttribute("statusPublico", new StatusPublicoDTO(s));
                return "redirect:/status/" + s.getProtocolo();
            } catch (IllegalArgumentException e) {
                rateLimiter.registrarFalha(IpExtractor.getClientIp(request), "/consultar-protocolo");
                model.addAttribute("erro", "Protocolo ou email inválidos");
            }
        } else if (protocolo != null && !protocolo.isBlank()) {
            model.addAttribute("erro", "Informe também o email utilizado na solicitação");
        }

        model.addAttribute("protocolo", protocolo);
        model.addAttribute("email", email);
        return "pages/aluno/consultar-protocolo";
    }

    @PostMapping("/cancelar-solicitacao")
    public String cancelar(@RequestParam String protocolo,
                            @RequestParam String email,
                            HttpServletRequest request,
                            RedirectAttributes redirectAttributes) {
        try {
            Solicitacao s = solicitacaoService.cancelar(protocolo, email);
            emailService.notificarMudancaStatus(s);
            redirectAttributes.addFlashAttribute("statusPublico", new StatusPublicoDTO(s));
            redirectAttributes.addFlashAttribute("mensagemCancelamento", "Solicitação cancelada com sucesso.");
            return "redirect:/status/" + s.getProtocolo();
        } catch (IllegalArgumentException e) {
            rateLimiter.registrarFalha(IpExtractor.getClientIp(request), "/cancelar-solicitacao");
            redirectAttributes.addFlashAttribute("erroCancelamento", e.getMessage());
            return "redirect:/consultar-protocolo";
        }
    }

    @GetMapping("/status/{protocolo}")
    public String status(@PathVariable String protocolo, Model model) {
        // Acesso pós-criação: flash da entity completa (usada apenas para gerar DTO)
        Solicitacao flash = (Solicitacao) model.getAttribute("solicitacaoCriada");
        if (flash != null && flash.getProtocolo().equals(protocolo.trim().toUpperCase())) {
            model.addAttribute("status", new StatusPublicoDTO(flash));
            model.addAttribute("recemCriada", true);
            return "pages/aluno/status-solicitacao";
        }

        // Acesso via consulta: flash do DTO minimizado
        StatusPublicoDTO dto = (StatusPublicoDTO) model.getAttribute("statusPublico");
        if (dto != null && dto.getProtocolo().equals(protocolo.trim().toUpperCase())) {
            model.addAttribute("status", dto);
            model.addAttribute("recemCriada", false);
            return "pages/aluno/status-solicitacao";
        }

        // Acesso direto sem flash: redireciona para formulário
        return "redirect:/consultar-protocolo";
    }

}
