package br.com.dio.orcamento.domain;

/** Canal pelo qual a transação entrou no sistema (útil para auditoria). */
public enum TransactionSource {
    VOZ,
    TEXTO,
    API
}
