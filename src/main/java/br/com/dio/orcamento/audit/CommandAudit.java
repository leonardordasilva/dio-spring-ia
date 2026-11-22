package br.com.dio.orcamento.audit;

import br.com.dio.orcamento.domain.TransactionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Trilha de auditoria de cada comando enviado ao assistente: o que foi dito, o que a IA respondeu,
 * quais ferramentas executou, quanto tempo levou e se houve erro.
 */
@Entity
@Table(name = "command_audits")
public class CommandAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionSource channel;

    @Column(name = "input_text", length = 4000)
    private String inputText;

    @Column(name = "response_text", length = 4000)
    private String responseText;

    @Column(name = "tools_used", length = 1000)
    private String toolsUsed;

    @Column(name = "created_transaction_ids", length = 500)
    private String createdTransactionIds;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommandAudit() {
    }

    public CommandAudit(TransactionSource channel, String inputText, String responseText, String toolsUsed,
                        String createdTransactionIds, boolean success, String errorMessage, long durationMs) {
        this.channel = channel;
        this.inputText = truncate(inputText, 4000);
        this.responseText = truncate(responseText, 4000);
        this.toolsUsed = truncate(toolsUsed, 1000);
        this.createdTransactionIds = truncate(createdTransactionIds, 500);
        this.success = success;
        this.errorMessage = truncate(errorMessage, 1000);
        this.durationMs = durationMs;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() { return id; }
    public TransactionSource getChannel() { return channel; }
    public String getInputText() { return inputText; }
    public String getResponseText() { return responseText; }
    public String getToolsUsed() { return toolsUsed; }
    public String getCreatedTransactionIds() { return createdTransactionIds; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }
}
