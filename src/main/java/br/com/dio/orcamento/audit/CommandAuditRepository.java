package br.com.dio.orcamento.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandAuditRepository extends JpaRepository<CommandAudit, Long> {
}
