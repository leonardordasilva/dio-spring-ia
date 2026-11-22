package br.com.dio.orcamento.web;

import br.com.dio.orcamento.domain.Category;
import br.com.dio.orcamento.domain.Transaction;
import br.com.dio.orcamento.domain.TransactionType;
import br.com.dio.orcamento.repository.TransactionFilter;
import br.com.dio.orcamento.service.TransactionService;
import br.com.dio.orcamento.web.dto.PageResponse;
import br.com.dio.orcamento.web.dto.TransactionRequest;
import br.com.dio.orcamento.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

/** CRUD de transações, para uso direto (sem IA) e para conferir o que o assistente gravou. */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        Transaction saved = service.create(request.toCommand());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(TransactionResponse.of(saved));
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        TransactionFilter filter = new TransactionFilter(type, category, from, to, q);
        return PageResponse.of(service.search(filter, pageable), TransactionResponse::of);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id) {
        return TransactionResponse.of(service.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
