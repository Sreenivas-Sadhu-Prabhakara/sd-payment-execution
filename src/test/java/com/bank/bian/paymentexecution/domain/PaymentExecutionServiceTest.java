package com.bank.bian.paymentexecution.domain;

import com.bank.bian.paymentexecution.events.DomainEvent;
import com.bank.bian.paymentexecution.events.EventPublisher;
import com.bank.bian.paymentexecution.infrastructure.InMemoryExecutionRepository;
import com.bank.bian.paymentexecution.infrastructure.SimulatedAccountsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every saga path, including both compensation outcomes and idempotency. */
class PaymentExecutionServiceTest {

    static class RecordingPublisher implements EventPublisher {
        final List<DomainEvent> events = new ArrayList<>();
        @Override public void publish(DomainEvent event) { events.add(event); }
        List<String> types() { return events.stream().map(DomainEvent::type).toList(); }
    }

    RecordingPublisher events;
    PaymentExecutionService service;

    @BeforeEach
    void setUp() {
        events = new RecordingPublisher();
        service = new PaymentExecutionService(new InMemoryExecutionRepository(), events,
                new SimulatedAccountsClient(), Clock.systemUTC());
    }

    @Nested
    class SagaPaths {
        @Test
        void happyPath_debitThenCreditCompletes() {
            PaymentExecution e = service.execute("PO-1", "CA-D", "CA-C", 100_000, "INR");
            assertThat(e.getStatus()).isEqualTo(PaymentExecution.Status.COMPLETED);
            assertThat(events.types()).containsExactly("payment.debited", "payment.completed");
        }

        @Test
        void debitFailure_cleanFailureNothingMoved() {
            PaymentExecution e = service.execute("PO-2", "CA-FAIL-DEBIT-1", "CA-C", 100_000, "INR");
            assertThat(e.getStatus()).isEqualTo(PaymentExecution.Status.FAILED_DEBIT);
            assertThat(e.getFailureReason()).contains("insufficient funds");
            assertThat(events.types()).containsExactly("payment.failed"); // no debited event
        }

        @Test
        void creditFailure_compensationReturnsMoneyToDebtor() {
            PaymentExecution e = service.execute("PO-3", "CA-D", "CA-FAIL-CREDIT-1", 100_000, "INR");
            assertThat(e.getStatus()).isEqualTo(PaymentExecution.Status.FAILED_COMPENSATED);
            assertThat(e.getFailureReason()).startsWith("CREDIT_FAILED:");
            assertThat(events.types()).containsExactly("payment.debited", "payment.failed");
        }

        @Test
        void creditAndCompensationFailure_isLoudSuspense() {
            // FAIL-COMPENSATE fails the original credit AND the compensating re-credit
            PaymentExecution e = service.execute("PO-4", "CA-FAIL-COMPENSATE-1", "CA-FAIL-COMPENSATE-2",
                    100_000, "INR");
            assertThat(e.getStatus()).isEqualTo(PaymentExecution.Status.FAILED_SUSPENSE);
            assertThat(e.getFailureReason()).contains("COMPENSATION_FAILED");
            assertThat(events.types()).containsExactly("payment.debited", "payment.suspense");
        }
    }

    @Nested
    class Idempotency {
        @Test
        void sameOrderRefNeverExecutesTwice() {
            PaymentExecution first = service.execute("PO-IDEM", "CA-D", "CA-C", 100_000, "INR");
            PaymentExecution second = service.execute("PO-IDEM", "CA-D", "CA-C", 100_000, "INR");
            assertThat(second.getExecutionId()).isEqualTo(first.getExecutionId());
            // exactly one debited + one completed — money moved once
            assertThat(events.types()).containsExactly("payment.debited", "payment.completed");
        }
    }
}
