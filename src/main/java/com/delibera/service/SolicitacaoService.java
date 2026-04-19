package com.delibera.service;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.repository.SolicitacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SolicitacaoService {

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

    @Transactional(readOnly = true)
    public Solicitacao buscarPorProtocolo(String protocolo) {
        Solicitacao s = solicitacaoRepository.findByProtocolo(protocolo)
                .orElseThrow(() -> new IllegalArgumentException("Protocolo não encontrado: " + protocolo));
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

    @Transactional
    public Solicitacao deferir(Long id, String parecer, Usuario coordenador) {
        Solicitacao s = buscarPorId(id);
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.RESOLVIDO);
        s.setParecer(parecer);
        s.adicionarHistorico(anterior, StatusSolicitacao.RESOLVIDO, parecer, coordenador);
        return solicitacaoRepository.save(s);
    }

    @Transactional
    public Solicitacao indeferir(Long id, String parecer, Usuario coordenador) {
        Solicitacao s = buscarPorId(id);
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.NEGADO);
        s.setParecer(parecer);
        s.adicionarHistorico(anterior, StatusSolicitacao.NEGADO, parecer, coordenador);
        return solicitacaoRepository.save(s);
    }

    @Transactional
    public Solicitacao cancelar(Long id, String emailAluno) {
        Solicitacao s = buscarPorId(id);
        if (!s.getAlunoEmail().equalsIgnoreCase(emailAluno)) {
            throw new IllegalArgumentException("Somente o aluno pode cancelar sua solicitação");
        }
        if (s.getStatus() == StatusSolicitacao.RESOLVIDO || s.getStatus() == StatusSolicitacao.NEGADO) {
            throw new IllegalStateException("Não é possível cancelar uma solicitação já finalizada");
        }
        StatusSolicitacao anterior = s.getStatus();
        s.setStatus(StatusSolicitacao.CANCELADA);
        s.adicionarHistorico(anterior, StatusSolicitacao.CANCELADA, "Cancelada pelo aluno");
        return solicitacaoRepository.save(s);
    }

    // KPIs
    public long contarTotal(Long instituicaoId) {
        return solicitacaoRepository.countByInstituicaoId(instituicaoId);
    }

    public long contarPorStatus(Long instituicaoId, StatusSolicitacao status) {
        return solicitacaoRepository.countByInstituicaoIdAndStatus(instituicaoId, status);
    }

    private String gerarProtocolo() {
        int ano = Year.now().getValue();
        int numero = ThreadLocalRandom.current().nextInt(100000, 999999);
        String protocolo = String.format("DEL-%d-%06d", ano, numero);
        while (solicitacaoRepository.findByProtocolo(protocolo).isPresent()) {
            numero = ThreadLocalRandom.current().nextInt(100000, 999999);
            protocolo = String.format("DEL-%d-%06d", ano, numero);
        }
        return protocolo;
    }
}
