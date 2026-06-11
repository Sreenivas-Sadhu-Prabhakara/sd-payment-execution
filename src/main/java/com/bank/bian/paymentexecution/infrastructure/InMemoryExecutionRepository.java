package com.bank.bian.paymentexecution.infrastructure;

import com.bank.bian.paymentexecution.domain.ExecutionRepository;
import com.bank.bian.paymentexecution.domain.PaymentExecution;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 2 adapter. */
@Repository
public class InMemoryExecutionRepository implements ExecutionRepository {

    private final Map<String, PaymentExecution> executions = new ConcurrentHashMap<>();

    @Override
    public void save(PaymentExecution execution) {
        executions.put(execution.getExecutionId(), execution);
    }

    @Override
    public Optional<PaymentExecution> findById(String executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    @Override
    public Collection<PaymentExecution> findAll() {
        return executions.values();
    }
}
