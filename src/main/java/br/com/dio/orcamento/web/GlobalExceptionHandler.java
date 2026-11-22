package br.com.dio.orcamento.web;

import br.com.dio.orcamento.application.InvalidAudioException;
import br.com.dio.orcamento.application.SpeechSynthesisException;
import br.com.dio.orcamento.service.ResourceNotFoundException;
import br.com.dio.orcamento.service.TransactionValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.net.URI;
import java.util.List;

/** Converte exceções em respostas RFC 9457 (Problem Details) consistentes. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://orcamento-voz.dio/problems/";

    @ExceptionHandler(TransactionValidationException.class)
    ProblemDetail handleBusinessValidation(TransactionValidationException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "transacao-invalida", "Transação inválida",
                "A transação viola regras de negócio");
        pd.setProperty("errors", e.getErrors());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "requisicao-invalida", "Requisição inválida",
                "Um ou mais campos são inválidos");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "parametro-invalido", "Parâmetro inválido",
                "Valor inválido para o parâmetro '%s'".formatted(e.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "requisicao-invalida", "Requisição inválida", e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "nao-encontrado", "Recurso não encontrado", e.getMessage());
    }

    @ExceptionHandler({InvalidAudioException.class, MissingServletRequestPartException.class})
    ProblemDetail handleInvalidAudio(Exception e) {
        String detail = e instanceof MissingServletRequestPartException
                ? "Envie um arquivo de áudio no campo 'audio' (multipart/form-data)"
                : e.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "audio-invalido", "Áudio inválido", detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "audio-muito-grande", "Áudio muito grande",
                "O arquivo excede o tamanho máximo permitido");
    }

    @ExceptionHandler(SpeechSynthesisException.class)
    ProblemDetail handleSpeech(SpeechSynthesisException e) {
        log.warn("Comando processado, mas a voz não foi gerada: {}", e.getMessage());
        ProblemDetail pd = problem(HttpStatus.BAD_GATEWAY, "voz-indisponivel", "Resposta em voz indisponível",
                "O comando foi processado, mas não foi possível gerar o áudio da resposta");
        pd.setProperty("transcription", e.getTranscription());
        pd.setProperty("reply", e.getReply());
        return pd;
    }

    @ExceptionHandler(TransientAiException.class)
    ProblemDetail handleTransientAi(TransientAiException e) {
        log.warn("Falha temporária no provedor de IA", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "ia-indisponivel", "Serviço de IA indisponível",
                "O provedor de IA está instável. Tente novamente em instantes.");
    }

    @ExceptionHandler({NonTransientAiException.class, RestClientException.class})
    ProblemDetail handleAiFailure(RuntimeException e) {
        log.error("Erro ao chamar o provedor de IA", e);
        return problem(HttpStatus.BAD_GATEWAY, "erro-ia", "Erro no provedor de IA",
                "Não foi possível processar o comando com o provedor de IA");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + type));
        pd.setTitle(title);
        return pd;
    }
}
