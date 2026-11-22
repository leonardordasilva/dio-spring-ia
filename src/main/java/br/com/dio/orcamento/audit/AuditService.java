package br.com.dio.orcamento.audit;

import br.com.dio.orcamento.domain.TransactionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final CommandAuditRepository repository;

    public AuditService(CommandAuditRepository repository) {
        this.repository = repository;
    }

    /** Grava em transação própria: a auditoria não pode ser perdida se o fluxo principal falhar. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TransactionSource channel, String input, String response, List<String> tools,
                       List<Long> createdIds, boolean success, String error, long durationMs) {
        try {
            repository.save(new CommandAudit(channel, input, response, join(tools), join(createdIds),
                    success, error, durationMs));
        } catch (RuntimeException e) {
            log.warn("Falha ao gravar auditoria do comando", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<CommandAudit> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    private static String join(List<?> values) {
        return values == null || values.isEmpty() ? null
                : values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
