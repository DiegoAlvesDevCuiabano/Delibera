package com.delibera.model.dto;

import com.delibera.model.enums.TipoSolicitacao;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class NovaSolicitacaoDTO {

    @NotNull(message = "Instituição é obrigatória")
    private Long instituicaoId;

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 200, message = "Nome deve ter no máximo 200 caracteres")
    private String alunoNome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 200, message = "Email deve ter no máximo 200 caracteres")
    private String alunoEmail;

    @Size(max = 20, message = "WhatsApp deve ter no máximo 20 caracteres")
    private String alunoWhatsapp;

    @Size(max = 200)
    private String alunoCurso;

    @Size(max = 20)
    private String alunoTurma;

    @NotNull(message = "Tipo de solicitação é obrigatório")
    private TipoSolicitacao tipo;

    @NotBlank(message = "Disciplina é obrigatória")
    @Size(max = 200)
    private String disciplina;

    @Size(max = 100)
    private String professor;

    private LocalDate dataOcorrencia;

    private String datasFalta;

    @Size(max = 10)
    private String notaAtual;

    @Size(max = 10)
    private String notaEsperada;

    @NotBlank(message = "Justificativa é obrigatória")
    @Size(max = 5000, message = "Justificativa deve ter no máximo 5000 caracteres")
    private String justificativa;
}
