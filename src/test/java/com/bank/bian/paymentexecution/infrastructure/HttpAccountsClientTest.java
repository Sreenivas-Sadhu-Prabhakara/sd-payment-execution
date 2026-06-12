package com.bank.bian.paymentexecution.infrastructure;

import com.bank.bian.paymentexecution.domain.AccountsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Routing + status mapping for the real account-SD legs (2d-ii). */
class HttpAccountsClientTest {

    RestClient.Builder builder;
    MockRestServiceServer server;
    HttpAccountsClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAccountsClient(builder,
                "http://current.test", "http://savings.test");
    }

    @Test
    void debitOnCaRefRoutesToCurrentAccountWithdraw() {
        server.expect(requestTo("http://current.test/v1/current-account-facility-fulfillment-arrangement/CA-1/payments/withdraw"))
                .andExpect(jsonPath("$.amountMinor").value(5000))
                .andRespond(withStatus(HttpStatus.CREATED));
        assertThat(client.debit("CA-1", 5000, "PAY:PE-1").success()).isTrue();
        server.verify();
    }

    @Test
    void creditOnSaRefRoutesToSavingsDeposit() {
        server.expect(requestTo("http://savings.test/v1/savings-account-facility-fulfillment-arrangement/SA-9/payments/deposit"))
                .andRespond(withStatus(HttpStatus.CREATED));
        assertThat(client.credit("SA-9", 7000, "PAY:PE-2").success()).isTrue();
        server.verify();
    }

    @Test
    void businessRejectionBecomesCleanLegFailureWithTheCode() {
        server.expect(requestTo("http://current.test/v1/current-account-facility-fulfillment-arrangement/CA-2/payments/withdraw"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"OVERDRAFT_EXCEEDED\",\"message\":\"available 0\"}"));
        AccountsClient.LegResult r = client.debit("CA-2", 5000, "PAY:PE-3");
        assertThat(r.success()).isFalse();
        assertThat(r.reason()).contains("OVERDRAFT_EXCEEDED");
    }

    @Test
    void unroutableRefFailsWithoutAnyHttpCall() {
        AccountsClient.LegResult r = client.debit("XX-1", 5000, "PAY:PE-4");
        assertThat(r.success()).isFalse();
        assertThat(r.reason()).startsWith("UNROUTABLE_ACCOUNT_REF");
    }
}
