package br.com.dio.orcamento.repository;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.TransactionType;

import java.time.LocalDate;

/** Critérios opcionais de busca; campos nulos são ignorados. */
public record TransactionFilter(TransactionType type, Category category, LocalDate from, LocalDate to, String text) {

    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, null, null);
    }
}
