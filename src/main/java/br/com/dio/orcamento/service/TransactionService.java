package br.com.dio.orcamento.service;

import br.com.dio.orcamento.domain.Transaction;
import br.com.dio.orcamento.repository.TransactionFilter;
import br.com.dio.orcamento.repository.TransactionRepository;
import br.com.dio.orcamento.repository.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

/** Casos de uso de escrita e leitura de transações. */
@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final TransactionValidator validator;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, TransactionValidator validator, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public Transaction create(NewTransaction command) {
        validator.validate(command);
        LocalDate date = command.date() != null ? command.date() : LocalDate.now(clock);
        Transaction tx = new Transaction(
                command.description().trim(),
                command.amount().setScale(2, RoundingMode.UNNECESSARY),
                command.type(),
                command.category(),
                date,
                command.source());
        return repository.save(tx);
    }

    @Transactional(readOnly = true)
    public Transaction findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação %d não encontrada".formatted(id)));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> search(TransactionFilter filter, Pageable pageable) {
        return repository.findAll(TransactionSpecifications.withFilter(filter), pageable);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
    }
}
