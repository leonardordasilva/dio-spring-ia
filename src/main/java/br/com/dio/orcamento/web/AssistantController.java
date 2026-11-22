package br.com.dio.orcamento.web;

import br.com.dio.orcamento.application.AssistantCommandService;
import br.com.dio.orcamento.application.CommandResult;
import br.com.dio.orcamento.web.dto.CommandResponse;
import br.com.dio.orcamento.web.dto.TextCommandRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Entrada do assistente: comandos por voz (áudio) ou por texto. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final AssistantCommandService commandService;

    public AssistantController(AssistantCommandService commandService) {
        this.commandService = commandService;
    }

    /** Recebe o áudio e devolve JSON com transcrição e resposta (e o mp3 em Base64 se {@code includeAudio=true}). */
    @PostMapping(path = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CommandResponse voice(@RequestPart("audio") MultipartFile audio,
                                 @RequestParam(defaultValue = "false") boolean includeAudio) {
        return CommandResponse.of(commandService.handleVoice(audio, includeAudio));
    }

    /** Recebe o áudio e responde diretamente com o áudio da resposta (mp3). Metadados vão nos cabeçalhos. */
    @PostMapping(path = "/voice/speech", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "audio/mpeg")
    public ResponseEntity<byte[]> voiceToSpeech(@RequestPart("audio") MultipartFile audio) {
        CommandResult result = commandService.handleVoice(audio, true);
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .header("X-Transcription", encode(result.transcription()))
                .header("X-Reply", encode(result.reply()))
                .header("X-Tools-Used", String.join(",", result.toolsUsed()))
                .body(result.audio());
    }

    /** Mesmo fluxo, a partir de texto. Ideal para testar sem microfone. */
    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CommandResponse text(@Valid @RequestBody TextCommandRequest request,
                                @RequestParam(defaultValue = "false") boolean includeAudio) {
        return CommandResponse.of(commandService.handleText(request.text(), includeAudio));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
