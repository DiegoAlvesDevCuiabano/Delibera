package com.delibera.config;

import com.delibera.model.entity.*;
import com.delibera.model.enums.Role;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@Profile({"dev", "h2"})
public class DataSeeder implements CommandLineRunner {

    private final InstituicaoRepository instituicaoRepo;
    private final CursoRepository cursoRepo;
    private final UsuarioRepository usuarioRepo;
    private final SolicitacaoRepository solicitacaoRepo;
    private final ErroLogRepository erroLogRepo;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(InstituicaoRepository instituicaoRepo, CursoRepository cursoRepo,
                      UsuarioRepository usuarioRepo, SolicitacaoRepository solicitacaoRepo,
                      ErroLogRepository erroLogRepo, PasswordEncoder passwordEncoder) {
        this.instituicaoRepo = instituicaoRepo;
        this.cursoRepo = cursoRepo;
        this.usuarioRepo = usuarioRepo;
        this.solicitacaoRepo = solicitacaoRepo;
        this.erroLogRepo = erroLogRepo;
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

        // Senha forte para dev/demo (mínimo 8 chars, maiúscula, minúscula, número, especial)
        String senhaHash = passwordEncoder.encode("Demo@2026");

        // Root (owner do sistema — sem instituição)
        Usuario root = new Usuario("Diego Alves", "contato@appdelibera.com.br", senhaHash, Role.ROOT);
        root = usuarioRepo.save(root);

        // Admin da instituição
        Usuario admin = new Usuario("Diretor Acadêmico", "admin@exemplo.edu.br", senhaHash, Role.ADMIN_INSTITUICAO, inst);
        admin = usuarioRepo.save(admin);

        // Coordenador
        Usuario coord = new Usuario("Prof. Mariana Costa", "coordenacao@exemplo.edu.br", senhaHash, Role.COORDENADOR, inst);
        coord = usuarioRepo.save(coord);

        // ── Solicitações (aluno = campos de texto, sem FK) ──

        // S1 — Em Análise (Abono de falta)
        Solicitacao s1 = new Solicitacao();
        s1.setProtocolo("DEL-2026-000001");
        s1.setTipo(TipoSolicitacao.ABONO_FALTA);
        s1.setStatus(StatusSolicitacao.EM_ANALISE);
        s1.setAlunoNome("Lucas Ferreira Santos");
        s1.setAlunoEmail("lucas@exemplo.edu.br");
        s1.setAlunoCurso("Engenharia de Software");
        s1.setAlunoTurma("3º A");
        s1.setDisciplina("Estrutura de Dados");
        s1.setProfessor("Prof. Ricardo Almeida");
        s1.setDataOcorrencia(LocalDate.of(2026, 3, 28));
        s1.setJustificativa("Precisei comparecer a consulta médica de urgência. Atestado médico em anexo.");
        s1.setInstituicao(inst);
        s1.setCriadoEm(LocalDateTime.of(2026, 3, 29, 10, 15));
        s1.setAtualizadoEm(s1.getCriadoEm());
        s1.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno");
        solicitacaoRepo.save(s1);

        // S2 — Deferida (Segunda chamada)
        Solicitacao s2 = new Solicitacao();
        s2.setProtocolo("DEL-2026-000002");
        s2.setTipo(TipoSolicitacao.SEGUNDA_CHAMADA);
        s2.setStatus(StatusSolicitacao.RESOLVIDO);
        s2.setAlunoNome("Lucas Ferreira Santos");
        s2.setAlunoEmail("lucas@exemplo.edu.br");
        s2.setAlunoCurso("Engenharia de Software");
        s2.setAlunoTurma("3º A");
        s2.setDisciplina("Banco de Dados II");
        s2.setProfessor("Profa. Cláudia Mendes");
        s2.setDataOcorrencia(LocalDate.of(2026, 3, 15));
        s2.setJustificativa("Participação em evento acadêmico obrigatório (SBIE 2026).");
        s2.setParecer("Deferido. Segunda chamada agendada para 05/04/2026.");
        s2.setInstituicao(inst);
        s2.setCriadoEm(LocalDateTime.of(2026, 3, 16, 8, 30));
        s2.setAtualizadoEm(LocalDateTime.of(2026, 3, 20, 14, 45));
        s2.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno");
        s2.adicionarHistorico(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.RESOLVIDO,
                "Deferido. Segunda chamada agendada para 05/04/2026.", coord);
        solicitacaoRepo.save(s2);

        // S3 — Negada (Contestação de nota)
        Solicitacao s3 = new Solicitacao();
        s3.setProtocolo("DEL-2026-000003");
        s3.setTipo(TipoSolicitacao.CONTESTACAO_NOTA);
        s3.setStatus(StatusSolicitacao.NEGADO);
        s3.setAlunoNome("Ana Paula Oliveira");
        s3.setAlunoEmail("ana@exemplo.edu.br");
        s3.setAlunoCurso("Administração");
        s3.setAlunoTurma("4º B");
        s3.setDisciplina("Cálculo I");
        s3.setProfessor("Prof. André Souza");
        s3.setDataOcorrencia(LocalDate.of(2026, 3, 10));
        s3.setNotaAtual("4.5");
        s3.setNotaEsperada("7.0");
        s3.setJustificativa("A nota da P2 está diferente do gabarito divulgado.");
        s3.setParecer("Indeferido. Correção de acordo com critérios estabelecidos.");
        s3.setInstituicao(inst);
        s3.setCriadoEm(LocalDateTime.of(2026, 3, 11, 16, 0));
        s3.setAtualizadoEm(LocalDateTime.of(2026, 3, 18, 9, 20));
        s3.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno");
        s3.adicionarHistorico(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.AGUARDANDO_PROFESSOR,
                "Encaminhada ao professor para reanálise", coord);
        s3.adicionarHistorico(StatusSolicitacao.AGUARDANDO_PROFESSOR, StatusSolicitacao.NEGADO,
                "Indeferido após reanálise pelo professor.", coord);
        solicitacaoRepo.save(s3);

        // S4 — Em Análise (Abono de falta, outro aluno)
        Solicitacao s4 = new Solicitacao();
        s4.setProtocolo("DEL-2026-000004");
        s4.setTipo(TipoSolicitacao.ABONO_FALTA);
        s4.setStatus(StatusSolicitacao.EM_ANALISE);
        s4.setAlunoNome("Maria Santos Costa");
        s4.setAlunoEmail("maria@exemplo.edu.br");
        s4.setAlunoCurso("Administração");
        s4.setAlunoTurma("2º A");
        s4.setDisciplina("Teoria Geral da Administração");
        s4.setProfessor("Prof. Roberto Lima");
        s4.setDataOcorrencia(LocalDate.of(2026, 4, 2));
        s4.setJustificativa("Internação hospitalar de familiar. Declaração do hospital em anexo.");
        s4.setInstituicao(inst);
        s4.setCriadoEm(LocalDateTime.of(2026, 4, 3, 9, 0));
        s4.setAtualizadoEm(s4.getCriadoEm());
        s4.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno");
        solicitacaoRepo.save(s4);

        // S5 — Aguardando Professor (Ajuste de presença)
        Solicitacao s5 = new Solicitacao();
        s5.setProtocolo("DEL-2026-000005");
        s5.setTipo(TipoSolicitacao.AJUSTE_PRESENCA);
        s5.setStatus(StatusSolicitacao.AGUARDANDO_PROFESSOR);
        s5.setAlunoNome("Pedro Souza Lima");
        s5.setAlunoEmail("pedro@exemplo.edu.br");
        s5.setAlunoCurso("Direito");
        s5.setAlunoTurma("5º A");
        s5.setDisciplina("Contabilidade Empresarial");
        s5.setProfessor("Profa. Fernanda Bastos");
        s5.setDataOcorrencia(LocalDate.of(2026, 3, 25));
        s5.setJustificativa("Estava presente na aula mas não consegui assinar a lista de presença.");
        s5.setInstituicao(inst);
        s5.setCriadoEm(LocalDateTime.of(2026, 3, 26, 14, 30));
        s5.setAtualizadoEm(LocalDateTime.of(2026, 3, 28, 11, 0));
        s5.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Solicitação criada pelo aluno");
        s5.adicionarHistorico(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.AGUARDANDO_PROFESSOR,
                "Encaminhada à professora para confirmar presença", coord);
        solicitacaoRepo.save(s5);

        // ── Instituições de teste para PilotoScheduledJob ──

        // Inst. com piloto expirado há 2 dias (teve atividade = tem solicitação)
        Instituicao instPilotoExpirado = new Instituicao("Faculdade Teste Piloto", "FTPILOTO");
        instPilotoExpirado.setPilotoExpiraEm(LocalDateTime.now().minusDays(2));
        // Domínio null = emails de retenção vão pro remetente (contato@appdelibera.com.br)
        instPilotoExpirado = instituicaoRepo.save(instPilotoExpirado);

        // Cria 1 solicitação nessa inst pra simular atividade
        Solicitacao sTestePiloto = new Solicitacao();
        sTestePiloto.setProtocolo("DEL-2026-TESTE1");
        sTestePiloto.setTipo(TipoSolicitacao.ABONO_FALTA);
        sTestePiloto.setStatus(StatusSolicitacao.EM_ANALISE);
        sTestePiloto.setAlunoNome("Aluno Teste Piloto");
        sTestePiloto.setAlunoEmail("aluno@testepiloto.edu.br");
        sTestePiloto.setDisciplina("Disciplina Teste");
        sTestePiloto.setJustificativa("Teste do job de piloto");
        sTestePiloto.setInstituicao(instPilotoExpirado);
        sTestePiloto.setCriadoEm(LocalDateTime.now().minusDays(15));
        sTestePiloto.setAtualizadoEm(sTestePiloto.getCriadoEm());
        sTestePiloto.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Criada para teste");
        solicitacaoRepo.save(sTestePiloto);

        // Inst. com voucher expirado há 1 dia
        Instituicao instVoucherExpirado = new Instituicao("Centro Teste Voucher", "CTVOUCHER");
        instVoucherExpirado.setPilotoExpiraEm(LocalDateTime.now().minusDays(62)); // piloto expirou há 62 dias
        instVoucherExpirado.setVoucherAtivadoEm(LocalDateTime.now().minusDays(31)); // voucher expirou há 1 dia
        instVoucherExpirado.setEmailPilotoEnviado(true); // já recebeu email do piloto
        // Domínio null = emails de retenção vão pro remetente (contato@appdelibera.com.br)
        instituicaoRepo.save(instVoucherExpirado);

        // ── Erros mock para tela de monitoramento ──

        ErroLog e1 = new ErroLog();
        e1.setTipo("java.lang.NullPointerException");
        e1.setMensagem("Cannot invoke \"String.length()\" because \"str\" is null");
        e1.setStacktrace("java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"str\" is null\n\tat com.delibera.service.SolicitacaoService.criar(SolicitacaoService.java:85)\n\tat com.delibera.controller.SolicitacaoPublicController.criar(SolicitacaoPublicController.java:68)\n\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n\t... 45 more");
        e1.setUrl("/nova-solicitacao");
        e1.setMetodoHttp("POST");
        e1.setUsuario(null);
        e1.setIp("189.40.72.101");
        e1.setCriadoEm(LocalDateTime.now().minusHours(3));
        e1.setAlertaEnviado(true);
        erroLogRepo.save(e1);

        ErroLog e2 = new ErroLog();
        e2.setTipo("org.springframework.dao.DataIntegrityViolationException");
        e2.setMensagem("could not execute statement; SQL [n/a]; constraint [uk_protocolo]");
        e2.setStacktrace("org.springframework.dao.DataIntegrityViolationException: could not execute statement\n\tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException\n\tat com.delibera.service.SolicitacaoService.criar(SolicitacaoService.java:82)\n\t... 38 more\nCaused by: org.hibernate.exception.ConstraintViolationException: could not execute statement\n\t... 52 more");
        e2.setUrl("/nova-solicitacao");
        e2.setMetodoHttp("POST");
        e2.setUsuario(null);
        e2.setIp("201.17.55.230");
        e2.setCriadoEm(LocalDateTime.now().minusHours(1));
        e2.setAlertaEnviado(true);
        erroLogRepo.save(e2);

        ErroLog e3 = new ErroLog();
        e3.setTipo("java.io.IOException");
        e3.setMensagem("No space left on device");
        e3.setStacktrace("java.io.IOException: No space left on device\n\tat java.base/java.io.FileOutputStream.writeBytes(Native Method)\n\tat com.delibera.service.AnexoService.salvar(AnexoService.java:71)\n\t... 29 more");
        e3.setUrl("/nova-solicitacao");
        e3.setMetodoHttp("POST");
        e3.setUsuario(null);
        e3.setIp("177.92.14.88");
        e3.setCriadoEm(LocalDateTime.now().minusMinutes(15));
        e3.setAlertaEnviado(false);
        erroLogRepo.save(e3);

        ErroLog e4 = new ErroLog();
        e4.setTipo("jakarta.mail.MessagingException");
        e4.setMensagem("Could not connect to SMTP host: smtp.zoho.com, port: 465");
        e4.setStacktrace("jakarta.mail.MessagingException: Could not connect to SMTP host\n\tat com.sun.mail.smtp.SMTPTransport.openServer\n\tat com.delibera.service.EmailHtmlService.enviarHtml(EmailHtmlService.java:228)\n\t... 18 more");
        e4.setUrl("@Async: EmailHtmlService.enviarVoucherExtensao");
        e4.setMetodoHttp("ASYNC");
        e4.setUsuario(null);
        e4.setIp(null);
        e4.setCriadoEm(LocalDateTime.now().minusMinutes(5));
        e4.setAlertaEnviado(true);
        e4.setResolvido(true);
        erroLogRepo.save(e4);
    }
}
