package com.delibera.service;

import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Solicitacao;
import com.delibera.repository.AnexoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnexoService {

    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );
    private static final long TAMANHO_MAXIMO = 10 * 1024 * 1024; // 10MB

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

    public Anexo buscarPorId(Long id) {
        return anexoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado: " + id));
    }

    private void validar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        if (file.getSize() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Arquivo excede o tamanho máximo de 10MB");
        }
        if (!TIPOS_PERMITIDOS.contains(file.getContentType())) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use PDF, JPG ou PNG.");
        }
    }

    private String extrairExtensao(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
