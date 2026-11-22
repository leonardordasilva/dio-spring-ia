package br.com.dio.orcamento.ai;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Adaptador que usa o {@link TranscriptionModel} do Spring AI (Whisper na OpenAI). */
@Component
class SpringAiSpeechToTextClient implements SpeechToTextClient {

    private final TranscriptionModel transcriptionModel;

    SpringAiSpeechToTextClient(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @Override
    public String transcribe(Resource audio) {
        String text = transcriptionModel.transcribe(audio);
        return text == null ? "" : text.trim();
    }
}
