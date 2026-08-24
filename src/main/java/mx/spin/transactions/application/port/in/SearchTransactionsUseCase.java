package mx.spin.transactions.application.port.in;

import mx.spin.transactions.application.common.PageResult;
import mx.spin.transactions.application.port.in.query.SearchTransactionsQuery;
import mx.spin.transactions.domain.model.Transaction;

public interface SearchTransactionsUseCase {
    PageResult<Transaction> search(SearchTransactionsQuery query);
}