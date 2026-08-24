package mx.spin.transactions.adapter.in.rest.mapper;

import mx.spin.transactions.adapter.in.rest.dto.*;
import mx.spin.transactions.application.common.PageResult;
import mx.spin.transactions.application.port.in.command.ExecuteTransactionCommand;
import mx.spin.transactions.domain.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionRestMapper {

    public ExecuteTransactionCommand toCommand(CreateTransactionRequest request, String idempotencyKey) {
        return new ExecuteTransactionCommand(request.accountId(), request.type(), request.amount(),
                request.currency(), request.description(), idempotencyKey);
    }

    public TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.id().toString(), tx.accountId().value(), tx.type().name(),
                tx.money().amount(), tx.money().currency().code(), tx.description(),
                tx.status().name(), tx.providerTransactionId(), tx.balanceAfter(), tx.createdAt());
    }

    public PagedResponse<TransactionResponse> toPagedResponse(PageResult<Transaction> result) {
        return new PagedResponse<>(result.content().stream().map(this::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}