package br.com.dio.orcamento.web;

import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.service.FinancialReportService;
import br.com.dio.orcamento.service.FinancialReportService.Balance;
import br.com.dio.orcamento.service.FinancialReportService.CategoryBreakdown;
import br.com.dio.orcamento.service.FinancialReportService.MonthlySummary;
import br.com.dio.orcamento.web.dto.TransactionResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Consultas financeiras agregadas (as mesmas que o assistente usa via Tool Calling). */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final FinancialReportService reports;

    public ReportController(FinancialReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/balance")
    public Balance balance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.balance(from, to);
    }

    @GetMapping("/by-category")
    public CategoryBreakdown byCategory(@RequestParam(required = false) TransactionType type,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.byCategory(type, from, to);
    }

    @GetMapping("/monthly")
    public MonthlySummary monthly(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return reports.monthly(month);
    }

    @GetMapping("/top")
    public List<TransactionResponse> top(@RequestParam(required = false) TransactionType type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "5") int limit) {
        return reports.largest(type, from, to, limit).stream().map(TransactionResponse::of).toList();
    }
}
