package br.com.dio.orcamento.ai;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.TransactionSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assistente financeiro: envia o comando ao modelo com as ferramentas disponíveis e devolve a resposta final.
 * O modelo decide qual ferramenta chamar; o Spring AI executa a chamada e devolve o resultado ao modelo.
 */
@Service
public class BudgetAssistant {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final ChatClient chatClient;
    private final BudgetTools tools;
    private final Resource systemPrompt;
    private final Clock clock;

    public BudgetAssistant(ChatClient chatClient, BudgetTools tools,
                           @Value("classpath:prompts/budget-system.st") Resource systemPrompt,
                           Clock clock) {
        this.chatClient = chatClient;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.clock = clock;
    }

    public AssistantReply ask(String userMessage, TransactionSource channel) {
        ToolCallLog callLog = new ToolCallLog();
        LocalDate today = LocalDate.now(clock);

        String content = chatClient.prompt()
                .system(s -> s.text(systemPrompt)
                        .param("today", today.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .param("weekday", today.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR))
                        .param("categories", categoriesDescription()))
                .user(userMessage)
                .tools(tools)
                .toolContext(Map.of(BudgetTools.CTX_CHANNEL, channel, BudgetTools.CTX_LOG, callLog))
                .call()
                .content();

        String text = content == null || content.isBlank()
                ? "Desculpe, não consegui gerar uma resposta. Pode repetir o comando?"
                : content.trim();
        return new AssistantReply(text, callLog.toolNames(), callLog.createdTransactionIds());
    }

    private static String categoriesDescription() {
        return Arrays.stream(Category.values())
                .map(c -> c.name() + " (" + c.label() + ")")
                .collect(Collectors.joining(", "));
    }
}
