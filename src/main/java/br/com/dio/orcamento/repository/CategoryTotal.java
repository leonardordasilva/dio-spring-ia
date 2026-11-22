package br.com.dio.orcamento.repository;

import br.com.dio.orcamento.domain.Category;

import java.math.BigDecimal;

/** Projeção com o total gasto/recebido por categoria em um período. */
public record CategoryTotal(Category category, BigDecimal total, long count) {
}
