package br.com.dio.orcamento.repository;

import br.com.dio.orcamento.domain.Transaction;
import br.com.dio.orcamento.domain.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.type = :type and t.date between :from and :to
            """)
    BigDecimal sumByTypeBetween(@Param("type") TransactionType type,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    @Query("""
            select new br.com.dio.orcamento.repository.CategoryTotal(t.category, sum(t.amount), count(t))
            from Transaction t
            where t.type = :type and t.date between :from and :to
            group by t.category
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> totalsByCategory(@Param("type") TransactionType type,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    @Query("""
            select t from Transaction t
            where t.type = :type and t.date between :from and :to
            order by t.amount desc, t.date desc
            """)
    List<Transaction> findLargest(@Param("type") TransactionType type,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  Pageable pageable);

    boolean existsByDescriptionIgnoreCaseAndAmountAndTypeAndDateAndCreatedAtAfter(
            String description, BigDecimal amount, TransactionType type, LocalDate date, Instant createdAfter);
}
