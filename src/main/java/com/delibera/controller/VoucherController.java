package com.delibera.controller;

import com.delibera.model.entity.Instituicao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.util.TokenAssinado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Ativação do voucher de extensão do piloto, via link enviado por email.
 *
 * <p>O link carrega um token assinado, não o id da instituição: com {@code ?inst=N}
 * qualquer pessoa enumerava ids e queimava o voucher de todos os prospects.
 *
 * <p>A ativação é POST. Em GET o voucher era consumido sozinho — scanners de link
 * corporativos (Safe Links e afins) seguem URLs das mensagens que entregam, então
 * o voucher morria antes de o destinatário abrir o email.
 */
@Controller
public class VoucherController {

    private static final Logger logger = LoggerFactory.getLogger(VoucherController.class);

    /** Escopo do token — impede reaproveitar um token de outro recurso aqui. */
    public static final String RECURSO = "voucher";

    private final InstituicaoRepository instituicaoRepository;
    private final TokenAssinado tokenAssinado;

    public VoucherController(InstituicaoRepository instituicaoRepository,
                             TokenAssinado tokenAssinado) {
        this.instituicaoRepository = instituicaoRepository;
        this.tokenAssinado = tokenAssinado;
    }

    /** Tela de confirmação. Não altera nada. */
    @GetMapping("/voucher/ativar")
    public String confirmar(@RequestParam(name = "t", required = false) String token, Model model) {
        Instituicao instituicao = resolver(token);
        if (instituicao == null) {
            model.addAttribute("erro", "Este link de voucher é inválido ou expirou.");
            return "pages/public/voucher-resultado";
        }

        String indisponivel = motivoIndisponivel(instituicao);
        if (indisponivel != null) {
            model.addAttribute("erro", indisponivel);
            return "pages/public/voucher-resultado";
        }

        model.addAttribute("confirmar", true);
        model.addAttribute("token", token);
        model.addAttribute("instituicao", instituicao);
        return "pages/public/voucher-resultado";
    }

    /** Ativação efetiva. */
    @PostMapping("/voucher/ativar")
    @Transactional
    public String ativar(@RequestParam(name = "t", required = false) String token, Model model) {
        Instituicao instituicao = resolver(token);
        if (instituicao == null) {
            model.addAttribute("erro", "Este link de voucher é inválido ou expirou.");
            return "pages/public/voucher-resultado";
        }

        String indisponivel = motivoIndisponivel(instituicao);
        if (indisponivel != null) {
            model.addAttribute("erro", indisponivel);
            return "pages/public/voucher-resultado";
        }

        instituicao.ativarVoucher();
        instituicaoRepository.save(instituicao);

        logger.info("[VOUCHER] Voucher ativado para instituição {} (id={})",
                instituicao.getNome(), instituicao.getId());

        model.addAttribute("sucesso", true);
        model.addAttribute("instituicao", instituicao);
        return "pages/public/voucher-resultado";
    }

    private Instituicao resolver(String token) {
        Long id = tokenAssinado.validar(RECURSO, token);
        return id == null ? null : instituicaoRepository.findById(id).orElse(null);
    }

    /** @return motivo pelo qual o voucher não pode ser ativado, ou null se puder. */
    private String motivoIndisponivel(Instituicao instituicao) {
        if (instituicao.getVoucherAtivadoEm() != null) {
            return "Este voucher já foi utilizado anteriormente.";
        }
        if (instituicao.isAcessoAtivo()) {
            return "Seu acesso ainda está ativo. Não é necessário ativar o voucher agora.";
        }
        return null;
    }
}
