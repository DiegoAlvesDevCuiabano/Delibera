package com.delibera.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SuporteContatoDTO {

    @NotBlank
    @Size(max = 200)
    private String nome;

    @NotBlank
    @Email
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    @Size(max = 200)
    private String email;

    @NotBlank
    @Size(max = 200)
    private String assunto;

    @NotBlank
    @Size(max = 5000)
    private String mensagem;
}
