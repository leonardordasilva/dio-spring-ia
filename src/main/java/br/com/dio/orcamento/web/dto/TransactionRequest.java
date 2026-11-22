package br.com.dio.orcamento.web.dto;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.TransactionSource;
import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.service.NewTransaction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Corpo do POST /api/v1/transactions. Validação de formato aqui; regras de negócio no serviço. */
public record TransactionRequest(
        @NotBlank @Size(min = 2, max = 255) String description,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        @NotNull Category category,
        @PastOrPresent LocalDate date) {

    public NewTransaction toCommand() {
        return new NewTransaction(description, amount, type, category, date, TransactionSource.API);
    }
}
