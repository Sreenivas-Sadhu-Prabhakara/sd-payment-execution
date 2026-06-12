package com.bank.bian.paymentexecution.infrastructure;

import com.bank.bian.paymentexecution.domain.AccountsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Phase 2d-ii loop closure: the saga's legs execute against the REAL account
 * service domains, routed by account-ref prefix:
 *
 *   CA-… → bian.payments.accounts.current-account-url  (sd-current-account)
 *   SA-… → bian.payments.accounts.savings-account-url  (sd-savings-account)
 *
 * debit → POST …/payments/withdraw ; credit → POST …/payments/deposit.
 * A 409 from the account SD (overdraft, blocked, KYC-pending…) is a clean
 * business failure of the leg — exactly what drives the saga's compensation.
 */
@Component
@ConditionalOnProperty(name = "bian.payments.accounts.current-account-url")
public class HttpAccountsClient implements AccountsClient {

    private static final Logger log = LoggerFactory.getLogger("bian.accounts-legs");
    private static final String CA_CR = "current-account-facility-fulfillment-arrangement";
    private static final String SA_CR = "savings-account-facility-fulfillment-arrangement";

    private final RestClient rest;
    private final String currentUrl;
    private final String savingsUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpAccountsClient(RestClient.Builder builder,
                              @Value("${bian.payments.accounts.current-account-url}") String currentUrl,
                              @Value("${bian.payments.accounts.savings-account-url:}") String savingsUrl) {
        this.rest = builder.build();
        this.currentUrl = currentUrl;
        this.savingsUrl = savingsUrl;
    }

    @Override
    public LegResult debit(String accountRef, long amountMinor, String reference) {
        return post(accountRef, "withdraw", amountMinor, reference);
    }

    @Override
    public LegResult credit(String accountRef, long amountMinor, String reference) {
        return post(accountRef, "deposit", amountMinor, reference);
    }

    private LegResult post(String accountRef, String op, long amountMinor, String reference) {
        String base, cr;
        if (accountRef != null && accountRef.startsWith("CA-")) {
            base = currentUrl; cr = CA_CR;
        } else if (accountRef != null && accountRef.startsWith("SA-") && !savingsUrl.isBlank()) {
            base = savingsUrl; cr = SA_CR;
        } else {
            return LegResult.fail("UNROUTABLE_ACCOUNT_REF:" + accountRef);
        }
        try {
            rest.post()
                    .uri(base + "/v1/" + cr + "/" + accountRef + "/payments/" + op)
                    .header("Content-Type", "application/json")
                    .body(Map.of("amountMinor", amountMinor, "reference", reference))
                    .retrieve()
                    .toBodilessEntity();
            return LegResult.ok();
        } catch (RestClientResponseException e) {
            // business rejection from the account SD (overdraft/blocked/KYC/404)
            String reason = e.getStatusCode().value() + ":" + extract(e.getResponseBodyAsString());
            return LegResult.fail(reason);
        } catch (Exception e) {
            log.warn("{} leg transport failure for {}: {}", op, accountRef, e.getMessage());
            return LegResult.fail("TRANSPORT:" + e.getMessage());
        }
    }

    private String extract(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            String code = n.path("code").asText("");
            return code.isEmpty() ? body : code + " " + n.path("message").asText("");
        } catch (Exception e) {
            return body;
        }
    }
}
