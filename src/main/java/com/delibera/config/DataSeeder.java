package com.delibera.config;

import com.delibera.model.entity.*;
import com.delibera.model.enums.Role;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private final InstituicaoRepository instituicaoRepo;
    private final CursoRepository cursoRepo;
    private final UsuarioRepository usuarioRepo;
    private final SolicitacaoRepository solicitacaoRepo;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(InstituicaoRepository instituicaoRepo, CursoRepository cursoRepo,
                      UsuarioRepository usuarioRepo, SolicitacaoRepository solicitacaoRepo,
                      PasswordEncoder passwordEncoder) {
        this.instituicaoRepo = instituicaoRepo;
        this.cursoRepo = cursoRepo;
        this.usuarioRepo = usuarioRepo;
        this.solicitacaoRepo = solicitacaoRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepo.count() > 0) return;

        // Instituição
        Instituicao inst = instituicaoRepo.save(new Instituicao("Faculdade Exemplo", "FEXEMPLO"));

        // Cursos
        Curso engSoft = cursoRepo.save(new Curso("Engenharia de Software", inst));
        Curso adm = cursoRepo.save(new Curso("Administração", inst));
        Curso direito = cursoRepo.save(new Curso("Direito", inst));

        String senhaHash = passwordEncoder.encode("demo123");

        // Root (owner do sistema)
        Usuario root = new Usuario("Diego Alves", "admin@delibera.com.br", senhaHash, Role.ROOT, inst);
        root = usuarioRepo.save(root);

        // Coordenador
        Usuario coord = new Usuario("Prof. Mariana Costa", "coordenacao@exemplo.edu.br", senhaHash, Role.COORDENADOR, inst);
        coord = usuarioRepo.save(coord);

        // Alunos placeholder (TODO: remover quando Solicitacao usar campos de texto em vez de FK)
        Usuario aluno = new Usuario("Lucas Ferreira Santos", "aluno@exemplo.edu.br", senhaHash, Role.COORDENADOR, inst);
        aluno.setMatricula("2024001234");
        aluno.setCurso(engSoft);
        aluno = usuarioRepo.save(aluno);

        Usuario aluno2 = new Usuario("Ana Paula Oliveira", "ana@exemplo.edu.br", senhaHash, Role.COORDENADOR, inst);
        aluno2.setMatricula("2024005678");
        aluno2.setCurso(adm);
        aluno2 = usuarioRepo.save(aluno2);

        // Solicitação 1 — Em Análise
        Solicitacao s1 = new Solicitacao();
        s1.setProtocolo("DEL-2026-000001");
        s1.setTipo(TipoSolicitacao.ABONO_FALTA);
        s1.setStatus(StatusSolicitacao.EM_ANALISE);
        s1.setDisciplina("Estrutura de Dados");
        s1.setProfessor("Prof. Ricardo Almeida");
        s1.setDataOcorrencia(LocalDate.of(2026, 3, 28));
        s1.setJustificativa("Precisei comparecer a consulta médica de urgência. Atestado médico em anexo.");
        s1.setAluno(aluno);
        s1.setInstituicao(inst);
        s1.setCriadoEm(LocalDateTime.of(2026, 3, 29, 10, 15));
        s1.setAtualizadoEm(s1.getCriadoEm());
        s1.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno", aluno);
        solicitacaoRepo.save(s1);

        // Solicitação 2 — Deferida
        Solicitacao s2 = new Solicitacao();
        s2.setProtocolo("DEL-2026-000002");
        s2.setTipo(TipoSolicitacao.SEGUNDA_CHAMADA);
        s2.setStatus(StatusSolicitacao.RESOLVIDO);
        s2.setDisciplina("Banco de Dados II");
        s2.setProfessor("Profa. Cláudia Mendes");
        s2.setDataOcorrencia(LocalDate.of(2026, 3, 15));
        s2.setJustificativa("Participação em evento acadêmico obrigatório (SBIE 2026). Comprovante de inscrição em anexo.");
        s2.setParecer("Deferido. Aluno apresentou comprovante válido. Segunda chamada agendada para 05/04/2026.");
        s2.setAluno(aluno);
        s2.setInstituicao(inst);
        s2.setCriadoEm(LocalDateTime.of(2026, 3, 16, 8, 30));
        s2.setAtualizadoEm(LocalDateTime.of(2026, 3, 20, 14, 45));
        s2.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno", aluno);
        s2.getHistorico().get(0).setCriadoEm(LocalDateTime.of(2026, 3, 16, 8, 30));
        s2.adicionarHistorico(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.RESOLVIDO,
                "Deferido. Segunda chamada agendada para 05/04/2026.", coord);
        s2.getHistorico().get(1).setCriadoEm(LocalDateTime.of(2026, 3, 20, 14, 45));
        solicitacaoRepo.save(s2);

        // Solicitação 3 — Negada
        Solicitacao s3 = new Solicitacao();
        s3.setProtocolo("DEL-2026-000003");
        s3.setTipo(TipoSolicitacao.CONTESTACAO_NOTA);
        s3.setStatus(StatusSolicitacao.NEGADO);
        s3.setDisciplina("Cálculo I");
        s3.setProfessor("Prof. André Souza");
        s3.setDataOcorrencia(LocalDate.of(2026, 3, 10));
        s3.setJustificativa("A nota da P2 está diferente do gabarito divulgado. Questão 3 deveria valer nota parcial.");
        s3.setParecer("Indeferido. Após reanálise da prova pelo professor, a correção está de acordo com o gabarito e critérios estabelecidos.");
        s3.setAluno(aluno);
        s3.setInstituicao(inst);
        s3.setCriadoEm(LocalDateTime.of(2026, 3, 11, 16, 0));
        s3.setAtualizadoEm(LocalDateTime.of(2026, 3, 18, 9, 20));
        s3.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno", aluno);
        s3.getHistorico().get(0).setCriadoEm(LocalDateTime.of(2026, 3, 11, 16, 0));
        s3.adicionarHistorico(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.AGUARDANDO_PROFESSOR,
                "Encaminhada ao professor para reanálise", coord);
        s3.getHistorico().get(1).setCriadoEm(LocalDateTime.of(2026, 3, 13, 10, 0));
        s3.adicionarHistorico(StatusSolicitacao.AGUARDANDO_PROFESSOR, StatusSolicitacao.NEGADO,
                "Indeferido após reanálise pelo professor.", coord);
        s3.getHistorico().get(2).setCriadoEm(LocalDateTime.of(2026, 3, 18, 9, 20));
        solicitacaoRepo.save(s3);

        // Solicitação 4 — Aluno 2, Em Análise
        Solicitacao s4 = new Solicitacao();
        s4.setProtocolo("DEL-2026-000004");
        s4.setTipo(TipoSolicitacao.ABONO_FALTA);
        s4.setStatus(StatusSolicitacao.EM_ANALISE);
        s4.setDisciplina("Teoria Geral da Administração");
        s4.setProfessor("Prof. Roberto Lima");
        s4.setDataOcorrencia(LocalDate.of(2026, 4, 2));
        s4.setJustificativa("Internação hospitalar de familiar. Declaração do hospital em anexo.");
        s4.setAluno(aluno2);
        s4.setInstituicao(inst);
        s4.setCriadoEm(LocalDateTime.of(2026, 4, 3, 9, 0));
        s4.setAtualizadoEm(s4.getCriadoEm());
        s4.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno", aluno2);
        solicitacaoRepo.save(s4);

        // Solicitação 5 — Aluno 2, Aguardando Professor
        Solicitacao s5 = new Solicitacao();
        s5.setProtocolo("DEL-2026-000005");
        s5.setTipo(TipoSolicitacao.AJUSTE_PRESENCA);
        s5.setStatus(StatusSolicitacao.AGUARDANDO_PROFESSOR);
        s5.setDisciplina("Contabilidade Empresarial");
        s5.setProfessor("Profa. Fernanda Bastos");
        s5.setDataOcorrencia(LocalDate.of(2026, 3, 25));
        s5.setJustificativa("Estava presente na aula mas não consegui assinar a lista de presença pois cheguei atrasada 5 minutos.");
        s5.setAluno(aluno2);
        s5.setInstituicao(inst);
        s5.setCriadoEm(LocalDateTime.of(2026, 3, 26, 14, 30));
        s5.setAtualizadoEm(LocalDateTime.of(2026, 3, 28, 11, 0));
        s5.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno", aluno2);
        s5.getHistorico().get(0).setCriadoEm(LocalDateTime.of(2026, 3, 26, 14, 30));
        s5.adicionarHistorico(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.AGUARDANDO_PROFESSOR,
                "Encaminhada à professora para confirmar presença", coord);
        s5.getHistorico().get(1).setCriadoEm(LocalDateTime.of(2026, 3, 28, 11, 0));
        solicitacaoRepo.save(s5);
    }
}
