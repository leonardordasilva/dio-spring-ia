package br.com.dio.orcamento.application;

/**
 * O comando foi processado, mas a resposta em voz não pôde ser gerada.
 * Carrega o texto para que o cliente ainda receba a resposta.
 */
public class SpeechSynthesisException extends RuntimeException {

    private final String transcription;
    private final String reply;

    public SpeechSynthesisException(String message, String transcription, String reply) {
        super(message);
        this.transcription = transcription;
        this.reply = reply;
    }

    public String getTranscription() {
        return transcription;
    }

    public String getReply() {
        return reply;
    }
}
