package com.delibera.security;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.enums.Role;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.UsuarioRepository;
import com.delibera.service.ProvisionamentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class GestaoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProvisionamentoService provisionamento;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    // ── Autorização ──────────────────────────────────────────────

    @Test
    void gestao_semLogin_exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/gestao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void gestao_comCoordenador_deveSerProibido() throws Exception {
        mockMvc.perform(get("/gestao")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "contato@appdelibera.com.br", roles = "ROOT")
    void gestao_comRoot_deveRenderizar() throws Exception {
        mockMvc.perform(get("/gestao")).andExpect(status().isOk());
    }

    // ── Provisionamento ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "contato@appdelibera.com.br", roles = "ROOT")
    void criarInstituicao_iniciaPilotoDe30Dias() throws Exception {
        mockMvc.perform(post("/gestao/instituicoes")
                        .param("nome", "Faculdade Provisionada")
                        .param("sigla", "fprov")
                        .param("dominio", "Prov.EDU.br")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Instituicao criada = instituicaoRepository.findAll().stream()
                .filter(i -> "Faculdade Provisionada".equals(i.getNome()))
                .findFirst().orElseThrow();

        assertThat(criada.getSigla()).isEqualTo("FPROV");
        assertThat(criada.getDominio()).isEqualTo("prov.edu.br");
        assertThat(criada.isAcessoAtivo()).isTrue();
        assertThat(criada.getPilotoExpiraEm()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    @WithMockUser(username = "contato@appdelibera.com.br", roles = "ROOT")
    void criarUsuario_geraSenhaForteEVinculaAInstituicao() throws Exception {
        Instituicao inst = provisionamento.criarInstituicao("Faculdade Usuarios", "FU", null);

        mockMvc.perform(post("/gestao/instituicoes/" + inst.getId() + "/usuarios")
                        .param("nome", "Coordenadora Teste")
                        .param("email", "Coord.Novo@FU.edu.br")
                        .param("role", "COORDENADOR")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        var criado = usuarioRepository.findByEmail("coord.novo@fu.edu.br").orElseThrow();
        assertThat(criado.getRole()).isEqualTo(Role.COORDENADOR);
        assertThat(criado.getInstituicao().getId()).isEqualTo(inst.getId());
        assertThat(criado.getSenha()).startsWith("$2");
    }

    @Test
    void criarUsuario_comRoleCrossTenant_deveSerRecusado() {
        Instituicao inst = provisionamento.criarInstituicao("Faculdade Bloqueio", "FB", null);
        assertThatThrownBy(() ->
                provisionamento.criarUsuario("Invasor", "root@x.com", Role.ROOT, inst.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                provisionamento.criarUsuario("Invasor", "gestao@x.com", Role.GESTAO, inst.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Ciclo comercial ──────────────────────────────────────────

    @Test
    void estenderAcesso_somaAoPrazoQuandoAindaVale() {
        Instituicao inst = provisionamento.criarInstituicao("Faculdade Extensao", "FEX", null);
        LocalDateTime antes = inst.getPilotoExpiraEm();

        Instituicao depois = provisionamento.estenderAcesso(inst.getId(), 365);

        assertThat(depois.getPilotoExpiraEm())
                .as("estender um piloto ativo nao pode encurtar o prazo")
                .isAfter(antes.plusDays(364));
    }

    @Test
    void estenderAcesso_contaDeHojeQuandoJaExpirou() {
        Instituicao inst = provisionamento.criarInstituicao("Faculdade Expirada", "FEXP", null);
        inst.setPilotoExpiraEm(LocalDateTime.now().minusDays(100));
        instituicaoRepository.save(inst);

        Instituicao depois = provisionamento.estenderAcesso(inst.getId(), 30);

        assertThat(depois.isAcessoAtivo()).isTrue();
        assertThat(depois.getPilotoExpiraEm()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    void estenderAcesso_comDiasInvalidos_deveFalhar() {
        Instituicao inst = provisionamento.criarInstituicao("Faculdade Invalida", "FI", null);
        assertThatThrownBy(() -> provisionamento.estenderAcesso(inst.getId(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provisionamento.estenderAcesso(inst.getId(), 99999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alternarAtivo_desligaEReligaAInstituicao() {
        Instituicao inst = provisionamento.criarInstituicao("Faculdade Toggle", "FT", null);
        assertThat(provisionamento.alternarAtivo(inst.getId()).isAtivo()).isFalse();
        assertThat(provisionamento.alternarAtivo(inst.getId()).isAtivo()).isTrue();
    }
}
