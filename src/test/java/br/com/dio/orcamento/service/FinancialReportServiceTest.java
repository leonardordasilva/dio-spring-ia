package br.com.dio.orcamento.service;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.TransactionSource;
import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static br.com.dio.orcamento.support.TestClockConfig.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestClockConfig.class, TransactionService.class, TransactionValidator.class, FinancialReportService.class})
class FinancialReportServiceTest {

    @Autowired
    TransactionService transactions;
    @Autowired
    FinancialReportService reports;

    @BeforeEach
    void seed() {
        add("Salário", "5000.00", TransactionType.RECEITA, Category.SALARIO, TODAY.withDayOfMonth(5));
        add("Mercado", "600.00", TransactionType.DESPESA, Category.ALIMENTACAO, TODAY.withDayOfMonth(6));
        add("Restaurante", "200.00", TransactionType.DESPESA, Category.ALIMENTACAO, TODAY.withDayOfMonth(10));
        add("Aluguel", "1200.00", TransactionType.DESPESA, Category.MORADIA, TODAY.withDayOfMonth(1));
        add("Cinema", "50.00", TransactionType.DESPESA, Category.LAZER, TODAY.minusMonths(1));
    }

    private void add(String d, String v, TransactionType t, Category c, LocalDate date) {
        transactions.create(new NewTransaction(d, new BigDecimal(v), t, c, date, TransactionSource.API));
    }

    @Test
    void balanceDefaultsToCurrentMonth() {
        var balance = reports.balance(null, null);

        assertThat(balance.period().from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(balance.period().to()).isEqualTo(TODAY);
        assertThat(balance.income()).isEqualByComparingTo("5000.00");
        assertThat(balance.expenses()).isEqualByComparingTo("2000.00");
        assertThat(balance.balance()).isEqualByComparingTo("3000.00");
    }

    @Test
    void groupsExpensesByCategoryWithPercentages() {
        var breakdown = reports.byCategory(null, null, null);

        assertThat(breakdown.type()).isEqualTo(TransactionType.DESPESA);
        assertThat(breakdown.total()).isEqualByComparingTo("2000.00");
        assertThat(breakdown.categories()).extracting(FinancialReportService.CategoryShare::category)
                .containsExactly("MORADIA", "ALIMENTACAO");
        assertThat(breakdown.categories().get(1).count()).isEqualTo(2);
        assertThat(breakdown.categories().getFirst().percentage()).isEqualByComparingTo("60.0");
    }

    @Test
    void monthlySummaryPointsTopCategory() {
        var october = reports.monthly(YearMonth.of(2026, 10));
        assertThat(october.expenses()).isEqualByComparingTo("50.00");
        assertThat(october.topExpenseCategory()).isEqualTo("Lazer");

        var current = reports.monthly(null);
        assertThat(current.month()).isEqualTo("2026-11");
        assertThat(current.topExpenseCategory()).isEqualTo("Moradia");
    }

    @Test
    void largestExpensesAreOrderedByAmount() {
        var top = reports.largest(TransactionType.DESPESA, null, null, 2);
        assertThat(top).extracting(t -> t.getDescription()).containsExactly("Aluguel", "Mercado");
    }

    @Test
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> reports.balance(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
