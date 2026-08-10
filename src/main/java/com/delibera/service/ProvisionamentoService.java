package com.delibera.service;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.Role;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cadastro de instituições e do primeiro coordenador — o que faltava para colocar um
 * cliente no ar sem abrir o banco.
 *
 * <p>A senha inicial é gerada aqui e devolvida uma única vez, na tela. Pedir para quem
 * provisiona inventar a senha do cliente leva a senhas fracas e repetidas entre contas.
 */
@Service
public class ProvisionamentoService {

    private static final Logger logger = LoggerFactory.getLogger(ProvisionamentoService.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String MAIUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final String ESPECIAIS = "!@#$%&*?";

    private final InstituicaoRepository instituicaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public ProvisionamentoService(InstituicaoRepository instituicaoRepository,
                                   UsuarioRepository usuarioRepository,
                                   UsuarioService usuarioService) {
        this.instituicaoRepository = instituicaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    public List<Instituicao> listarInstituicoes() {
        return instituicaoRepository.findAll();
    }

    public List<Usuario> listarUsuarios(Long instituicaoId) {
        return usuarioRepository.findByInstituicaoIdAndAtivoTrue(instituicaoId);
    }

    /**
     * Cria a instituição com piloto de 30 dias já contando.
     *
     * @return a instituição criada.
     */
    @Transactional
    public Instituicao criarInstituicao(String nome, String sigla, String dominio) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome da instituição");
        }
        Instituicao inst = new Instituicao(nome.trim(), sigla != null ? sigla.trim().toUpperCase() : null);
        inst.setDominio(dominio != null && !dominio.isBlank() ? dominio.trim().toLowerCase() : null);
        inst = instituicaoRepository.save(inst);
        logger.info("[PROVISIONAMENTO] Instituição criada: {} (id={}), piloto até {}",
                inst.getNome(), inst.getId(), inst.getPilotoExpiraEm());
        return inst;
    }

    /**
     * Cria um usuário da instituição e devolve a senha gerada.
     *
     * <p>Bloqueia a criação de ROOT e GESTAO por aqui: contas cross-tenant não podem
     * nascer de um formulário web. ROOT só existe via {@code RootBootstrap} com
     * variáveis de ambiente.
     */
    @Transactional
    public String criarUsuario(String nome, String email, Role role, Long instituicaoId) {
        if (role == null || role == Role.ROOT || role == Role.GESTAO) {
            throw new IllegalArgumentException(
                    "Só é possível criar COORDENADOR ou ADMIN_INSTITUICAO por aqui");
        }
        Instituicao inst = instituicaoRepository.findById(instituicaoId)
                .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

        String senha = gerarSenhaInicial();
        usuarioService.criar(nome.trim(), email.trim().toLowerCase(), senha, role, inst);
        logger.info("[PROVISIONAMENTO] Usuário {} criado para instituição {}", role, inst.getNome());
        return senha;
    }

    /** Liga/desliga a instituição — desligada, ninguém dela consegue entrar. */
    @Transactional
    public Instituicao alternarAtivo(Long instituicaoId) {
        Instituicao inst = instituicaoRepository.findById(instituicaoId)
                .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));
        inst.setAtivo(!inst.isAtivo());
        logger.info("[PROVISIONAMENTO] Instituição {} agora está {}",
                inst.getNome(), inst.isAtivo() ? "ativa" : "inativa");
        return instituicaoRepository.save(inst);
    }

    /** Estende o acesso manualmente — o caminho para converter em cliente pagante. */
    @Transactional
    public Instituicao estenderAcesso(Long instituicaoId, int dias) {
        if (dias < 1 || dias > 3650) {
            throw new IllegalArgumentException("Informe entre 1 e 3650 dias");
        }
        Instituicao inst = instituicaoRepository.findById(instituicaoId)
                .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

        // A partir de hoje quando já expirou; a partir do fim quando ainda vale —
        // senão estender um piloto ativo encurtaria o prazo.
        LocalDateTime base = (inst.getPilotoExpiraEm() != null
                && inst.getPilotoExpiraEm().isAfter(LocalDateTime.now()))
                ? inst.getPilotoExpiraEm()
                : LocalDateTime.now();

        inst.setPilotoExpiraEm(base.plusDays(dias));
        // Zera o voucher: o acesso agora vem do prazo estendido, e manter a marca
        // impediria o cliente de usar o voucher numa renovação futura.
        inst.setVoucherAtivadoEm(null);
        inst.setEmailPilotoEnviado(false);
        inst.setEmailVoucherEnviado(false);

        logger.info("[PROVISIONAMENTO] Acesso de {} estendido por {} dias, até {}",
                inst.getNome(), dias, inst.getPilotoExpiraEm());
        return instituicaoRepository.save(inst);
    }

    /** 16 caracteres, sem glifos ambíguos (O/0, l/1) para ditar por telefone. */
    private String gerarSenhaInicial() {
        StringBuilder sb = new StringBuilder();
        sb.append(sortear(MAIUSCULAS)).append(sortear(MINUSCULAS))
          .append(sortear(DIGITOS)).append(sortear(ESPECIAIS));
        String todos = MAIUSCULAS + MINUSCULAS + DIGITOS + ESPECIAIS;
        for (int i = 4; i < 16; i++) {
            sb.append(sortear(todos));
        }
        // Embaralha para os quatro obrigatórios não ficarem sempre no começo.
        List<Character> chars = new java.util.ArrayList<>();
        for (char c : sb.toString().toCharArray()) {
            chars.add(c);
        }
        java.util.Collections.shuffle(chars, SECURE_RANDOM);
        StringBuilder saida = new StringBuilder(chars.size());
        chars.forEach(saida::append);
        return saida.toString();
    }

    private char sortear(String alfabeto) {
        return alfabeto.charAt(SECURE_RANDOM.nextInt(alfabeto.length()));
    }
}
