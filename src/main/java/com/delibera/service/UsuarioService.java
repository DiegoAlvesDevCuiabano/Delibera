package com.delibera.service;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.Role;
import com.delibera.repository.UsuarioRepository;
import com.delibera.util.SenhaValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));
        if (u.getInstituicao() != null) {
            u.getInstituicao().getNome();
        }
        return u;
    }

    @Transactional
    public Usuario criar(String nome, String email, String senhaPlana, Role role, Instituicao instituicao) {
        List<String> errosSenha = SenhaValidator.validar(senhaPlana);
        if (!errosSenha.isEmpty()) {
            throw new IllegalArgumentException("Senha fraca: " + String.join(", ", errosSenha));
        }

        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Já existe um usuário com este email");
        }

        Usuario u = new Usuario(nome, email, passwordEncoder.encode(senhaPlana), role, instituicao);
        return usuarioRepository.save(u);
    }

    @Transactional
    public void alterarSenha(Long id, String senhaAtual, String senhaNova) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (!passwordEncoder.matches(senhaAtual, u.getSenha())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }

        List<String> errosSenha = SenhaValidator.validar(senhaNova);
        if (!errosSenha.isEmpty()) {
            throw new IllegalArgumentException("Senha fraca: " + String.join(", ", errosSenha));
        }

        u.setSenha(passwordEncoder.encode(senhaNova));
        usuarioRepository.save(u);
    }
}
