package com.delibera.service;

import com.delibera.model.dto.ExportacaoAlunoDTO;
import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.repository.SolicitacaoRepository;
import com.delibera.util.TokenAssinado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acesso do aluno aos próprios dados via link assinado enviado por email.
 *
 * <p>O aluno não tem conta. Antes, a única porta era protocolo + email, o que deixava
 * três buracos: quem perdia o protocolo não tinha como recuperá-lo, a tela de status
 * vivia de flash attribute e sumia num F5, e não havia caminho para exportar ou
 * pedir exclusão — direitos que a política de privacidade promete.
 *
 * <p>Um único token resolve os três: a chave é o email (que já é a chave do produto),
 * o link chega por email — o que prova posse da caixa — e vale por prazo curto.
 */
@Service
public class AcessoAlunoService {

    private static final Logger logger = LoggerFactory.getLogger(AcessoAlunoService.class);

    /** Escopo do token — não intercambiável com o do voucher. */
    public static final String RECURSO = "aluno";

    /** Prazo do link. Curto porque dá acesso a todo o histórico do aluno. */
    public static final int VALIDADE_DIAS = 7;

    private final SolicitacaoRepository solicitacaoRepository;
    private final TokenAssinado tokenAssinado;

    public AcessoAlunoService(SolicitacaoRepository solicitacaoRepository, TokenAssinado tokenAssinado) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.tokenAssinado = tokenAssinado;
    }

    public String gerarToken(String email) {
        return tokenAssinado.gerarPara(RECURSO, normalizar(email), VALIDADE_DIAS);
    }

    /** @return o email do token, ou null se inválido/expirado. */
    public String emailDoToken(String token) {
        return tokenAssinado.validarSujeito(RECURSO, token);
    }

    /** Solicitações visíveis do aluno — exclui as já marcadas para exclusão. */
    @Transactional(readOnly = true)
    public List<Solicitacao> listarPorEmail(String email) {
        List<Solicitacao> visiveis = solicitacaoRepository
                .findByAlunoEmailOrderByCriadoEmDesc(normalizar(email)).stream()
                .filter(s -> s.getExcluidoEm() == null)
                .toList();
        // Inicializa o histórico aqui dentro: com open-in-view desligado, tocar a
        // coleção depois (na view ou ao serializar o export) estoura LazyInitialization.
        visiveis.forEach(s -> s.getHistorico().size());
        return visiveis;
    }

    /**
     * Monta a exportação de portabilidade dentro da transação.
     *
     * <p>Construir o DTO no controller funcionava só por acidente do open-in-view: no
     * perfil que o desliga, a serialização do histórico falhava e o erro virava uma
     * página HTML no lugar do JSON.
     */
    @Transactional(readOnly = true)
    public ExportacaoAlunoDTO exportar(String email, java.util.function.Function<Long, List<Anexo>> anexosDe) {
        List<Solicitacao> solicitacoes = listarPorEmail(email);
        Map<Long, List<Anexo>> anexos = new LinkedHashMap<>();
        solicitacoes.forEach(s -> anexos.put(s.getId(), anexosDe.apply(s.getId())));
        return new ExportacaoAlunoDTO(normalizar(email), solicitacoes, anexos);
    }

    /**
     * Busca uma solicitação garantindo que pertence ao dono do token.
     * Sem esta amarração, um token válido daria acesso a qualquer protocolo.
     */
    @Transactional(readOnly = true)
    public Solicitacao buscarDoAluno(String protocolo, String email) {
        Solicitacao s = solicitacaoRepository
                .findByProtocoloAndEmail(protocolo.trim().toUpperCase(), normalizar(email))
                .orElseThrow(() -> new IllegalArgumentException("Protocolo não encontrado para este email"));
        if (s.getExcluidoEm() != null) {
            throw new IllegalArgumentException("Solicitação marcada para exclusão");
        }
        s.getHistorico().size(); // force fetch
        return s;
    }

    /**
     * Marca as solicitações do aluno para exclusão (LGPD, art. 18).
     *
     * <p>Não apaga na hora: grava {@code excluidoEm} e deixa o job de anonimização
     * agir depois de 90 dias, que é o que a política de privacidade descreve e o que
     * concilia o direito de eliminação com a retenção acadêmica.
     *
     * <p>Solicitações em andamento ficam de fora — excluí-las apagaria um processo que
     * a coordenação ainda precisa decidir. O aluno pode cancelar antes, se quiser.
     *
     * @return quantas foram marcadas.
     */
    @Transactional
    public int solicitarExclusao(String email) {
        List<Solicitacao> finalizadas = listarPorEmail(email).stream()
                .filter(s -> s.getStatus() != StatusSolicitacao.EM_ANALISE
                          && s.getStatus() != StatusSolicitacao.AGUARDANDO_PROFESSOR)
                .toList();

        LocalDateTime agora = LocalDateTime.now();
        finalizadas.forEach(s -> s.setExcluidoEm(agora));
        solicitacaoRepository.saveAll(finalizadas);

        logger.info("[LGPD] Exclusão solicitada — {} solicitações marcadas", finalizadas.size());
        return finalizadas.size();
    }

    /** Quantas solicitações não podem ser excluídas agora por estarem em andamento. */
    @Transactional(readOnly = true)
    public long contarEmAndamento(String email) {
        return listarPorEmail(email).stream()
                .filter(s -> s.getStatus() == StatusSolicitacao.EM_ANALISE
                          || s.getStatus() == StatusSolicitacao.AGUARDANDO_PROFESSOR)
                .count();
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
