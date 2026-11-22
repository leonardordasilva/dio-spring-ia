package br.com.dio.orcamento.application;

import br.com.dio.orcamento.ai.AssistantReply;
import br.com.dio.orcamento.ai.BudgetAssistant;
import br.com.dio.orcamento.ai.SpeechToTextClient;
import br.com.dio.orcamento.ai.TextToSpeechClient;
import br.com.dio.orcamento.audit.AuditService;
import br.com.dio.orcamento.config.BudgetProperties;
import br.com.dio.orcamento.domain.TransactionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantCommandServiceTest {

    private SpeechToTextClient stt;
    private TextToSpeechClient tts;
    private BudgetAssistant assistant;
    private AuditService audit;
    private AssistantCommandService service;

    private final MockMultipartFile audio = new MockMultipartFile("audio", "comando.mp3", "audio/mpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        stt = mock(SpeechToTextClient.class);
        tts = mock(TextToSpeechClient.class);
        assistant = mock(BudgetAssistant.class);
        audit = mock(AuditService.class);
        var props = new BudgetProperties(BigDecimal.TEN, 5, Duration.ofMinutes(2),
                new BudgetProperties.Audio(DataSize.ofKilobytes(1), List.of("mp3", "wav")));
        service = new AssistantCommandService(new AudioValidator(props), stt, assistant, tts, audit);
    }

    @Test
    void voiceFlowTranscribesAsksSpeaksAndAudits() {
        when(stt.transcribe(any(Resource.class))).thenReturn("gastei 20 reais de uber");
        when(assistant.ask("gastei 20 reais de uber", TransactionSource.VOZ))
                .thenReturn(new AssistantReply("Registrei R$ 20,00 em transporte.", List.of("registrarTransacao"), List.of(7L)));
        when(tts.synthesize("Registrei R$ 20,00 em transporte.")).thenReturn(new byte[]{9});

        CommandResult result = service.handleVoice(audio, true);

        assertThat(result.transcription()).isEqualTo("gastei 20 reais de uber");
        assertThat(result.reply()).isEqualTo("Registrei R$ 20,00 em transporte.");
        assertThat(result.audio()).containsExactly(9);
        assertThat(result.createdTransactionIds()).containsExactly(7L);
        verify(audit).record(eq(TransactionSource.VOZ), eq("gastei 20 reais de uber"), eq("Registrei R$ 20,00 em transporte."),
                eq(List.of("registrarTransacao")), eq(List.of(7L)), eq(true), isNull(), anyLong());
    }

    @Test
    void sendsFilenameToTranscription() {
        when(stt.transcribe(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            assertThat(r.getFilename()).isEqualTo("comando.mp3");
            return "oi";
        });
        when(assistant.ask(anyString(), any())).thenReturn(new AssistantReply("Olá", List.of(), List.of()));

        service.handleVoice(audio, false);

        verifyNoInteractions(tts);
    }

    @Test
    void rejectsInvalidAudioBeforeCallingAi() {
        var wrongFormat = new MockMultipartFile("audio", "nota.txt", "text/plain", new byte[]{1});
        var empty = new MockMultipartFile("audio", "a.mp3", "audio/mpeg", new byte[0]);
        var tooBig = new MockMultipartFile("audio", "a.mp3", "audio/mpeg", new byte[2048]);

        assertThatThrownBy(() -> service.handleVoice(wrongFormat, false)).isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Formato");
        assertThatThrownBy(() -> service.handleVoice(empty, false)).isInstanceOf(InvalidAudioException.class);
        assertThatThrownBy(() -> service.handleVoice(tooBig, false)).isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("tamanho máximo");
        verifyNoInteractions(stt, assistant);
    }

    @Test
    void blankTranscriptionIsAuditedAsFailure() {
        when(stt.transcribe(any(Resource.class))).thenReturn("  ");

        assertThatThrownBy(() -> service.handleVoice(audio, false)).isInstanceOf(InvalidAudioException.class);
        verify(audit).record(eq(TransactionSource.VOZ), anyString(), isNull(), eq(List.of()), eq(List.of()),
                eq(false), anyString(), anyLong());
        verifyNoInteractions(assistant);
    }

    @Test
    void aiFailureIsAuditedAndPropagated() {
        when(assistant.ask(anyString(), any())).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.handleText("qual meu saldo", false)).hasMessage("timeout");
        verify(audit).record(eq(TransactionSource.TEXTO), eq("qual meu saldo"), isNull(), any(), any(),
                eq(false), eq("timeout"), anyLong());
    }

    @Test
    void speechFailureKeepsReplyAndAuditsCommandAsExecuted() {
        when(assistant.ask(anyString(), any())).thenReturn(new AssistantReply("Saldo: R$ 10,00", List.of("consultarSaldo"), List.of()));
        when(tts.synthesize(anyString())).thenThrow(new IllegalStateException("tts fora"));

        assertThatThrownBy(() -> service.handleText("saldo", true))
                .isInstanceOf(SpeechSynthesisException.class)
                .extracting(e -> ((SpeechSynthesisException) e).getReply())
                .isEqualTo("Saldo: R$ 10,00");
        verify(audit).record(eq(TransactionSource.TEXTO), eq("saldo"), eq("Saldo: R$ 10,00"), any(), any(),
                eq(true), anyString(), anyLong());
    }

    @Test
    void textFlowRequiresText() {
        assertThatThrownBy(() -> service.handleText(" ", false)).isInstanceOf(IllegalArgumentException.class);
        verify(assistant, never()).ask(anyString(), any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), anyBoolean(), any(), anyLong());
    }
}
