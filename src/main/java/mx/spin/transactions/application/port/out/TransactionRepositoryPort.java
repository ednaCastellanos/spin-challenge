package mx.spin.transactions.application.port.out;

import mx.spin.transactions.application.common.PageResult;
import mx.spin.transactions.application.port.in.query.SearchTransactionsQuery;
import mx.spin.transactions.domain.model.Transaction;
import mx.spin.transactions.domain.model.TransactionId;

import java.util.Optional;

public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(TransactionId id);
    PageResult<Transaction> search(SearchTransactionsQuery query);
}