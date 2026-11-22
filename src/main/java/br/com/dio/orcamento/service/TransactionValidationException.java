package br.com.dio.orcamento.service;

import java.util.List;

/** Lançada quando uma transação viola regras de negócio; carrega todas as violações encontradas. */
public class TransactionValidationException extends RuntimeException {

    private final List<String> errors;

    public TransactionValidationException(List<String> errors) {
        super("Transação inválida: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
