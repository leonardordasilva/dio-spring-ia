package br.com.dio.orcamento;

import br.com.dio.orcamento.ai.SpeechToTextClient;
import br.com.dio.orcamento.ai.TextToSpeechClient;
import br.com.dio.orcamento.support.FakeModel;
import br.com.dio.orcamento.support.TestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo com a aplicação inteira de pé: upload de áudio → transcrição → ChatClient → Tool Calling
 * → banco → voz → auditoria. Apenas as chamadas externas à OpenAI são substituídas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class VoiceCommandFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChatModel chatModel;
    @MockitoBean
    SpeechToTextClient speechToText;
    @MockitoBean
    TextToSpeechClient textToSpeech;

    @Test
    void voiceCommandCreatesTransactionAndIsAudited() throws Exception {
        when(speechToText.transcribe(any(Resource.class))).thenReturn("gastei 32 reais de uber ontem");
        FakeModel fake = FakeModel.create()
                .callsTool("registrarTransacao", """
                        {"descricao":"Uber","valor":32,"tipo":"DESPESA","categoria":"TRANSPORTE","data":"2026-11-21"}""")
                .replies("Anotado: R$ 32,00 de Uber em transporte, ontem.");
        when(chatModel.call(any(Prompt.class))).thenAnswer(fake.answer());
        when(textToSpeech.synthesize(anyString())).thenReturn(new byte[]{42});

        var audio = new MockMultipartFile("audio", "comando.webm", "audio/webm", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/v1/assistant/voice/speech").file(audio))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{42}));

        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Uber"))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-21"))
                .andExpect(jsonPath("$.content[0].source").value("VOZ"));

        mvc.perform(get("/api/v1/reports/balance"))
                .andExpect(jsonPath("$.expenses").value(32.00));

        mvc.perform(get("/api/v1/audits"))
                .andExpect(jsonPath("$.content[0].channel").value("VOZ"))
                .andExpect(jsonPath("$.content[0].input").value("gastei 32 reais de uber ontem"))
                .andExpect(jsonPath("$.content[0].toolsUsed").value("registrarTransacao"))
                .andExpect(jsonPath("$.content[0].success").value(true));
    }
}
