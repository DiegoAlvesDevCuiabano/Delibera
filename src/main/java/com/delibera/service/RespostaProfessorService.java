package com.delibera.service;

import com.delibera.model.entity.ParecerProfessor;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.RespostaProfessor;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.repository.ParecerProfessorRepository;
import com.delibera.repository.SolicitacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RespostaProfessorService {

    private final ParecerProfessorRepository parecerRepository;
    private final SolicitacaoRepository solicitacaoRepository;

    public RespostaProfessorService(ParecerProfessorRepository parecerRepository,
                                    SolicitacaoRepository solicitacaoRepository) {
        this.parecerRepository = parecerRepository;
        this.solicitacaoRepository = solicitacaoRepository;
    }

    @Transactional
    public Solicitacao responder(Long parecerId, String codigo, RespostaProfessor resposta, String comentario) {
        ParecerProfessor parecer = parecerRepository.findParaResposta(parecerId)
                .orElseThrow(CodigoIndisponivelException::new);
        if (codigo == null || !parecer.getCodigoAcesso().equals(codigo) || !parecer.isDisponivel()) {
            throw new CodigoIndisponivelException();
        }

        Solicitacao solicitacao = solicitacaoRepository.findByIdForUpdate(parecer.getSolicitacao().getId())
                .orElseThrow(CodigoIndisponivelException::new);
        if (solicitacao.getStatus() != StatusSolicitacao.AGUARDANDO_PROFESSOR) {
            throw new SolicitacaoConcluidaException();
        }

        String texto = comentario == null ? null : comentario.trim();
        parecer.setResposta(resposta);
        parecer.setParecerProfessor(texto);
        parecer.setRespondidoEm(LocalDateTime.now());
        parecer.setUsado(true);
        parecerRepository.save(parecer);

        solicitacao.setStatus(StatusSolicitacao.EM_ANALISE);
        String observacao = "Resposta do professor: " + resposta.getDescricao();
        if (texto != null && !texto.isBlank()) {
            observacao += " — " + texto;
        }
        solicitacao.adicionarHistorico(StatusSolicitacao.AGUARDANDO_PROFESSOR,
                StatusSolicitacao.EM_ANALISE, observacao);
        solicitacaoRepository.save(solicitacao);
        return solicitacao;
    }

    public static class CodigoIndisponivelException extends RuntimeException {
    }

    public static class SolicitacaoConcluidaException extends RuntimeException {
    }
}
