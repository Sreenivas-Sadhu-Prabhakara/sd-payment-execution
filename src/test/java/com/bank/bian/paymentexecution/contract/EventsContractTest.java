package com.bank.bian.paymentexecution.contract;

import com.bank.bian.paymentexecution.domain.PaymentExecutionService;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVENT CONTRACT TEST — the topics this domain's code publishes to must be
 * exactly the topics api/events.yaml declares. Code and contract cannot drift.
 */
class EventsContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void codeTopicsAreDeclaredInTheEventContract() throws Exception {
        Map<String, Object> events = new Yaml().load(new FileReader("api/events.yaml"));
        assertThat(events.get("service")).isEqualTo("sd-payment-execution");
        Set<String> topics = ((List<Map<String, Object>>) events.get("publishes")).stream()
                .map(t -> String.valueOf(t.get("topic")))
                .collect(Collectors.toSet());
        assertThat(topics).as("PaymentExecutionService.TOPIC_EXECUTION must be a declared publish topic").contains(PaymentExecutionService.TOPIC_EXECUTION);
        assertThat(topics).as("every declared topic should be backed by a code constant").hasSize(1);
    }
}
