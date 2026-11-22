package br.com.dio.orcamento.repository;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.Transaction;
import br.com.dio.orcamento.domain.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Filtros dinâmicos e combináveis para a listagem de transações. */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> withFilter(TransactionFilter filter) {
        Specification<Transaction> spec = (root, query, cb) -> cb.conjunction();
        if (filter == null) {
            return spec;
        }
        return spec
                .and(hasType(filter.type()))
                .and(hasCategory(filter.category()))
                .and(dateFrom(filter.from()))
                .and(dateTo(filter.to()))
                .and(descriptionContains(filter.text()));
    }

    private static Specification<Transaction> hasType(TransactionType type) {
        return type == null ? null : (root, q, cb) -> cb.equal(root.get("type"), type);
    }

    private static Specification<Transaction> hasCategory(Category category) {
        return category == null ? null : (root, q, cb) -> cb.equal(root.get("category"), category);
    }

    private static Specification<Transaction> dateFrom(LocalDate from) {
        return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    private static Specification<Transaction> dateTo(LocalDate to) {
        return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("date"), to);
    }

    private static Specification<Transaction> descriptionContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("description")), pattern);
    }
}
