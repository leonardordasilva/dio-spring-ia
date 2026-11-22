package br.com.dio.orcamento.web.dto;

import br.com.dio.orcamento.application.CommandResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Base64;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandResponse(String transcription, String reply, List<String> toolsUsed,
                              List<Long> createdTransactionIds, String audioBase64) {

    public static CommandResponse of(CommandResult r) {
        String audio = r.audio() == null ? null : Base64.getEncoder().encodeToString(r.audio());
        return new CommandResponse(r.transcription(), r.reply(), r.toolsUsed(), r.createdTransactionIds(), audio);
    }
}
