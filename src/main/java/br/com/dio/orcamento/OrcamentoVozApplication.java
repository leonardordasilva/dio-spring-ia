package br.com.dio.orcamento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrcamentoVozApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrcamentoVozApplication.class, args);
    }
}
