package br.com.dio.orcamento.service;

import br.com.dio.orcamento.config.BudgetProperties;
import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.TransactionSource;
import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private TransactionRepository repository;
    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        var props = new BudgetProperties(new BigDecimal("1000.00"), 5, Duration.ofMinutes(2),
                new BudgetProperties.Audio(DataSize.ofMegabytes(10), List.of("mp3")));
        var clock = Clock.fixed(Instant.parse("2026-11-22T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        validator = new TransactionValidator(props, repository, clock);
    }

    private static NewTransaction tx(String desc, String amount, TransactionType type, Category cat, LocalDate date) {
        return new NewTransaction(desc, amount == null ? null : new BigDecimal(amount), type, cat, date,
                TransactionSource.API);
    }

    @Test
    void acceptsValidTransaction() {
        assertThatCode(() -> validator.validate(tx("Almoço", "45.90", TransactionType.DESPESA,
                Category.ALIMENTACAO, TODAY))).doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingDateMeaningToday() {
        assertThatCode(() -> validator.validate(tx("Salário", "900", TransactionType.RECEITA,
                Category.SALARIO, null))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveAndTooManyDecimals() {
        assertThatThrownBy(() -> validator.validate(tx("Café", "-1.999", TransactionType.DESPESA,
                Category.ALIMENTACAO, TODAY)))
                .isInstanceOf(TransactionValidationException.class)
                .extracting(e -> ((TransactionValidationException) e).getErrors())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder("O valor deve ser maior que zero",
                        "O valor deve ter no máximo 2 casas decimais");
    }

    @Test
    void rejectsAmountAboveLimit() {
        assertThatThrownBy(() -> validator.validate(tx("Carro", "1000.01", TransactionType.DESPESA,
                Category.TRANSPORTE, TODAY)))
                .hasMessageContaining("excede o limite");
    }

    @Test
    void rejectsIncompatibleCategory() {
        assertThatThrownBy(() -> validator.validate(tx("Salário", "100", TransactionType.DESPESA,
                Category.SALARIO, TODAY)))
                .hasMessageContaining("não é compatível");
    }

    @Test
    void rejectsFutureAndTooOldDates() {
        assertThatThrownBy(() -> validator.validate(tx("Show", "100", TransactionType.DESPESA,
                Category.LAZER, TODAY.plusDays(1))))
                .hasMessageContaining("futuro");
        assertThatThrownBy(() -> validator.validate(tx("Show", "100", TransactionType.DESPESA,
                Category.LAZER, TODAY.minusYears(6))))
                .hasMessageContaining("anterior a 5 anos");
    }

    @Test
    void rejectsMissingFieldsAndShortDescription() {
        assertThatThrownBy(() -> validator.validate(tx(" ", null, null, null, TODAY)))
                .isInstanceOf(TransactionValidationException.class)
                .extracting(e -> ((TransactionValidationException) e).getErrors())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(4);
    }

    @Test
    void rejectsDuplicateWithinWindow() {
        when(repository.existsByDescriptionIgnoreCaseAndAmountAndTypeAndDateAndCreatedAtAfter(
                anyString(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(tx("Uber", "20", TransactionType.DESPESA,
                Category.TRANSPORTE, TODAY)))
                .hasMessageContaining("possível comando repetido");
    }
}
