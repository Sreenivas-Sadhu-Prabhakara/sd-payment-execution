package com.bank.bian.paymentexecution.infrastructure;

import com.bank.bian.paymentexecution.domain.AccountsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Deterministic simulator with failure injection by account-ref marker —
 * the saga's compensation paths are exercisable end-to-end without real
 * account services:
 *
 *   …FAIL-DEBIT…       → debit leg fails        (e.g. "CA-FAIL-DEBIT-1")
 *   …FAIL-CREDIT…      → credit leg fails
 *   …FAIL-COMPENSATE…  → credit AND the compensating re-credit fail
 *                        (drives the FAILED_SUSPENSE path)
 *   anything else      → both legs succeed
 */
@Component
// Default adapter — replaced by HttpAccountsClient once the account-SD URLs
// are configured (havingValue="false" can never equal a real URL).
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "bian.payments.accounts.current-account-url", havingValue = "false", matchIfMissing = true)
public class SimulatedAccountsClient implements AccountsClient {

    private static final Logger log = LoggerFactory.getLogger("bian.accounts-sim");

    @Override
    public LegResult debit(String accountRef, long amountMinor, String reference) {
        if (marker(accountRef, "FAIL-DEBIT")) {
            return LegResult.fail("insufficient funds (simulated)");
        }
        log.info("debit  {} {} ({})", accountRef, amountMinor, reference);
        return LegResult.ok();
    }

    @Override
    public LegResult credit(String accountRef, long amountMinor, String reference) {
        // compensation re-credits the DEBTOR; only the explicit compensate marker fails it
        boolean isCompensation = reference != null && reference.startsWith("COMPENSATE:");
        if (isCompensation) {
            if (marker(accountRef, "FAIL-COMPENSATE")) {
                return LegResult.fail("debtor account unreachable (simulated)");
            }
        } else if (marker(accountRef, "FAIL-CREDIT") || marker(accountRef, "FAIL-COMPENSATE")) {
            // FAIL-COMPENSATE also fails the original credit so the saga reaches compensation
            return LegResult.fail("creditor account closed (simulated)");
        }
        log.info("credit {} {} ({})", accountRef, amountMinor, reference);
        return LegResult.ok();
    }

    private boolean marker(String accountRef, String token) {
        return accountRef != null && accountRef.toUpperCase(Locale.ROOT).contains(token);
    }
}
