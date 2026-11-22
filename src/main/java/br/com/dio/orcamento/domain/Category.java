package br.com.dio.orcamento.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Categorias suportadas. Cada categoria declara quais tipos de transação aceita,
 * o que permite validar combinações sem sentido (ex.: "salário" como despesa).
 */
public enum Category {
    ALIMENTACAO("Alimentação", EnumSet.of(TransactionType.DESPESA)),
    TRANSPORTE("Transporte", EnumSet.of(TransactionType.DESPESA)),
    MORADIA("Moradia", EnumSet.of(TransactionType.DESPESA)),
    SAUDE("Saúde", EnumSet.of(TransactionType.DESPESA)),
    EDUCACAO("Educação", EnumSet.of(TransactionType.DESPESA)),
    LAZER("Lazer", EnumSet.of(TransactionType.DESPESA)),
    CONTAS("Contas e serviços", EnumSet.of(TransactionType.DESPESA)),
    COMPRAS("Compras", EnumSet.of(TransactionType.DESPESA)),
    SALARIO("Salário", EnumSet.of(TransactionType.RECEITA)),
    FREELANCE("Freelance", EnumSet.of(TransactionType.RECEITA)),
    INVESTIMENTOS("Investimentos", EnumSet.allOf(TransactionType.class)),
    OUTROS("Outros", EnumSet.allOf(TransactionType.class));

    private final String label;
    private final Set<TransactionType> allowedTypes;

    Category(String label, Set<TransactionType> allowedTypes) {
        this.label = label;
        this.allowedTypes = allowedTypes;
    }

    public String label() {
        return label;
    }

    public boolean accepts(TransactionType type) {
        return allowedTypes.contains(type);
    }
}
