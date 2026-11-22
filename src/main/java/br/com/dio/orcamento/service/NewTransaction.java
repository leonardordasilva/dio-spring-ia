package br.com.dio.orcamento.service;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.TransactionSource;
import br.com.dio.orcamento.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Comando de criação de transação, comum a todos os canais (REST, texto, voz). */
public record NewTransaction(String description, BigDecimal amount, TransactionType type,
                             Category category, LocalDate date, TransactionSource source) {
}
