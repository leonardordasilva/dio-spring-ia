package br.com.dio.orcamento.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Regras de negócio configuráveis em {@code application.yml} (prefixo {@code budget}).
 *
 * @param maxAmount          maior valor aceito para uma única transação
 * @param maxPastYears       quantos anos para trás uma transação pode ser lançada
 * @param duplicateWindow    janela em que uma transação idêntica é considerada duplicada
 *                           (evita lançar duas vezes o mesmo comando de voz)
 * @param audio              restrições do arquivo de áudio enviado
 */
@ConfigurationProperties(prefix = "budget")
public record BudgetProperties(
        @DefaultValue("1000000.00") BigDecimal maxAmount,
        @DefaultValue("5") int maxPastYears,
        @DefaultValue("2m") Duration duplicateWindow,
        @DefaultValue Audio audio) {

    /**
     * @param maxSize           tamanho máximo do arquivo
     * @param allowedExtensions extensões aceitas (formatos suportados pelo Whisper)
     */
    public record Audio(
            @DefaultValue("10MB") DataSize maxSize,
            @DefaultValue({"mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm", "ogg", "flac"}) List<String> allowedExtensions) {
    }
}
