package br.com.dio.orcamento.service;

import br.com.dio.orcamento.config.BudgetProperties;
import br.com.dio.orcamento.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras de negócio aplicadas antes de persistir qualquer transação, independente do canal.
 * A IA pode interpretar mal um comando de voz; esta camada garante que dados inconsistentes
 * nunca cheguem ao banco.
 */
@Component
public class TransactionValidator {

    private static final int MIN_DESCRIPTION = 2;
    private static final int MAX_DESCRIPTION = 255;

    private final BudgetProperties properties;
    private final TransactionRepository repository;
    private final Clock clock;

    public TransactionValidator(BudgetProperties properties, TransactionRepository repository, Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.clock = clock;
    }

    public void validate(NewTransaction tx) {
        List<String> errors = new ArrayList<>();

        String description = tx.description() == null ? "" : tx.description().trim();
        if (description.length() < MIN_DESCRIPTION || description.length() > MAX_DESCRIPTION) {
            errors.add("A descrição deve ter entre %d e %d caracteres".formatted(MIN_DESCRIPTION, MAX_DESCRIPTION));
        }

        BigDecimal amount = tx.amount();
        if (amount == null) {
            errors.add("O valor é obrigatório");
        } else {
            if (amount.signum() <= 0) {
                errors.add("O valor deve ser maior que zero");
            }
            if (amount.stripTrailingZeros().scale() > 2) {
                errors.add("O valor deve ter no máximo 2 casas decimais");
            }
            if (amount.compareTo(properties.maxAmount()) > 0) {
                errors.add("O valor excede o limite de R$ %s por transação".formatted(properties.maxAmount()));
            }
        }

        if (tx.type() == null) {
            errors.add("O tipo (RECEITA ou DESPESA) é obrigatório");
        }
        if (tx.category() == null) {
            errors.add("A categoria é obrigatória");
        } else if (tx.type() != null && !tx.category().accepts(tx.type())) {
            errors.add("A categoria %s não é compatível com o tipo %s".formatted(tx.category(), tx.type()));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate date = tx.date();
        if (date != null) {
            if (date.isAfter(today)) {
                errors.add("A data não pode estar no futuro");
            }
            if (date.isBefore(today.minusYears(properties.maxPastYears()))) {
                errors.add("A data não pode ser anterior a %d anos".formatted(properties.maxPastYears()));
            }
        }

        if (errors.isEmpty() && isDuplicate(tx, description, date == null ? today : date)) {
            errors.add("Transação idêntica registrada há menos de %d minuto(s); possível comando repetido"
                    .formatted(Math.max(1, properties.duplicateWindow().toMinutes())));
        }

        if (!errors.isEmpty()) {
            throw new TransactionValidationException(errors);
        }
    }

    private boolean isDuplicate(NewTransaction tx, String description, LocalDate date) {
        Instant since = clock.instant().minus(properties.duplicateWindow());
        return repository.existsByDescriptionIgnoreCaseAndAmountAndTypeAndDateAndCreatedAtAfter(
                description, tx.amount(), tx.type(), date, since);
    }
}
