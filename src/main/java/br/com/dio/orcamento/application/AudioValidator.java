package br.com.dio.orcamento.application;

import br.com.dio.orcamento.config.BudgetProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/** Valida o arquivo antes de gastar uma chamada (paga) ao modelo de transcrição. */
@Component
public class AudioValidator {

    private final BudgetProperties.Audio rules;

    public AudioValidator(BudgetProperties properties) {
        this.rules = properties.audio();
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAudioException("Envie um arquivo de áudio no campo 'audio'");
        }
        if (file.getSize() > rules.maxSize().toBytes()) {
            throw new InvalidAudioException("O áudio excede o tamanho máximo de %d MB"
                    .formatted(rules.maxSize().toMegabytes()));
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !rules.allowedExtensions().contains(extension.toLowerCase(Locale.ROOT))) {
            throw new InvalidAudioException("Formato de áudio não suportado. Use: "
                    + String.join(", ", rules.allowedExtensions()));
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("audio/") && !contentType.startsWith("video/")
                && !contentType.equals("application/octet-stream")) {
            throw new InvalidAudioException("Tipo de conteúdo inválido: " + contentType);
        }
    }
}
