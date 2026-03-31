// mock-data.js - Dados mockados para demonstração do protótipo Delibera

const SOLICITACOES_MOCK = [
  {
    protocolo: "DEL-2026-000147",
    status: "pendente",
    aluno: {
      nome: "Maria Santos",
      email: "maria.santos@fatec.sp.gov.br",
      whatsapp: "(11) 99999-9999",
      curso: "ADS",
      turma: "3º A"
    },
    solicitacao: {
      tipo: "Segunda chamada",
      disciplina: "Algoritmos",
      dataRelacionada: "20/12/2026",
      dataEnvio: "18/12/2026 às 09:15",
      descricao: "Não pude comparecer à prova devido a consulta médica de emergência. Segue atestado anexo.",
      anexo: {
        nome: "atestado_maria.pdf",
        tamanho: "245 KB",
        tipo: "application/pdf"
      }
    },
    historico: [
      { data: "18/12/2026 09:15", evento: "Solicitação recebida", status: "pendente" }
    ]
  },
  {
    protocolo: "DEL-2026-000148",
    status: "pendente",
    aluno: {
      nome: "João Oliveira",
      email: "joao.oliveira@fatec.sp.gov.br",
      whatsapp: "(11) 98888-8888",
      curso: "ADS",
      turma: "2º B"
    },
    solicitacao: {
      tipo: "Abono de falta",
      disciplina: "Banco de Dados",
      dataRelacionada: "15/12/2026",
      dataEnvio: "19/12/2026 às 14:30",
      descricao: "Precisei comparecer a audiência judicial. Anexo comprovante.",
      anexo: {
        nome: "comprovante_joao.pdf",
        tamanho: "180 KB",
        tipo: "application/pdf"
      }
    },
    historico: [
      { data: "19/12/2026 14:30", evento: "Solicitação recebida", status: "pendente" }
    ]
  },
  {
    protocolo: "DEL-2026-000149",
    status: "pendente",
    aluno: {
      nome: "Ana Costa",
      email: "ana.costa@fatec.sp.gov.br",
      whatsapp: "(11) 97777-7777",
      curso: "Sistemas de Informação",
      turma: "4º A"
    },
    solicitacao: {
      tipo: "Retificação de nota",
      disciplina: "Engenharia de Software",
      dataRelacionada: "12/12/2026",
      dataEnvio: "19/12/2026 às 16:45",
      descricao: "Acredito que houve erro na correção da questão 3. Gostaria de revisar a prova.",
      anexo: null
    },
    historico: [
      { data: "19/12/2026 16:45", evento: "Solicitação recebida", status: "pendente" }
    ]
  },
  {
    protocolo: "DEL-2026-000150",
    status: "em_analise",
    aluno: {
      nome: "Pedro Souza",
      email: "pedro.souza@fatec.sp.gov.br",
      whatsapp: "(11) 96666-6666",
      curso: "ADS",
      turma: "3º A"
    },
    solicitacao: {
      tipo: "Segunda chamada",
      disciplina: "Estrutura de Dados",
      dataRelacionada: "18/12/2026",
      dataEnvio: "20/12/2026 às 08:00",
      descricao: "Tive Covid-19 e não pude comparecer. Segue atestado médico.",
      anexo: {
        nome: "atestado_pedro.pdf",
        tamanho: "310 KB",
        tipo: "application/pdf"
      }
    },
    historico: [
      { data: "20/12/2026 08:00", evento: "Solicitação recebida", status: "pendente" },
      { data: "20/12/2026 10:30", evento: "Em análise pela coordenação", status: "em_analise" }
    ]
  },
  {
    protocolo: "DEL-2026-000146",
    status: "deferida",
    aluno: {
      nome: "Carlos Mendes",
      email: "carlos.mendes@fatec.sp.gov.br",
      whatsapp: "(11) 95555-5555",
      curso: "ADS",
      turma: "1º B"
    },
    solicitacao: {
      tipo: "Segunda chamada",
      disciplina: "Lógica de Programação",
      dataRelacionada: "10/12/2026",
      dataEnvio: "16/12/2026 às 11:20",
      descricao: "Acidente de trânsito no dia da prova. Anexo boletim de ocorrência.",
      anexo: {
        nome: "bo_carlos.pdf",
        tamanho: "520 KB",
        tipo: "application/pdf"
      }
    },
    decisao: {
      tipo: "deferida",
      parecer: "Solicitação DEFERIDA conforme Art. 84 do Regimento Acadêmico. Documentação apresentada está de acordo com as normas institucionais.",
      novaData: "08/01/2027",
      dataDecisao: "18/12/2026 às 15:00",
      coordenador: "Prof. João Silva"
    },
    historico: [
      { data: "16/12/2026 11:20", evento: "Solicitação recebida", status: "pendente" },
      { data: "17/12/2026 09:00", evento: "Em análise pela coordenação", status: "em_analise" },
      { data: "18/12/2026 15:00", evento: "Solicitação deferida", status: "deferida" }
    ]
  },
  {
    protocolo: "DEL-2026-000145",
    status: "indeferida",
    aluno: {
      nome: "Juliana Reis",
      email: "juliana.reis@fatec.sp.gov.br",
      whatsapp: "(11) 94444-4444",
      curso: "ADS",
      turma: "2º A"
    },
    solicitacao: {
      tipo: "Abono de falta",
      disciplina: "Matemática Discreta",
      dataRelacionada: "05/12/2026",
      dataEnvio: "20/12/2026 às 13:00",
      descricao: "Tive problema de saúde e não pude comparecer.",
      anexo: null
    },
    decisao: {
      tipo: "indeferida",
      parecer: "Solicitação INDEFERIDA. O prazo regulamentar para solicitação de abono de falta expirou conforme Art. 85 do Regimento Acadêmico. A solicitação deveria ter sido feita em até 5 dias úteis após a ocorrência.",
      dataDecisao: "21/12/2026 às 10:00",
      coordenador: "Prof. João Silva"
    },
    historico: [
      { data: "20/12/2026 13:00", evento: "Solicitação recebida", status: "pendente" },
      { data: "21/12/2026 09:30", evento: "Em análise pela coordenação", status: "em_analise" },
      { data: "21/12/2026 10:00", evento: "Solicitação indeferida", status: "indeferida" }
    ]
  }
];

// Status disponíveis
const STATUS_LABELS = {
  pendente: { texto: "Pendente", classe: "bg-warning text-dark", icone: "clock" },
  em_analise: { texto: "Em Análise", classe: "bg-info text-white", icone: "hourglass-split" },
  deferida: { texto: "Deferida", classe: "bg-success text-white", icone: "check-circle" },
  indeferida: { texto: "Indeferida", classe: "bg-danger text-white", icone: "x-circle" }
};

// Tipos de solicitação
const TIPOS_SOLICITACAO = [
  "Segunda chamada de prova",
  "Abono / justificativa de falta",
  "Retificação de nota"
];

// Funções auxiliares
function buscarSolicitacaoPorProtocolo(protocolo) {
  return SOLICITACOES_MOCK.find(s => s.protocolo === protocolo);
}

function filtrarSolicitacoesPorStatus(status) {
  if (!status) return SOLICITACOES_MOCK;
  return SOLICITACOES_MOCK.filter(s => s.status === status);
}

function gerarProtocolo() {
  const ano = new Date().getFullYear();
  const numero = String(Math.floor(Math.random() * 900000) + 100000);
  return `DEL-${ano}-${numero}`;
}

function formatarData(data) {
  if (!data) return '';
  return new Date(data).toLocaleDateString('pt-BR');
}

function formatarDataHora(data) {
  if (!data) return '';
  return new Date(data).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

// Exportar para uso global (quando necessário)
if (typeof window !== 'undefined') {
  window.DELIBERA_MOCK = {
    solicitacoes: SOLICITACOES_MOCK,
    statusLabels: STATUS_LABELS,
    tiposSolicitacao: TIPOS_SOLICITACAO,
    buscarSolicitacaoPorProtocolo,
    filtrarSolicitacoesPorStatus,
    gerarProtocolo,
    formatarData,
    formatarDataHora
  };
}
