package com.delibera.controller;

import com.delibera.config.CotaPublica;
import com.delibera.config.GlobalRateLimiter;
import com.delibera.model.dto.NovaSolicitacaoDTO;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.service.AnexoService;
import com.delibera.service.EmailService;
import com.delibera.service.SolicitacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitacaoPublicControllerTest {

    @Mock
    private SolicitacaoService solicitacaoService;
    @Mock
    private InstituicaoRepository instituicaoRepository;
    @Mock
    private AnexoService anexoService;
    @Mock
    private EmailService emailService;
    @Mock
    private GlobalRateLimiter rateLimiter;
    @Mock
    private CotaPublica cotaPublica;

    private SolicitacaoPublicController controller;

    @BeforeEach
    void setUp() {
        controller = new SolicitacaoPublicController(solicitacaoService, instituicaoRepository,
                anexoService, emailService, rateLimiter, cotaPublica);
    }

    @Test
    void criar_segundaChamadaSemDataEAnexo_deveReexibirFormulario() throws Exception {
        NovaSolicitacaoDTO dto = criarDto(TipoSolicitacao.SEGUNDA_CHAMADA);
        BindingResult errors = new BeanPropertyBindingResult(dto, "novaSolicitacaoDTO");

        String view = controller.criar(dto, errors, arquivoVazio(), new MockHttpServletRequest(), new ExtendedModelMap(),
                new RedirectAttributesModelMap());

        assertEquals("pages/aluno/nova-solicitacao", view);
        assertTrue(errors.hasFieldErrors("dataOcorrencia"));
        assertTrue(errors.hasGlobalErrors());
        verify(cotaPublica, never()).permitir(any(), any(), anyInt());
        verify(solicitacaoService, never()).criar(any(), any());
    }

    @Test
    void criar_contestacaoSemNotas_deveReexibirFormulario() throws Exception {
        NovaSolicitacaoDTO dto = criarDto(TipoSolicitacao.CONTESTACAO_NOTA);
        BindingResult errors = new BeanPropertyBindingResult(dto, "novaSolicitacaoDTO");

        String view = controller.criar(dto, errors, arquivoVazio(), new MockHttpServletRequest(), new ExtendedModelMap(),
                new RedirectAttributesModelMap());

        assertEquals("pages/aluno/nova-solicitacao", view);
        assertTrue(errors.hasFieldErrors("notaAtual"));
        assertTrue(errors.hasFieldErrors("notaEsperada"));
        verify(cotaPublica, never()).permitir(any(), any(), anyInt());
        verify(solicitacaoService, never()).criar(any(), any());
    }

    @Test
    void criar_segundaChamadaValida_deveSalvarEEnviarEmail() throws Exception {
        NovaSolicitacaoDTO dto = criarDto(TipoSolicitacao.SEGUNDA_CHAMADA);
        dto.setDataOcorrencia(LocalDate.of(2026, 8, 1));
        MockMultipartFile anexo = new MockMultipartFile(
                "anexo", "atestado.pdf", "application/pdf", "%PDF-1.4".getBytes());
        BindingResult errors = new BeanPropertyBindingResult(dto, "novaSolicitacaoDTO");
        Instituicao instituicao = new Instituicao("Faculdade Teste", "FT");
        Solicitacao criada = new Solicitacao();
        criada.setProtocolo("DEL-2026-TESTE123");

        when(instituicaoRepository.findById(1L)).thenReturn(Optional.of(instituicao));
        when(solicitacaoService.criar(any(Solicitacao.class), same(instituicao))).thenReturn(criada);
        when(cotaPublica.permitir(any(), any(), anyInt())).thenReturn(true);

        String view = controller.criar(dto, errors, anexo, new MockHttpServletRequest(), new ExtendedModelMap(),
                new RedirectAttributesModelMap());

        assertEquals("redirect:/status/DEL-2026-TESTE123", view);
        assertFalse(errors.hasErrors());
        verify(anexoService).validar(anexo);
        verify(anexoService).salvar(anexo, criada);
        verify(emailService).notificarCriacao(criada);
    }

    private NovaSolicitacaoDTO criarDto(TipoSolicitacao tipo) {
        NovaSolicitacaoDTO dto = new NovaSolicitacaoDTO();
        dto.setInstituicaoId(1L);
        dto.setAlunoNome("Maria Silva");
        dto.setAlunoEmail("maria@teste.edu.br");
        dto.setDisciplina("Banco de Dados");
        dto.setJustificativa("Justificativa de teste");
        dto.setAceiteTermos(true);
        dto.setTipo(tipo);
        return dto;
    }

    private MockMultipartFile arquivoVazio() {
        return new MockMultipartFile("anexo", new byte[0]);
    }
}
