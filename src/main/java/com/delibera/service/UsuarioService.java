package com.delibera.service;

import com.delibera.model.entity.Usuario;
import com.delibera.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));
        // Force fetch da instituição (evitar LazyInitializationException)
        if (u.getInstituicao() != null) {
            u.getInstituicao().getNome();
        }
        return u;
    }
}
