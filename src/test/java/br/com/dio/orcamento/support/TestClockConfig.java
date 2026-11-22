package br.com.dio.orcamento.support;

import br.com.dio.orcamento.config.BudgetProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Relógio fixo em 22/11/2026 para testes determinísticos. */
@TestConfiguration
@EnableConfigurationProperties(BudgetProperties.class)
public class TestClockConfig {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-22T15:00:00Z"), ZONE);
    }
}
