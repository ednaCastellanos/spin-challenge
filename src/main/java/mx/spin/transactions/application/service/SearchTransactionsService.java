package mx.spin.transactions.application.service;

import mx.spin.transactions.application.common.PageResult;
import mx.spin.transactions.application.port.in.SearchTransactionsUseCase;
import mx.spin.transactions.application.port.in.query.SearchTransactionsQuery;
import mx.spin.transactions.application.port.out.TransactionRepositoryPort;
import mx.spin.transactions.domain.model.Transaction;

public class SearchTransactionsService implements SearchTransactionsUseCase {

    private final TransactionRepositoryPort repository;

    public SearchTransactionsService(TransactionRepositoryPort repository) { this.repository = repository; }

    @Override
    public PageResult<Transaction> search(SearchTransactionsQuery query) { return repository.search(query); }
}