package br.com.dio.orcamento.web.dto;

import br.com.dio.orcamento.audit.CommandAudit;
import br.com.dio.orcamento.domain.TransactionSource;

import java.time.Instant;

public record AuditResponse(Long id, TransactionSource channel, String input, String response, String toolsUsed,
                            String createdTransactionIds, boolean success, String errorMessage, long durationMs,
                            Instant createdAt) {

    public static AuditResponse of(CommandAudit a) {
        return new AuditResponse(a.getId(), a.getChannel(), a.getInputText(), a.getResponseText(), a.getToolsUsed(),
                a.getCreatedTransactionIds(), a.isSuccess(), a.getErrorMessage(), a.getDurationMs(), a.getCreatedAt());
    }
}
