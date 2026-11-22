package br.com.dio.orcamento.ai;

import br.com.dio.orcamento.domain.TransactionSource;
import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.repository.TransactionRepository;
import br.com.dio.orcamento.service.FinancialReportService;
import br.com.dio.orcamento.service.TransactionService;
import br.com.dio.orcamento.service.TransactionValidator;
import br.com.dio.orcamento.support.FakeModel;
import br.com.dio.orcamento.support.TestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercita ChatClient + ferramentas reais + banco H2, com o modelo simulado por {@link FakeModel}.
 */
@DataJpaTest
@Import({TestClockConfig.class, TransactionService.class, TransactionValidator.class,
        FinancialReportService.class, BudgetTools.class})
class BudgetAssistantTest {

    @Autowired
    BudgetTools tools;
    @Autowired
    TransactionRepository repository;
    @Autowired
    Clock clock;

    private BudgetAssistant assistantWith(FakeModel fake) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(fake.answer());
        return new BudgetAssistant(ChatClient.builder(chatModel).build(), tools,
                new ClassPathResource("prompts/budget-system.st"), clock);
    }

    @Test
    void registersExpenseThroughToolCalling() {
        FakeModel fake = FakeModel.create()
                .callsTool("registrarTransacao", """
                        {"descricao":"Almoço","valor":45.90,"tipo":"DESPESA","categoria":"ALIMENTACAO"}""")
                .replies("Pronto! Registrei R$ 45,90 em alimentação.");

        AssistantReply reply = assistantWith(fake).ask("gastei 45 e 90 no almoço", TransactionSource.VOZ);

        assertThat(reply.text()).isEqualTo("Pronto! Registrei R$ 45,90 em alimentação.");
        assertThat(reply.toolsUsed()).containsExactly("registrarTransacao");
        assertThat(reply.createdTransactionIds()).hasSize(1);
        var saved = repository.findById(reply.createdTransactionIds().getFirst()).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo("45.90");
        assertThat(saved.getType()).isEqualTo(TransactionType.DESPESA);
        assertThat(saved.getSource()).isEqualTo(TransactionSource.VOZ);
        assertThat(saved.getDate()).isEqualTo(TestClockConfig.TODAY);
    }

    @Test
    void invalidTransactionIsReturnedToModelInsteadOfSaved() {
        FakeModel fake = FakeModel.create()
                .callsTool("registrarTransacao", """
                        {"descricao":"Salário","valor":100,"tipo":"DESPESA","categoria":"SALARIO"}""")
                .replies("Não consegui registrar: salário só pode ser receita.");

        AssistantReply reply = assistantWith(fake).ask("paguei 100 de salário", TransactionSource.TEXTO);

        assertThat(reply.createdTransactionIds()).isEmpty();
        assertThat(repository.count()).isZero();
        assertThat(fake.toolResults().getFirst()).contains("\"sucesso\":false").contains("não é compatível");
    }

    @Test
    void sendsSystemPromptWithDateAndRegistersAllTools() {
        FakeModel fake = FakeModel.create().replies("Seu saldo é zero.");

        assistantWith(fake).ask("qual meu saldo?", TransactionSource.TEXTO);

        Prompt prompt = fake.lastPrompt();
        assertThat(prompt.getSystemMessage().getText()).contains("2026-11-22").contains("domingo");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("qual meu saldo?");
        var toolNames = ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks().stream()
                .map(cb -> cb.getToolDefinition().name()).toList();
        assertThat(toolNames).containsExactlyInAnyOrder("registrarTransacao", "consultarSaldo",
                "totaisPorCategoria", "resumoMensal", "listarTransacoes", "maioresGastos");
    }

    @Test
    void queryToolsReadFromDatabase() {
        FakeModel fake = FakeModel.create()
                .callsTool("registrarTransacao", """
                        {"descricao":"Freela site","valor":800,"tipo":"RECEITA","categoria":"FREELANCE","data":"2026-11-20"}""")
                .callsTool("consultarSaldo", "{}")
                .callsTool("listarTransacoes", """
                        {"texto":"freela"}""")
                .replies("Seu saldo de novembro é R$ 800,00.");

        AssistantReply reply = assistantWith(fake).ask("recebi 800 de freela e qual meu saldo", TransactionSource.TEXTO);

        assertThat(reply.toolsUsed()).containsExactly("registrarTransacao", "consultarSaldo", "listarTransacoes");
        assertThat(fake.toolResults().get(1)).contains("\"balance\":800.00");
        assertThat(fake.toolResults().get(2)).contains("Freela site");
    }
}
