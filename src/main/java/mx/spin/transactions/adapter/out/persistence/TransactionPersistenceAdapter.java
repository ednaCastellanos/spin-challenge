package mx.spin.transactions.adapter.out.persistence;

import mx.spin.transactions.adapter.out.persistence.mapper.TransactionPersistenceMapper;
import mx.spin.transactions.application.common.PageResult;
import mx.spin.transactions.application.port.in.query.SearchTransactionsQuery;
import mx.spin.transactions.application.port.out.TransactionRepositoryPort;
import mx.spin.transactions.domain.model.Transaction;
import mx.spin.transactions.domain.model.TransactionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {

    private final TransactionJpaRepository repository;
    private final TransactionPersistenceMapper mapper;

    public TransactionPersistenceAdapter(TransactionJpaRepository repository, TransactionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Transaction save(Transaction transaction) {
        return mapper.toDomain(repository.save(mapper.toEntity(transaction)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> findById(TransactionId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Transaction> search(SearchTransactionsQuery query) {
        var pageable = PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TransactionJpaEntity> page = repository.findAll(TransactionSpecifications.from(query), pageable);

        return PageResult.of(page.getContent().stream().map(mapper::toDomain).toList(),
                query.page(), query.size(), page.getTotalElements());
    }
}