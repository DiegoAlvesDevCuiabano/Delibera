package com.delibera.service;

import com.delibera.exception.AccessDeniedException;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.repository.SolicitacaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
public class SolicitacaoService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final SolicitacaoRepository solicitacaoRepository;

    public SolicitacaoService(SolicitacaoRepository solicitacaoRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
    }

    public List<Solicitacao> listarPorEmail(String email) {
        return solicitacaoRepository.findByAlunoEmailOrderByCriadoEmDesc(email);
    }

    public List<Solicitacao> listarPorInstituicao(Long instituicaoId) {
        return solicitacaoRepository.findByInstituicaoIdOrderByCriadoEmDesc(instituicaoId);
    }

    public List<Solicitacao> listarPendentesPorInstituicao(Long instituicaoId) {
        return solicitacaoRepository.findPendentesByInstituicao(
                instituicaoId,
                List.of(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.AGUARDANDO_PROFESSOR)
        );
    }

    @Transactional(readOnly = true)
    public Solicitacao buscarPorId(Long id) {
        Solicitacao s = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada: " + id));
        s.getHistorico().size(); // force fetch
        return s;
    }

    /**
     * Busca interna (coordenação) — sem segundo fator.
     */
    @Transactional(readOnly = true)
    public Solicitacao buscarPorProtocolo(String protocolo) {
        Solicitacao s = solicitacaoRepository.findByProtocolo(protocolo)
                .orElseThrow(() -> new IllegalArgumentException("Protocolo não encontrado: " + protocolo));
        s.getHistorico().size(); // force fetch
        return s;
    }

    /**
     * Busca pública — exige protocolo + email (segundo fator).
     * Mensagem genérica para não revelar qual campo falhou.
     */
    @Transactional(readOnly = true)
    public Solicitacao buscarPorProtocoloEEmail(String protocolo, String email) {
        String protocoloNorm = protocolo.trim().toUpperCase();
        String emailNorm = email.trim().toLowerCase();
        Solicitacao s = solicitacaoRepository.findByProtocoloAndEmail(protocoloNorm, emailNorm)
                .orElseThrow(() -> new IllegalArgumentException("Protocolo ou email inválidos"));
        s.getHistorico().size(); // force fetch
        return s;
    }

    @Transactional
    public Solicitacao criar(Solicitacao solicitacao, Instituicao instituicao) {
        solicitacao.setInstituicao(instituicao);
        solicitacao.setStatus(StatusSolicitacao.EM_ANALISE);
        solicitacao.setProtocolo(gerarProtocolo());
        solicitacao.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno");
        return solicitacaoRepository.save(solicitacao);
    }

    /**
     * Estados a partir dos quais a coordenação ainda pode decidir.
     * RESOLVIDO, NEGADO e CANCELADA são finais: sem esta checagem era possível
     * deferir duas vezes, ou deferir o que o aluno já havia cancelado — cada
     * repetição gerando um novo email e uma nova entrada no histórico.
     */
    private static final List<StatusSolicitacao> DECIDIVEIS =
            List.of(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.AGUARDANDO_PROFESSOR);

    private void exigirDecidivel(Solicitacao s, String acao) {
        if (!DECIDIVEIS.contains(s.getStatus())) {
            throw new IllegalStateException(
                    "Não é possível " + acao + ": a solicitação já está " + s.getStatus().getDescricao() + ".");
        }
    }

    @Transactional
    public Solicitacao deferir(Long id, String parecer, String parecerInterno, Usuario coordenador) {
        Solicitacao s = buscarComOwnership(id, coordenador);
        exigirDecidivel(s, "deferir");
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.RESOLVIDO);
        s.setParecer(parecer);
        s.setParecerInterno(parecerInterno);
        s.adicionarHistorico(anterior, StatusSolicitacao.RESOLVIDO, parecer, coordenador);
        return solicitacaoRepository.save(s);
    }

    @Transactional
    public Solicitacao encaminhar(Long id, String parecer, Usuario coordenador) {
        Solicitacao s = buscarComOwnership(id, coordenador);
        exigirDecidivel(s, "encaminhar");
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.AGUARDANDO_PROFESSOR);
        s.adicionarHistorico(anterior, StatusSolicitacao.AGUARDANDO_PROFESSOR, parecer, coordenador);
        return solicitacaoRepository.save(s);
    }

    @Transactional
    public Solicitacao indeferir(Long id, String parecer, String parecerInterno, Usuario coordenador) {
        Solicitacao s = buscarComOwnership(id, coordenador);
        exigirDecidivel(s, "indeferir");
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.NEGADO);
        s.setParecer(parecer);
        s.setParecerInterno(parecerInterno);
        s.adicionarHistorico(anterior, StatusSolicitacao.NEGADO, parecer, coordenador);
        return solicitacaoRepository.save(s);
    }

    /**
     * Busca solicitação com verificação de ownership na query (defense in depth).
     * ROOT e GESTAO fazem busca sem filtro de instituição.
     */
    @Transactional(readOnly = true)
    public Solicitacao buscarComOwnership(Long id, Usuario coordenador) {
        Solicitacao s;
        if (coordenador.isCrossTenant()) {
            s = solicitacaoRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Solicitação não encontrada ou sem permissão"));
        } else {
            Long instId = coordenador.getInstituicao() != null
                    ? coordenador.getInstituicao().getId() : -1L;
            s = solicitacaoRepository.findByIdAndInstituicaoId(id, instId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Solicitação não encontrada ou sem permissão"));
        }
        s.getHistorico().size(); // force fetch
        return s;
    }

    // KPIs
    public long contarTotal(Long instituicaoId) {
        return solicitacaoRepository.countByInstituicaoId(instituicaoId);
    }

    public long contarPorStatus(Long instituicaoId, StatusSolicitacao status) {
        return solicitacaoRepository.countByInstituicaoIdAndStatus(instituicaoId, status);
    }

    @Transactional(readOnly = true)
    public Double calcularTempoMedioResolucaoDias(Long instituicaoId) {
        List<Solicitacao> finalizadas = solicitacaoRepository.findFinalizadasPorInstituicao(instituicaoId);
        if (finalizadas.isEmpty()) return null;
        java.util.OptionalDouble avg = finalizadas.stream()
                .filter(s -> s.getAtualizadoEm() != null)
                .mapToDouble(s -> java.time.Duration.between(s.getCriadoEm(), s.getAtualizadoEm()).toHours() / 24.0)
                .average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 10) / 10.0 : null;
    }

    public Page<Solicitacao> listarPorInstituicaoComFiltros(Long instituicaoId, String busca, StatusSolicitacao status, Pageable pageable) {
        String buscaNorm = (busca != null && !busca.isBlank()) ? busca.trim() : null;
        if (buscaNorm == null && status == null) {
            return solicitacaoRepository.findByInstituicaoIdOrderByCriadoEmDesc(instituicaoId, pageable);
        }
        if (buscaNorm == null) {
            return solicitacaoRepository.findByInstituicaoIdAndStatusOrderByCriadoEmDesc(
                    instituicaoId, status, pageable);
        }
        if (status == null) {
            return solicitacaoRepository.findByInstituicaoAndBusca(instituicaoId, buscaNorm, pageable);
        }
        return solicitacaoRepository.findByInstituicaoBuscaAndStatus(
                instituicaoId, buscaNorm, status, pageable);
    }

    /**
     * Cancelamento pelo aluno — exige protocolo + email como segundo fator.
     * Só permite cancelar se o status não for final (RESOLVIDO, NEGADO, CANCELADA).
     */
    @Transactional
    public Solicitacao cancelar(String protocolo, String email) {
        Solicitacao s = buscarPorProtocoloEEmail(protocolo, email);
        if (s.getStatus() == StatusSolicitacao.RESOLVIDO
                || s.getStatus() == StatusSolicitacao.NEGADO
                || s.getStatus() == StatusSolicitacao.CANCELADA) {
            throw new IllegalArgumentException("Solicitação já foi finalizada e não pode ser cancelada");
        }
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.CANCELADA);
        s.adicionarHistorico(anterior, StatusSolicitacao.CANCELADA, "Cancelada pelo aluno");
        return solicitacaoRepository.save(s);
    }

    /**
     * Verifica se a solicitação pertence à instituição do coordenador.
     * ROOT e GESTAO têm acesso cross-tenant.
     */
    public void verificarOwnership(Solicitacao solicitacao, Usuario coordenador) {
        if (coordenador.isCrossTenant()) return;
        if (coordenador.getInstituicao() == null) {
            throw new AccessDeniedException("Usuário sem instituição vinculada");
        }
        if (!solicitacao.getInstituicao().getId().equals(coordenador.getInstituicao().getId())) {
            throw new AccessDeniedException("Solicitação não pertence à sua instituição");
        }
    }

    private String gerarProtocolo() {
        int ano = Year.now().getValue();
        String protocolo;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(BASE36_CHARS.charAt(SECURE_RANDOM.nextInt(36)));
            }
            protocolo = String.format("DEL-%d-%s", ano, sb);
        } while (solicitacaoRepository.findByProtocolo(protocolo).isPresent());
        return protocolo;
    }
}
