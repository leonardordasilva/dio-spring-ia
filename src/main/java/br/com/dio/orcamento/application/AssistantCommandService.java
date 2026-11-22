package br.com.dio.orcamento.application;

import br.com.dio.orcamento.ai.AssistantReply;
import br.com.dio.orcamento.ai.BudgetAssistant;
import br.com.dio.orcamento.ai.SpeechToTextClient;
import br.com.dio.orcamento.ai.TextToSpeechClient;
import br.com.dio.orcamento.audit.AuditService;
import br.com.dio.orcamento.domain.TransactionSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Orquestra o fluxo principal: áudio → texto → IA (+ ferramentas) → resposta (texto e, opcionalmente, voz).
 * Não conhece detalhes do provedor de IA nem de HTTP além do arquivo recebido.
 */
@Service
public class AssistantCommandService {

    private final AudioValidator audioValidator;
    private final SpeechToTextClient speechToText;
    private final BudgetAssistant assistant;
    private final TextToSpeechClient textToSpeech;
    private final AuditService auditService;

    public AssistantCommandService(AudioValidator audioValidator, SpeechToTextClient speechToText,
                                   BudgetAssistant assistant, TextToSpeechClient textToSpeech,
                                   AuditService auditService) {
        this.audioValidator = audioValidator;
        this.speechToText = speechToText;
        this.assistant = assistant;
        this.textToSpeech = textToSpeech;
        this.auditService = auditService;
    }

    public CommandResult handleVoice(MultipartFile audio, boolean speakReply) {
        audioValidator.validate(audio);
        long start = System.currentTimeMillis();
        String transcription = null;
        try {
            transcription = speechToText.transcribe(toResource(audio));
            if (transcription.isBlank()) {
                throw new InvalidAudioException("Não foi possível entender o áudio. Tente falar mais perto do microfone.");
            }
            return process(transcription, TransactionSource.VOZ, speakReply, start);
        } catch (RuntimeException e) {
            if (transcription == null || transcription.isBlank()) {
                auditService.record(TransactionSource.VOZ, transcription, null, List.of(), List.of(), false,
                        e.getMessage(), elapsed(start));
            }
            throw e;
        }
    }

    public CommandResult handleText(String text, boolean speakReply) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("O texto do comando é obrigatório");
        }
        return process(text.trim(), TransactionSource.TEXTO, speakReply, System.currentTimeMillis());
    }

    private CommandResult process(String input, TransactionSource channel, boolean speakReply, long start) {
        AssistantReply reply;
        try {
            reply = assistant.ask(input, channel);
        } catch (RuntimeException e) {
            auditService.record(channel, input, null, List.of(), List.of(), false, e.getMessage(), elapsed(start));
            throw e;
        }
        byte[] audio = null;
        String speechError = null;
        if (speakReply) {
            try {
                audio = textToSpeech.synthesize(reply.text());
            } catch (RuntimeException e) {
                speechError = "Falha ao gerar voz: " + e.getMessage();
            }
        }
        // O comando foi executado (transações podem ter sido criadas), então é auditado como sucesso
        // mesmo que a síntese de voz falhe.
        auditService.record(channel, input, reply.text(), reply.toolsUsed(), reply.createdTransactionIds(),
                true, speechError, elapsed(start));
        if (speechError != null) {
            throw new SpeechSynthesisException(speechError, input, reply.text());
        }
        return new CommandResult(input, reply.text(), reply.toolsUsed(), reply.createdTransactionIds(), audio);
    }

    private static Resource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename();
            // O nome do arquivo é necessário: a API de transcrição usa a extensão para detectar o formato.
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (IOException e) {
            throw new InvalidAudioException("Não foi possível ler o arquivo de áudio");
        }
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
