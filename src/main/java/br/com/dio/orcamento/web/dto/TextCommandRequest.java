package br.com.dio.orcamento.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Comando em texto: mesmo fluxo da voz, sem a etapa de transcrição (útil para testes e chatbots). */
public record TextCommandRequest(@NotBlank @Size(max = 1000) String text) {
}
