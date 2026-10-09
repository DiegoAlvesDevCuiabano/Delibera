package com.delibera.service;

import com.delibera.model.entity.ParecerProfessor;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.repository.ParecerProfessorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class EncaminhamentoProfessorService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final SolicitacaoService solicitacaoService;
    private final ParecerProfessorRepository parecerProfessorRepository;

    public EncaminhamentoProfessorService(SolicitacaoService solicitacaoService,
                                          ParecerProfessorRepository parecerProfessorRepository) {
        this.solicitacaoService = solicitacaoService;
        this.parecerProfessorRepository = parecerProfessorRepository;
    }

    @Transactional
    public Encaminhamento encaminhar(Long id, String parecer, String emailProfessor, Usuario coordenador) {
        String email = emailProfessor.trim().toLowerCase();
        Solicitacao solicitacao = solicitacaoService.encaminhar(id, parecer, coordenador);

        ParecerProfessor registro = new ParecerProfessor();
        registro.setSolicitacao(solicitacao);
        registro.setEmailProfessor(email);
        registro.setCodigoAcesso(gerarCodigoProfessor());
        registro.setComentarioCoordenador(parecer);
        parecerProfessorRepository.save(registro);

        return new Encaminhamento(solicitacao, email, registro.getCodigoAcesso());
    }

    private String gerarCodigoProfessor() {
        StringBuilder codigo = new StringBuilder("PRF-");
        for (int i = 0; i < 12; i++) {
            codigo.append(BASE36_CHARS.charAt(SECURE_RANDOM.nextInt(BASE36_CHARS.length())));
        }
        return codigo.toString();
    }

    public record Encaminhamento(Solicitacao solicitacao, String emailProfessor, String codigoAcesso) {
    }
}
