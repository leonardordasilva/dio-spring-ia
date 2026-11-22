package br.com.dio.orcamento.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

/**
 * Simula o "loop" de Tool Calling de um modelo real sem chamar a API:
 * executa as ferramentas roteirizadas (com os mesmos callbacks e ToolContext que o Spring AI montou)
 * e devolve um texto final. Assim os testes exercitam a ligação real entre ChatClient e ferramentas.
 */
public final class FakeModel {

    public record PlannedCall(String tool, String jsonArguments) {
    }

    private final List<PlannedCall> calls = new ArrayList<>();
    private final List<String> toolResults = new ArrayList<>();
    private final List<Prompt> prompts = new ArrayList<>();
    private String finalText = "ok";

    public static FakeModel create() {
        return new FakeModel();
    }

    public FakeModel callsTool(String tool, String jsonArguments) {
        calls.add(new PlannedCall(tool, jsonArguments));
        return this;
    }

    public FakeModel replies(String text) {
        this.finalText = text;
        return this;
    }

    public Answer<ChatResponse> answer() {
        return invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt);
            ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
            for (PlannedCall call : calls) {
                ToolCallback callback = options.getToolCallbacks().stream()
                        .filter(cb -> cb.getToolDefinition().name().equals(call.tool()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Ferramenta não registrada: " + call.tool()));
                toolResults.add(callback.call(call.jsonArguments(), new ToolContext(options.getToolContext())));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(finalText))));
        };
    }

    public List<String> toolResults() {
        return toolResults;
    }

    public Prompt lastPrompt() {
        return prompts.getLast();
    }
}
