package com.delibera.service;

import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Solicitacao;
import com.delibera.repository.AnexoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnexoServiceTest {

    @Mock
    private AnexoRepository anexoRepository;

    private AnexoService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AnexoService(anexoRepository, tempDir.toString());
    }

    // ── PDF válido ──

    @Test
    void validar_pdfValido_devePassing() {
        // Magic bytes reais de um PDF: %PDF-1.4
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "atestado.pdf", "application/pdf", pdfBytes);

        assertDoesNotThrow(() -> service.validar(file));
    }

    // ── JPEG válido ──

    @Test
    void validar_jpegValido_devePassing() {
        // Magic bytes de JPEG: FF D8 FF E0 (JFIF) ou FF D8 FF E1 (EXIF)
        byte[] jpegBytes = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 'J', 'F', 'I', 'F', 0x00,
                0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        };
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "foto.jpg", "image/jpeg", jpegBytes);

        assertDoesNotThrow(() -> service.validar(file));
    }

    @Test
    void validar_jpegComExif_devePassing() {
        // JPEG com EXIF header (FF D8 FF E1)
        byte[] jpegExif = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1,
                0x00, 0x10, 'E', 'x', 'i', 'f', 0x00, 0x00,
                0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08
        };
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "foto-exif.jpeg", "image/jpeg", jpegExif);

        assertDoesNotThrow(() -> service.validar(file));
    }

    // ── PNG válido ──

    @Test
    void validar_pngValido_devePassing() {
        // Magic bytes de PNG: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
        };
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "imagem.png", "image/png", pngBytes);

        assertDoesNotThrow(() -> service.validar(file));
    }

    // ── Executável renomeado para .pdf ──

    @Test
    void validar_exeRenomeadoParaPdf_deveRejeitar() {
        // Magic bytes de PE executable: MZ
        byte[] exeBytes = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "malware.pdf", "application/pdf", exeBytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.validar(file));
        assertEquals("Tipo de arquivo não permitido. Use PDF, JPG, PNG ou DOCX.", ex.getMessage());
    }

    // ── PDF com Content-Type errado ──

    @Test
    void validar_pdfComContentTypeErrado_deveRejeitar() {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "documento.pdf", "image/jpeg", pdfBytes);

        assertThrows(IllegalArgumentException.class, () -> service.validar(file));
    }

    // ── Imagem com extensão errada ──

    @Test
    void validar_jpegComExtensaoPng_deveRejeitar() {
        byte[] jpegBytes = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
        };
        // Extensão .png mas conteúdo JPEG
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "foto.png", "image/jpeg", jpegBytes);

        assertThrows(IllegalArgumentException.class, () -> service.validar(file));
    }

    // ── Arquivo vazio ──

    @Test
    void validar_arquivoVazio_deveRejeitar() {
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "vazio.pdf", "application/pdf", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.validar(file));
        assertEquals("Arquivo vazio", ex.getMessage());
    }

    // ── DOCX válido ──

    @Test
    void validar_docxValido_devePassing() {
        // Magic bytes de DOCX (ZIP format): PK\x03\x04
        byte[] docxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00,
                0x08, 0x00, 0x00, 0x00, 0x21, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "trabalho.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);

        assertDoesNotThrow(() -> service.validar(file));
    }

    // ── Extensão faltando ──

    @Test
    void validar_semExtensao_deveRejeitar() {
        byte[] pdfBytes = "%PDF-1.4".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "arquivo", "application/pdf", pdfBytes);

        assertThrows(IllegalArgumentException.class, () -> service.validar(file));
    }

    // ── salvar() ──

    private Solicitacao criarSolicitacao() {
        Solicitacao sol = new Solicitacao();
        sol.setId(1L);
        return sol;
    }

    private MockMultipartFile criarPdfValido() {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj".getBytes();
        return new MockMultipartFile(
                "anexo", "atestado.pdf", "application/pdf", pdfBytes);
    }

    @Test
    void salvar_pdfValido_salvaNoDisco() throws Exception {
        MockMultipartFile file = criarPdfValido();
        Solicitacao sol = criarSolicitacao();

        when(anexoRepository.save(any(Anexo.class))).thenAnswer(i -> {
            Anexo a = i.getArgument(0);
            a.setId(1L);
            return a;
        });

        service.salvar(file, sol);

        // Deve existir exatamente 1 arquivo no diretório temporário
        long count = Files.list(tempDir).count();
        assertEquals(1, count, "Deve haver exatamente 1 arquivo salvo no disco");
    }

    @Test
    void salvar_pdfValido_salvaEntidadeComCamposCorretos() throws Exception {
        MockMultipartFile file = criarPdfValido();
        Solicitacao sol = criarSolicitacao();

        when(anexoRepository.save(any(Anexo.class))).thenAnswer(i -> {
            Anexo a = i.getArgument(0);
            a.setId(1L);
            return a;
        });

        service.salvar(file, sol);

        ArgumentCaptor<Anexo> captor = ArgumentCaptor.forClass(Anexo.class);
        verify(anexoRepository).save(captor.capture());

        Anexo salvo = captor.getValue();
        assertEquals("atestado.pdf", salvo.getNomeOriginal());
        assertEquals("application/pdf", salvo.getContentType());
        assertEquals(file.getSize(), salvo.getTamanho());
        assertSame(sol, salvo.getSolicitacao());
    }

    @Test
    void salvar_pdfValido_nomeArmazenadoTemExtensaoCorreta() throws Exception {
        MockMultipartFile file = criarPdfValido();
        Solicitacao sol = criarSolicitacao();

        when(anexoRepository.save(any(Anexo.class))).thenAnswer(i -> {
            Anexo a = i.getArgument(0);
            a.setId(1L);
            return a;
        });

        Anexo resultado = service.salvar(file, sol);

        assertTrue(resultado.getNomeArmazenado().endsWith(".pdf"),
                "Nome armazenado deve terminar com .pdf, mas foi: " + resultado.getNomeArmazenado());
    }

    @Test
    void salvar_arquivoInvalido_naoSalvaNoDisco() {
        byte[] exeBytes = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "anexo", "malware.exe", "application/x-msdownload", exeBytes);
        Solicitacao sol = criarSolicitacao();

        assertThrows(IllegalArgumentException.class, () -> service.salvar(file, sol));

        // Nenhum arquivo deve ter sido criado no disco
        try {
            long count = Files.list(tempDir).count();
            assertEquals(0, count, "Nenhum arquivo deve ser salvo no disco quando a validacao falha");
        } catch (Exception e) {
            fail("Erro ao listar diretorio temporario: " + e.getMessage());
        }

        verify(anexoRepository, never()).save(any());
    }
}
