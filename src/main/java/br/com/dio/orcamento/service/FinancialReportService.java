package br.com.dio.orcamento.service;

import br.com.dio.orcamento.domain.Transaction;
import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.repository.CategoryTotal;
import br.com.dio.orcamento.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Consultas financeiras agregadas: saldo, totais por categoria, resumo mensal e maiores gastos. */
@Service
@Transactional(readOnly = true)
public class FinancialReportService {

    private static final int MAX_LIMIT = 50;

    private final TransactionRepository repository;
    private final Clock clock;

    public FinancialReportService(TransactionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Period resolve(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : YearMonth.from(end).atDay(1);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
        return new Period(start, end);
    }

    public Balance balance(LocalDate from, LocalDate to) {
        Period p = resolve(from, to);
        BigDecimal income = repository.sumByTypeBetween(TransactionType.RECEITA, p.from(), p.to());
        BigDecimal expenses = repository.sumByTypeBetween(TransactionType.DESPESA, p.from(), p.to());
        return new Balance(p, scale(income), scale(expenses), scale(income.subtract(expenses)));
    }

    public CategoryBreakdown byCategory(TransactionType type, LocalDate from, LocalDate to) {
        Period p = resolve(from, to);
        TransactionType effectiveType = type != null ? type : TransactionType.DESPESA;
        List<CategoryTotal> totals = repository.totalsByCategory(effectiveType, p.from(), p.to());
        BigDecimal grand = totals.stream().map(CategoryTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryShare> shares = totals.stream()
                .map(t -> new CategoryShare(t.category().name(), t.category().label(), scale(t.total()), t.count(),
                        percentage(t.total(), grand)))
                .toList();
        return new CategoryBreakdown(p, effectiveType, scale(grand), shares);
    }

    public MonthlySummary monthly(YearMonth month) {
        YearMonth target = month != null ? month : YearMonth.now(clock);
        Balance balance = balance(target.atDay(1), target.atEndOfMonth());
        CategoryBreakdown expenses = byCategory(TransactionType.DESPESA, target.atDay(1), target.atEndOfMonth());
        String topCategory = expenses.categories().isEmpty() ? null : expenses.categories().getFirst().label();
        return new MonthlySummary(target.toString(), balance.income(), balance.expenses(), balance.balance(),
                topCategory, expenses.categories());
    }

    public List<Transaction> largest(TransactionType type, LocalDate from, LocalDate to, int limit) {
        Period p = resolve(from, to);
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        TransactionType effectiveType = type != null ? type : TransactionType.DESPESA;
        return repository.findLargest(effectiveType, p.from(), p.to(), PageRequest.of(0, size));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_EVEN);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_EVEN);
    }

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Balance(Period period, BigDecimal income, BigDecimal expenses, BigDecimal balance) {
    }

    public record CategoryShare(String category, String label, BigDecimal total, long count, BigDecimal percentage) {
    }

    public record CategoryBreakdown(Period period, TransactionType type, BigDecimal total,
                                    List<CategoryShare> categories) {
    }

    public record MonthlySummary(String month, BigDecimal income, BigDecimal expenses, BigDecimal balance,
                                 String topExpenseCategory, List<CategoryShare> expensesByCategory) {
    }
}
