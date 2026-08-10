package com.delibera.service;

import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Solicitacao;
import com.delibera.repository.AnexoRepository;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AnexoService {

    private static final Logger logger = LoggerFactory.getLogger(AnexoService.class);

    private static final long TAMANHO_MAXIMO = 10 * 1024 * 1024; // 10MB

    // Extensões permitidas (lowercase, com ponto)
    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of(".pdf", ".jpg", ".jpeg", ".png", ".docx");

    // Content-Types permitidos
    private static final Set<String> CONTENT_TYPES_PERMITIDOS = Set.of(
            "application/pdf", "image/jpeg", "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    // Mapeamento extensão → MIME types válidos (Tika pode retornar variantes)
    private static final Map<String, Set<String>> EXTENSAO_PARA_MIMES = Map.of(
            ".pdf", Set.of("application/pdf"),
            ".jpg", Set.of("image/jpeg"),
            ".jpeg", Set.of("image/jpeg"),
            ".png", Set.of("image/png"),
            ".docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml",
                    "application/zip"
            )
    );

    private final Tika tika = new Tika();
    private final AnexoRepository anexoRepository;
    private final Path uploadDir;

    public AnexoService(AnexoRepository anexoRepository,
                        @Value("${delibera.upload.dir:uploads}") String uploadPath) {
        this.anexoRepository = anexoRepository;
        this.uploadDir = Paths.get(uploadPath).toAbsolutePath();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar diretório de uploads: " + uploadPath, e);
        }
    }

    @Transactional
    public Anexo salvar(MultipartFile file, Solicitacao solicitacao) throws IOException {
        validar(file);

        String extensao = extrairExtensao(file.getOriginalFilename());
        String nomeArmazenado = UUID.randomUUID() + extensao;
        Path destino = uploadDir.resolve(nomeArmazenado);

        Files.copy(file.getInputStream(), destino);

        Anexo anexo = new Anexo(
                file.getOriginalFilename(),
                nomeArmazenado,
                file.getContentType(),
                file.getSize(),
                solicitacao
        );

        return anexoRepository.save(anexo);
    }

    public List<Anexo> listarPorSolicitacao(Long solicitacaoId) {
        return anexoRepository.findBySolicitacaoId(solicitacaoId);
    }

    public Path getArquivo(Anexo anexo) {
        return uploadDir.resolve(anexo.getNomeArmazenado());
    }

    @Transactional(readOnly = true)
    public Anexo buscarPorId(Long id) {
        Anexo a = anexoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado: " + id));
        // Force fetch de solicitacao + instituicao para ownership check
        a.getSolicitacao().getInstituicao().getId();
        return a;
    }

    public void validar(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        if (file.getSize() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Arquivo excede o tamanho máximo de 10MB");
        }

        // Camada 1: extensão
        String extensao = extrairExtensao(file.getOriginalFilename()).toLowerCase();
        if (!EXTENSOES_PERMITIDAS.contains(extensao)) {
            logger.warn("[UPLOAD REJEITADO] extensao: {}, content-type declarado: {}, motivo: extensao nao permitida",
                    extensao, file.getContentType());
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use PDF, JPG, PNG ou DOCX.");
        }

        // Camada 2: Content-Type declarado pelo cliente
        String contentType = file.getContentType();
        if (contentType == null || !CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
            logger.warn("[UPLOAD REJEITADO] extensao: {}, content-type declarado: {}, motivo: content-type nao permitido",
                    extensao, contentType);
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use PDF, JPG, PNG ou DOCX.");
        }

        // Camada 3: magic bytes via Tika (buffer limitado, não carrega arquivo inteiro)
        String tikaType;
        try (InputStream is = new BufferedInputStream(file.getInputStream(), 16384)) {
            tikaType = tika.detect(is, file.getOriginalFilename());
        }

        // Verificar que magic bytes batem com a extensão
        Set<String> mimesEsperados = EXTENSAO_PARA_MIMES.get(extensao);
        if (mimesEsperados == null || !mimesEsperados.contains(tikaType)) {
            logger.warn("[UPLOAD REJEITADO] extensao: {}, content-type declarado: {}, magic bytes detectados: {}, motivo: magic bytes divergem da extensao",
                    extensao, contentType, tikaType);
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use PDF, JPG, PNG ou DOCX.");
        }

        // Verificar que Content-Type declarado é consistente com magic bytes
        // Para DOCX, Tika pode retornar variantes (zip, x-tika-ooxml) — aceitar se ambos estão no mapping
        if (!contentType.equals(tikaType)) {
            boolean ambosAceitos = mimesEsperados.contains(contentType) && mimesEsperados.contains(tikaType);
            if (!ambosAceitos) {
                logger.warn("[UPLOAD REJEITADO] extensao: {}, content-type declarado: {}, magic bytes detectados: {}, motivo: content-type diverge dos magic bytes",
                        extensao, contentType, tikaType);
                throw new IllegalArgumentException("Tipo de arquivo não permitido. Use PDF, JPG, PNG ou DOCX.");
            }
        }
    }

    private String extrairExtensao(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
