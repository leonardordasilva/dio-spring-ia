package br.com.dio.orcamento.web;

import br.com.dio.orcamento.service.ResourceNotFoundException;
import br.com.dio.orcamento.service.TransactionService;
import br.com.dio.orcamento.service.TransactionValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TransactionService service;

    @Test
    void invalidPayloadIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"description":"","amount":-5,"type":"DESPESA"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4));
        verifyNoInteractions(service);
    }

    @Test
    void unknownEnumIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("type", "XPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro 'type'"));
    }

    @Test
    void businessRuleViolationIs422() throws Exception {
        when(service.create(any())).thenThrow(new TransactionValidationException(List.of("A categoria SALARIO não é compatível com o tipo DESPESA")));

        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"description":"Salário","amount":100,"type":"DESPESA","category":"SALARIO"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("A categoria SALARIO não é compatível com o tipo DESPESA"));
    }

    @Test
    void notFoundIs404() throws Exception {
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Transação 99 não encontrada"));

        mvc.perform(get("/api/v1/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Transação 99 não encontrada"));
    }
}
