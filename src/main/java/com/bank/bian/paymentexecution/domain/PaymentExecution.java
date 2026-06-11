package com.bank.bian.paymentexecution.domain;

import java.time.Instant;

/**
 * Control record made real: "Payment Transaction Procedure" — the actual
 * movement of money, executed as a two-leg saga with compensation.
 *
 * State machine:
 *   RECEIVED ─debit ok─▶ DEBITED ─credit ok─▶ COMPLETED
 *      │                    └─credit fails─▶ COMPENSATING
 *      │                          ├─repay ok──▶ FAILED_COMPENSATED  (money back with debtor)
 *      │                          └─repay fails▶ FAILED_SUSPENSE    (MONEY IN FLIGHT — ops!)
 *      └─debit fails─▶ FAILED_DEBIT  (nothing moved)
 *
 * FAILED_SUSPENSE is deliberately loud: the debit succeeded, the credit
 * failed, AND the compensation failed — funds are in suspense and a human
 * (or a Phase-3 recovery job) must intervene. It must never be silently
 * retried or hidden.
 */
public class PaymentExecution {

    public enum Status { RECEIVED, DEBITED, COMPLETED, COMPENSATING, FAILED_DEBIT, FAILED_COMPENSATED, FAILED_SUSPENSE }

    private String executionId;
    private String orderRef;            // the Payment Order this executes
    private String debtorAccountRef;
    private String creditorAccountRef;
    private long amountMinor;
    private String currency;
    private Status status = Status.RECEIVED;
    private String failureReason;
    private Instant receivedAt;
    private Instant finishedAt;

    public static PaymentExecution receive(String executionId, String orderRef,
                                           String debtorAccountRef, String creditorAccountRef,
                                           long amountMinor, String currency, Instant now) {
        PaymentExecution e = new PaymentExecution();
        e.executionId = executionId;
        e.orderRef = orderRef;
        e.debtorAccountRef = debtorAccountRef;
        e.creditorAccountRef = creditorAccountRef;
        e.amountMinor = amountMinor;
        e.currency = currency;
        e.receivedAt = now;
        return e;
    }

    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.FAILED_DEBIT
                || status == Status.FAILED_COMPENSATED || status == Status.FAILED_SUSPENSE;
    }

    public String getExecutionId() { return executionId; }
    public String getOrderRef() { return orderRef; }
    public String getDebtorAccountRef() { return debtorAccountRef; }
    public String getCreditorAccountRef() { return creditorAccountRef; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
