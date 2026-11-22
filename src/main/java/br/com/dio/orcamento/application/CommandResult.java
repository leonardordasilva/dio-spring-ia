package br.com.dio.orcamento.application;

import java.util.List;

/**
 * Resultado de um comando processado pelo assistente.
 *
 * @param transcription texto entendido (transcrição do áudio ou o próprio texto enviado)
 * @param reply         resposta final gerada pela IA
 * @param toolsUsed     ferramentas executadas via Tool Calling
 * @param createdTransactionIds ids das transações criadas neste comando
 * @param audio         resposta em voz (mp3), quando solicitada
 */
public record CommandResult(String transcription, String reply, List<String> toolsUsed,
                            List<Long> createdTransactionIds, byte[] audio) {
}
