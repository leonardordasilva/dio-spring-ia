package br.com.dio.orcamento.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registro, por requisição, das ferramentas que o modelo decidiu executar.
 * Vai no {@code ToolContext} (não é enviado ao modelo) e alimenta a resposta e a auditoria.
 */
public final class ToolCallLog {

    private final List<String> toolNames = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> createdTransactionIds = Collections.synchronizedList(new ArrayList<>());

    public void tool(String name) {
        toolNames.add(name);
    }

    public void createdTransaction(Long id) {
        createdTransactionIds.add(id);
    }

    public List<String> toolNames() {
        return List.copyOf(toolNames);
    }

    public List<Long> createdTransactionIds() {
        return List.copyOf(createdTransactionIds);
    }
}
