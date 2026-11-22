package br.com.dio.orcamento.web.dto;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.Transaction;
import br.com.dio.orcamento.domain.TransactionSource;
import br.com.dio.orcamento.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(Long id, String description, BigDecimal amount, TransactionType type,
                                  Category category, String categoryLabel, LocalDate date,
                                  TransactionSource source, Instant createdAt) {

    public static TransactionResponse of(Transaction t) {
        return new TransactionResponse(t.getId(), t.getDescription(), t.getAmount(), t.getType(), t.getCategory(),
                t.getCategory().label(), t.getDate(), t.getSource(), t.getCreatedAt());
    }
}
