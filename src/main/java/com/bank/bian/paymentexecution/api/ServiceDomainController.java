package com.bank.bian.paymentexecution.api;

import com.bank.bian.paymentexecution.domain.PaymentExecution;
import com.bank.bian.paymentexecution.domain.PaymentExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * BIAN semantic API for "Payment Execution" — Phase 2b-c, real domain.
 * Control record: Payment Transaction Procedure.
 * Initiate runs the debit-credit saga synchronously and returns the outcome.
 *
 * Contract: api/openapi.yaml (owned by this repo).
 */
@RestController
@RequestMapping("/v1")
public class ServiceDomainController {

    static final String CR = "payment-transaction-procedure";

    private final PaymentExecutionService service;

    public ServiceDomainController(PaymentExecutionService service) {
        this.service = service;
    }

    @GetMapping("/service-domain")
    public Map<String, String> serviceDomain() {
        return Map.of(
                "serviceDomain", "Payment Execution",
                "businessArea", "Operations and Execution",
                "businessDomain", "Payments",
                "functionalPattern", "Process",
                "assetType", "Payment Transaction",
                "controlRecord", "Payment Transaction Procedure",
                "version", "0.2.0",
                "phase", "2b-deep"
        );
    }

    public record ExecuteRequest(String orderRef, String debtorAccountRef,
                                 String creditorAccountRef, long amountMinor, String currency) {}

    @PostMapping("/" + CR + "/initiate")
    public ResponseEntity<PaymentExecution> initiate(@RequestBody ExecuteRequest req) {
        PaymentExecution exec = service.execute(req.orderRef(), req.debtorAccountRef(),
                req.creditorAccountRef(), req.amountMinor(), req.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(exec);
    }

    @GetMapping("/" + CR)
    public Collection<PaymentExecution> list() {
        return service.list();
    }

    @GetMapping("/" + CR + "/{executionId}/retrieve")
    public PaymentExecution retrieve(@PathVariable String executionId) {
        return service.retrieve(executionId);
    }
}
