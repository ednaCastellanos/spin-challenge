package mx.spin.transactions.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import mx.spin.transactions.adapter.in.rest.dto.*;
import mx.spin.transactions.adapter.in.rest.mapper.TransactionRestMapper;
import mx.spin.transactions.application.port.in.ExecuteTransactionUseCase;
import mx.spin.transactions.application.port.in.SearchTransactionsUseCase;
import mx.spin.transactions.application.port.in.query.SearchTransactionsQuery;
import mx.spin.transactions.domain.model.Transaction;
import mx.spin.transactions.domain.model.TransactionStatus;
import mx.spin.transactions.domain.model.TransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@Validated
@Tag(name = "Transactions", description = "Ejecución y consulta de transacciones financieras")
public class TransactionController {

    private final ExecuteTransactionUseCase executeTransaction;
    private final SearchTransactionsUseCase searchTransactions;
    private final TransactionRestMapper mapper;

    public TransactionController(ExecuteTransactionUseCase executeTransaction,
                                 SearchTransactionsUseCase searchTransactions,
                                 TransactionRestMapper mapper) {
        this.executeTransaction = executeTransaction;
        this.searchTransactions = searchTransactions;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Ejecuta una transacción contra el proveedor externo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ejecutada"),
            @ApiResponse(responseCode = "202", description = "Aceptada; proveedor no disponible, pendiente de reintento"),
            @ApiResponse(responseCode = "422", description = "Regla de negocio violada o rechazo del proveedor")
    })
    public ResponseEntity<TransactionResponse> execute(
            @Valid @RequestBody CreateTransactionRequest request,
            @Parameter(description = "Clave de idempotencia (UUID) generada por el cliente")
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Idempotency-Key must be a UUID")
            String idempotencyKey) {

        Transaction transaction = executeTransaction.execute(mapper.toCommand(request, idempotencyKey));

        HttpStatus httpStatus = transaction.status() == TransactionStatus.PENDING
                ? HttpStatus.ACCEPTED : HttpStatus.CREATED;

        return ResponseEntity.status(httpStatus).body(mapper.toResponse(transaction));
    }

    @GetMapping
    @Operation(summary = "Consulta transacciones con filtros y paginación")
    public PagedResponse<TransactionResponse> search(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {

        return mapper.toPagedResponse(
                searchTransactions.search(new SearchTransactionsQuery(accountId, status, type, page, limit)));
    }
}