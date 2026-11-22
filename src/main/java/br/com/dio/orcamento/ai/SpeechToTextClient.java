package br.com.dio.orcamento.ai;

import org.springframework.core.io.Resource;

/** Porta de saída para transcrição de áudio. Isola o restante da aplicação do provedor de IA. */
public interface SpeechToTextClient {

    String transcribe(Resource audio);
}
