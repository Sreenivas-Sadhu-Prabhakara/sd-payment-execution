package com.bank.bian.paymentexecution.domain;

import com.bank.bian.paymentexecution.events.DomainEvent;
import com.bank.bian.paymentexecution.events.EventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The debit-credit saga — Payment Execution's whole reason to exist.
 *
 *   1. DEBIT the debtor.    Fails → FAILED_DEBIT (nothing moved; clean failure).
 *   2. CREDIT the creditor. Fails → COMPENSATE: re-credit the debtor.
 *        compensation ok    → FAILED_COMPENSATED (money back where it started)
 *        compensation fails → FAILED_SUSPENSE    (funds in flight — loudest
 *                             possible signal; never silently retried)
 *   3. Both legs ok → COMPLETED.
 *
 * Every leg and every failure mode emits an event; the Payment Order SD
 * receives the outcome through its execution-result callback (today: the
 * caller relays it; with Kafka: it consumes payment.completed/failed).
 *
 * Idempotence note: orderRef is the natural idempotency key — re-submitting
 * an already-executed order returns the existing execution instead of moving
 * money twice. (Double-execution is the cardinal sin of payments.)
 */
@Service
public class PaymentExecutionService {

    public static final String TOPIC_EXECUTION = "bian.payments.payment-execution";

    private final ExecutionRepository repository;
    private final EventPublisher events;
    private final AccountsClient accounts;
    private final Clock clock;

    @Autowired
    public PaymentExecutionService(ExecutionRepository repository, EventPublisher events,
                                   AccountsClient accounts) {
        this(repository, events, accounts, Clock.systemUTC());
    }

    public PaymentExecutionService(ExecutionRepository repository, EventPublisher events,
                                   AccountsClient accounts, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.accounts = accounts;
        this.clock = clock;
    }

    // ── the saga ─────────────────────────────────────────────────────────────

    public PaymentExecution execute(String orderRef, String debtorAccountRef,
                                    String creditorAccountRef, long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw DomainException.invalid("AMOUNT_NOT_POSITIVE", "amountMinor must be > 0");
        }
        if (debtorAccountRef == null || debtorAccountRef.isBlank()
                || creditorAccountRef == null || creditorAccountRef.isBlank()) {
            throw DomainException.invalid("ACCOUNT_REFS_REQUIRED",
                    "debtorAccountRef and creditorAccountRef are required");
        }
        // idempotency on orderRef — never execute the same order twice
        if (orderRef != null && !orderRef.isBlank()) {
            PaymentExecution existing = repository.findAll().stream()
                    .filter(e -> orderRef.equals(e.getOrderRef()))
                    .findFirst().orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        PaymentExecution exec = PaymentExecution.receive("PE-" + UUID.randomUUID(), orderRef,
                debtorAccountRef, creditorAccountRef, amountMinor,
                currency == null ? "INR" : currency, clock.instant());
        repository.save(exec);

        // ── leg 1: debit the debtor ──────────────────────────────────────────
        AccountsClient.LegResult debit = accounts.debit(debtorAccountRef, amountMinor,
                "PAY:" + exec.getExecutionId());
        if (!debit.success()) {
            return finish(exec, PaymentExecution.Status.FAILED_DEBIT,
                    "DEBIT_FAILED:" + debit.reason(), "payment.failed");
        }
        exec.setStatus(PaymentExecution.Status.DEBITED);
        repository.save(exec);
        events.publish(DomainEvent.of(TOPIC_EXECUTION, "payment.debited", Map.of(
                "executionId", exec.getExecutionId(), "orderRef", orderRef == null ? "" : orderRef,
                "debtorAccountRef", debtorAccountRef, "amountMinor", amountMinor)));

        // ── leg 2: credit the creditor ───────────────────────────────────────
        AccountsClient.LegResult credit = accounts.credit(creditorAccountRef, amountMinor,
                "PAY:" + exec.getExecutionId());
        if (credit.success()) {
            return finish(exec, PaymentExecution.Status.COMPLETED, null, "payment.completed");
        }

        // ── compensation: undo the debit ─────────────────────────────────────
        exec.setStatus(PaymentExecution.Status.COMPENSATING);
        repository.save(exec);
        AccountsClient.LegResult compensation = accounts.credit(debtorAccountRef, amountMinor,
                "COMPENSATE:" + exec.getExecutionId());
        if (compensation.success()) {
            return finish(exec, PaymentExecution.Status.FAILED_COMPENSATED,
                    "CREDIT_FAILED:" + credit.reason(), "payment.failed");
        }
        // Money left the debtor and reached no one. Scream.
        PaymentExecution suspense = finish(exec, PaymentExecution.Status.FAILED_SUSPENSE,
                "CREDIT_FAILED:" + credit.reason() + " AND COMPENSATION_FAILED:" + compensation.reason(),
                "payment.suspense");
        return suspense;
    }

    private PaymentExecution finish(PaymentExecution exec, PaymentExecution.Status status,
                                    String reason, String eventType) {
        exec.setStatus(status);
        exec.setFailureReason(reason);
        exec.setFinishedAt(clock.instant());
        repository.save(exec);
        events.publish(DomainEvent.of(TOPIC_EXECUTION, eventType, Map.of(
                "executionId", exec.getExecutionId(),
                "orderRef", exec.getOrderRef() == null ? "" : exec.getOrderRef(),
                "status", status.name(),
                "reason", reason == null ? "" : reason,
                "amountMinor", exec.getAmountMinor())));
        return exec;
    }

    // ── queries ──────────────────────────────────────────────────────────────

    public PaymentExecution retrieve(String executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> DomainException.notFound("EXECUTION_UNKNOWN",
                        "no execution " + executionId));
    }

    public Collection<PaymentExecution> list() {
        return repository.findAll();
    }
}
