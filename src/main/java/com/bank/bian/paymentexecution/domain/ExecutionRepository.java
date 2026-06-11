package com.bank.bian.paymentexecution.domain;

import java.util.Collection;
import java.util.Optional;

/** Persistence port — in-memory now, Postgres when the platform hydrates. */
public interface ExecutionRepository {

    void save(PaymentExecution execution);

    Optional<PaymentExecution> findById(String executionId);

    Collection<PaymentExecution> findAll();
}
