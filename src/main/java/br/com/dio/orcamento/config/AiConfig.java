package br.com.dio.orcamento.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * ChatClient compartilhado. O prompt de sistema e as ferramentas são definidos por requisição
     * no {@code BudgetAssistant}, o que mantém este bean reutilizável para outros assistentes.
     * O {@link SimpleLoggerAdvisor} registra requisição/resposta em nível DEBUG.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
