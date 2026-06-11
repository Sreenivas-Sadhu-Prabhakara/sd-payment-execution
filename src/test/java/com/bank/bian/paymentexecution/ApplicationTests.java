package com.bank.bian.paymentexecution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot + API smoke: the saga through HTTP, happy and compensated paths. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

    static final String CR = "/v1/payment-transaction-procedure";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void happyPathCompletesThroughTheApi() {
        var r = rest.postForEntity(url(CR + "/initiate"),
                Map.of("orderRef", "PO-API-1", "debtorAccountRef", "CA-D",
                        "creditorAccountRef", "CA-C", "amountMinor", 50_000, "currency", "INR"),
                Map.class);
        assertThat(r.getStatusCode().value()).isEqualTo(201);
        assertThat(r.getBody().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    void creditFailureCompensatesThroughTheApi() {
        var r = rest.postForEntity(url(CR + "/initiate"),
                Map.of("orderRef", "PO-API-2", "debtorAccountRef", "CA-D",
                        "creditorAccountRef", "CA-FAIL-CREDIT-9", "amountMinor", 50_000),
                Map.class);
        assertThat(r.getBody().get("status")).isEqualTo("FAILED_COMPENSATED");
        assertThat((String) r.getBody().get("failureReason")).startsWith("CREDIT_FAILED:");
    }
}
