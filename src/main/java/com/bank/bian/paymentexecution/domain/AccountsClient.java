package com.bank.bian.paymentexecution.domain;

/**
 * Outbound port to the account service domains (debit/credit legs).
 *
 * Phase 2b-c default: a simulator with deterministic failure injection so the
 * saga and its compensation paths are fully testable today. The in-cluster
 * HTTP adapter (sd-current-account / sd-savings-account withdraw + deposit
 * endpoints) replaces it without touching the saga.
 */
public interface AccountsClient {

    record LegResult(boolean success, String reason) {
        public static LegResult ok() { return new LegResult(true, null); }
        public static LegResult fail(String reason) { return new LegResult(false, reason); }
    }

    LegResult debit(String accountRef, long amountMinor, String reference);

    LegResult credit(String accountRef, long amountMinor, String reference);
}
