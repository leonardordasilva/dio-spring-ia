package br.com.dio.orcamento.ai;

/** Porta de saída para geração de voz a partir de texto. */
public interface TextToSpeechClient {

    byte[] synthesize(String text);
}
