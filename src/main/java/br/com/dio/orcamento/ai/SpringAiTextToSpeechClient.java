package br.com.dio.orcamento.ai;

import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.stereotype.Component;

/** Adaptador que usa o {@link TextToSpeechModel} do Spring AI (TTS da OpenAI). */
@Component
class SpringAiTextToSpeechClient implements TextToSpeechClient {

    private final TextToSpeechModel textToSpeechModel;

    SpringAiTextToSpeechClient(TextToSpeechModel textToSpeechModel) {
        this.textToSpeechModel = textToSpeechModel;
    }

    @Override
    public byte[] synthesize(String text) {
        return textToSpeechModel.call(text);
    }
}
