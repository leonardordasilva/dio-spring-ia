package br.com.dio.orcamento.web;

import br.com.dio.orcamento.application.AssistantCommandService;
import br.com.dio.orcamento.application.CommandResult;
import br.com.dio.orcamento.application.InvalidAudioException;
import br.com.dio.orcamento.application.SpeechSynthesisException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistantController.class)
class AssistantControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AssistantCommandService commandService;

    private final MockMultipartFile audio = new MockMultipartFile("audio", "cmd.mp3", "audio/mpeg", new byte[]{1});

    @Test
    void voiceReturnsJson() throws Exception {
        when(commandService.handleVoice(any(), eq(false))).thenReturn(
                new CommandResult("gastei 10 no café", "Registrei R$ 10,00.", List.of("registrarTransacao"), List.of(1L), null));

        mvc.perform(multipart("/api/v1/assistant/voice").file(audio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcription").value("gastei 10 no café"))
                .andExpect(jsonPath("$.reply").value("Registrei R$ 10,00."))
                .andExpect(jsonPath("$.toolsUsed[0]").value("registrarTransacao"))
                .andExpect(jsonPath("$.audioBase64").doesNotExist());
    }

    @Test
    void voiceCanIncludeAudioAsBase64() throws Exception {
        when(commandService.handleVoice(any(), eq(true))).thenReturn(
                new CommandResult("oi", "Olá", List.of(), List.of(), new byte[]{1, 2, 3}));

        mvc.perform(multipart("/api/v1/assistant/voice").file(audio).param("includeAudio", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioBase64").value("AQID"));
    }

    @Test
    void speechEndpointReturnsMp3WithMetadataHeaders() throws Exception {
        when(commandService.handleVoice(any(), eq(true))).thenReturn(
                new CommandResult("qual meu saldo", "Seu saldo é R$ 5,00.", List.of("consultarSaldo"), List.of(), new byte[]{7, 7}));

        mvc.perform(multipart("/api/v1/assistant/voice/speech").file(audio))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(new byte[]{7, 7}))
                .andExpect(header().string("X-Transcription", "qual+meu+saldo"))
                .andExpect(header().string("X-Tools-Used", "consultarSaldo"));
    }

    @Test
    void missingAudioPartIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/v1/assistant/voice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Áudio inválido"));
    }

    @Test
    void invalidAudioIsBadRequest() throws Exception {
        when(commandService.handleVoice(any(), anyBoolean())).thenThrow(new InvalidAudioException("Formato de áudio não suportado"));

        mvc.perform(multipart("/api/v1/assistant/voice").file(audio))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Formato de áudio não suportado"));
    }

    @Test
    void textCommandValidatesBody() throws Exception {
        mvc.perform(post("/api/v1/assistant/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("text:")));
    }

    @Test
    void aiUnavailableMapsTo503() throws Exception {
        when(commandService.handleText(any(), anyBoolean())).thenThrow(new TransientAiException("rate limit"));

        mvc.perform(post("/api/v1/assistant/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"saldo\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void speechFailureStillReturnsReplyText() throws Exception {
        when(commandService.handleVoice(any(), eq(true)))
                .thenThrow(new SpeechSynthesisException("tts fora", "saldo", "Seu saldo é R$ 5,00."));

        mvc.perform(multipart("/api/v1/assistant/voice/speech").file(audio))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reply").value("Seu saldo é R$ 5,00."));
    }
}
