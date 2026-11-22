package br.com.dio.orcamento.ai;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.Transaction;
import br.com.dio.orcamento.domain.TransactionSource;
import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.repository.TransactionFilter;
import br.com.dio.orcamento.service.FinancialReportService;
import br.com.dio.orcamento.service.FinancialReportService.Balance;
import br.com.dio.orcamento.service.FinancialReportService.CategoryBreakdown;
import br.com.dio.orcamento.service.FinancialReportService.MonthlySummary;
import br.com.dio.orcamento.service.NewTransaction;
import br.com.dio.orcamento.service.TransactionService;
import br.com.dio.orcamento.service.TransactionValidationException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Funções reais da aplicação expostas ao modelo via Tool Calling.
 * <p>
 * Esta classe é só um adaptador: converte parâmetros vindos do modelo e delega para os serviços,
 * onde ficam as regras de negócio. Erros de validação viram um resultado estruturado
 * para que o modelo explique o problema à pessoa usuária em vez de inventar um sucesso.
 */
@Component
public class BudgetTools {

    public static final String CTX_CHANNEL = "channel";
    public static final String CTX_LOG = "toolCallLog";

    private static final int DEFAULT_LIST_LIMIT = 10;

    private final TransactionService transactionService;
    private final FinancialReportService reportService;

    public BudgetTools(TransactionService transactionService, FinancialReportService reportService) {
        this.transactionService = transactionService;
        this.reportService = reportService;
    }

    @Tool(name = "registrarTransacao",
            description = "Registra uma nova receita ou despesa. Use somente quando a pessoa pedir explicitamente para lançar, registrar ou anotar um valor.")
    public CreateResult registerTransaction(
            @ToolParam(description = "Descrição curta, ex.: 'Almoço no restaurante'") String descricao,
            @ToolParam(description = "Valor positivo em reais, com ponto como separador decimal, ex.: 45.90") BigDecimal valor,
            @ToolParam(description = "RECEITA para entradas, DESPESA para gastos") TransactionType tipo,
            @ToolParam(description = "Categoria que melhor se encaixa") Category categoria,
            @ToolParam(description = "Data no formato AAAA-MM-DD. Omita para usar a data de hoje", required = false) String data,
            ToolContext toolContext) {
        log(toolContext, "registrarTransacao");
        try {
            NewTransaction command = new NewTransaction(descricao, valor, tipo, categoria, parseDate(data, "data"),
                    channel(toolContext));
            Transaction saved = transactionService.create(command);
            ToolCallLog callLog = callLog(toolContext);
            if (callLog != null) {
                callLog.createdTransaction(saved.getId());
            }
            return CreateResult.ok(TransactionView.of(saved));
        } catch (TransactionValidationException e) {
            return CreateResult.rejected(e.getErrors());
        } catch (IllegalArgumentException e) {
            return CreateResult.rejected(List.of(e.getMessage()));
        }
    }

    @Tool(name = "consultarSaldo",
            description = "Calcula total de receitas, total de despesas e saldo em um período. Sem datas, usa o mês atual.")
    public Balance balance(
            @ToolParam(description = "Data inicial AAAA-MM-DD", required = false) String dataInicial,
            @ToolParam(description = "Data final AAAA-MM-DD", required = false) String dataFinal,
            ToolContext toolContext) {
        log(toolContext, "consultarSaldo");
        return reportService.balance(parseDate(dataInicial, "dataInicial"), parseDate(dataFinal, "dataFinal"));
    }

    @Tool(name = "totaisPorCategoria",
            description = "Soma os valores por categoria em um período, com percentual de cada uma. Útil para 'onde estou gastando mais?'.")
    public CategoryBreakdown totalsByCategory(
            @ToolParam(description = "RECEITA ou DESPESA. Padrão: DESPESA", required = false) TransactionType tipo,
            @ToolParam(description = "Data inicial AAAA-MM-DD", required = false) String dataInicial,
            @ToolParam(description = "Data final AAAA-MM-DD", required = false) String dataFinal,
            ToolContext toolContext) {
        log(toolContext, "totaisPorCategoria");
        return reportService.byCategory(tipo, parseDate(dataInicial, "dataInicial"), parseDate(dataFinal, "dataFinal"));
    }

    @Tool(name = "resumoMensal",
            description = "Resumo de um mês: receitas, despesas, saldo e gastos por categoria.")
    public MonthlySummary monthlySummary(
            @ToolParam(description = "Mês no formato AAAA-MM. Omita para o mês atual", required = false) String mes,
            ToolContext toolContext) {
        log(toolContext, "resumoMensal");
        return reportService.monthly(parseMonth(mes));
    }

    @Tool(name = "listarTransacoes",
            description = "Lista as transações mais recentes, com filtros opcionais por tipo, categoria, período ou texto da descrição.")
    public List<TransactionView> listTransactions(
            @ToolParam(description = "RECEITA ou DESPESA", required = false) TransactionType tipo,
            @ToolParam(description = "Categoria", required = false) Category categoria,
            @ToolParam(description = "Data inicial AAAA-MM-DD", required = false) String dataInicial,
            @ToolParam(description = "Data final AAAA-MM-DD", required = false) String dataFinal,
            @ToolParam(description = "Trecho da descrição, ex.: 'uber'", required = false) String texto,
            @ToolParam(description = "Quantidade máxima (1 a 50). Padrão: 10", required = false) Integer limite,
            ToolContext toolContext) {
        log(toolContext, "listarTransacoes");
        TransactionFilter filter = new TransactionFilter(tipo, categoria,
                parseDate(dataInicial, "dataInicial"), parseDate(dataFinal, "dataFinal"), texto);
        int size = limite == null ? DEFAULT_LIST_LIMIT : Math.min(Math.max(limite, 1), 50);
        PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
        return transactionService.search(filter, page).map(TransactionView::of).getContent();
    }

    @Tool(name = "maioresGastos",
            description = "Retorna as maiores despesas (ou receitas) de um período, ordenadas pelo valor.")
    public List<TransactionView> largest(
            @ToolParam(description = "RECEITA ou DESPESA. Padrão: DESPESA", required = false) TransactionType tipo,
            @ToolParam(description = "Data inicial AAAA-MM-DD", required = false) String dataInicial,
            @ToolParam(description = "Data final AAAA-MM-DD", required = false) String dataFinal,
            @ToolParam(description = "Quantidade (1 a 50). Padrão: 5", required = false) Integer limite,
            ToolContext toolContext) {
        log(toolContext, "maioresGastos");
        return reportService.largest(tipo, parseDate(dataInicial, "dataInicial"), parseDate(dataFinal, "dataFinal"),
                        limite == null ? 5 : limite)
                .stream().map(TransactionView::of).toList();
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Parâmetro '%s' inválido: use o formato AAAA-MM-DD".formatted(field));
        }
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Parâmetro 'mes' inválido: use o formato AAAA-MM");
        }
    }

    private static TransactionSource channel(ToolContext ctx) {
        if (ctx != null && ctx.getContext().get(CTX_CHANNEL) instanceof TransactionSource source) {
            return source;
        }
        return TransactionSource.TEXTO;
    }

    private static ToolCallLog callLog(ToolContext ctx) {
        if (ctx != null && ctx.getContext().get(CTX_LOG) instanceof ToolCallLog log) {
            return log;
        }
        return null;
    }

    private static void log(ToolContext ctx, String tool) {
        ToolCallLog callLog = callLog(ctx);
        if (callLog != null) {
            callLog.tool(tool);
        }
    }

    /** Visão enxuta da transação devolvida ao modelo (evita expor a entidade JPA). */
    public record TransactionView(Long id, String descricao, BigDecimal valor, TransactionType tipo,
                                  Category categoria, LocalDate data) {
        static TransactionView of(Transaction t) {
            return new TransactionView(t.getId(), t.getDescription(), t.getAmount(), t.getType(),
                    t.getCategory(), t.getDate());
        }
    }

    /** Resultado do registro: sucesso com a transação salva ou rejeição com os motivos. */
    public record CreateResult(boolean sucesso, TransactionView transacao, List<String> erros) {
        static CreateResult ok(TransactionView view) {
            return new CreateResult(true, view, List.of());
        }

        static CreateResult rejected(List<String> errors) {
            return new CreateResult(false, null, errors);
        }
    }
}
