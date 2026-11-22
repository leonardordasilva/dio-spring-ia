package br.com.dio.orcamento.ai;

import java.util.List;

/** Resposta final do assistente e o que foi executado para chegar nela. */
public record AssistantReply(String text, List<String> toolsUsed, List<Long> createdTransactionIds) {
}
