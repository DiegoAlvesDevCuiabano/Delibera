package com.delibera.service;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.Role;
import com.delibera.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService service;

    private Instituicao inst;
    private Usuario usuario;

    private static final String SENHA_VALIDA = "Teste@123";

    @BeforeEach
    void setUp() {
        inst = new Instituicao("Faculdade Teste", "FT");
        inst.setId(1L);

        usuario = new Usuario("Coord Teste", "coord@teste.edu.br", "hash_bcrypt", Role.COORDENADOR, inst);
        usuario.setId(1L);
    }

    // --- buscarPorEmail ---

    @Test
    void buscarPorEmail_deveRetornarUsuarioQuandoEncontrado() {
        when(usuarioRepository.findByEmail("coord@teste.edu.br")).thenReturn(Optional.of(usuario));

        Usuario resultado = service.buscarPorEmail("coord@teste.edu.br");

        assertNotNull(resultado);
        assertEquals("Coord Teste", resultado.getNome());
        assertEquals("coord@teste.edu.br", resultado.getEmail());
    }

    @Test
    void buscarPorEmail_deveLancarExcecaoQuandoNaoEncontrado() {
        when(usuarioRepository.findByEmail("naoexiste@teste.edu.br")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buscarPorEmail("naoexiste@teste.edu.br"));
        assertTrue(ex.getMessage().contains("naoexiste@teste.edu.br"));
    }

    @Test
    void buscarPorEmail_deveForcaLazyLoadDeInstituicao() {
        Instituicao instSpy = spy(inst);
        usuario = new Usuario("Coord Teste", "coord@teste.edu.br", "hash_bcrypt", Role.COORDENADOR, instSpy);
        usuario.setId(1L);
        when(usuarioRepository.findByEmail("coord@teste.edu.br")).thenReturn(Optional.of(usuario));

        service.buscarPorEmail("coord@teste.edu.br");

        verify(instSpy).getNome();
    }

    // --- criar ---

    @Test
    void criar_deveEncodarSenhaESalvar() {
        when(usuarioRepository.findByEmail("novo@teste.edu.br")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(SENHA_VALIDA)).thenReturn("encoded_hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        Usuario resultado = service.criar("Novo Usuario", "novo@teste.edu.br", SENHA_VALIDA, Role.COORDENADOR, inst);

        assertNotNull(resultado);
        assertEquals("Novo Usuario", resultado.getNome());
        assertEquals("novo@teste.edu.br", resultado.getEmail());
        assertEquals("encoded_hash", resultado.getSenha());
        assertEquals(Role.COORDENADOR, resultado.getRole());
        assertEquals(inst, resultado.getInstituicao());
        verify(passwordEncoder).encode(SENHA_VALIDA);
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void criar_deveLancarExcecaoParaSenhaFraca() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.criar("Novo", "novo@teste.edu.br", "123", Role.COORDENADOR, inst));
        assertTrue(ex.getMessage().contains("Senha fraca"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criar_deveLancarExcecaoParaEmailDuplicado() {
        when(usuarioRepository.findByEmail("coord@teste.edu.br")).thenReturn(Optional.of(usuario));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.criar("Outro", "coord@teste.edu.br", SENHA_VALIDA, Role.COORDENADOR, inst));
        assertTrue(ex.getMessage().contains("Já existe"));
        verify(usuarioRepository, never()).save(any());
    }

    // --- alterarSenha ---

    @Test
    void alterarSenha_deveAlterarQuandoSenhaAtualCorreta() {
        String novaSenha = "NovaSenha@1";
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaAtual@1", "hash_bcrypt")).thenReturn(true);
        when(passwordEncoder.encode(novaSenha)).thenReturn("novo_hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.alterarSenha(1L, "SenhaAtual@1", novaSenha));

        assertEquals("novo_hash", usuario.getSenha());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void alterarSenha_deveLancarExcecaoQuandoSenhaAtualIncorreta() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash_bcrypt")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.alterarSenha(1L, "errada", "NovaSenha@1"));
        assertTrue(ex.getMessage().contains("Senha atual incorreta"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void alterarSenha_deveLancarExcecaoParaSenhaNovaFraca() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaAtual@1", "hash_bcrypt")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.alterarSenha(1L, "SenhaAtual@1", "fraca"));
        assertTrue(ex.getMessage().contains("Senha fraca"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void alterarSenha_deveLancarExcecaoQuandoUsuarioNaoEncontrado() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.alterarSenha(99L, "qualquer", "NovaSenha@1"));
        assertTrue(ex.getMessage().contains("não encontrado"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
