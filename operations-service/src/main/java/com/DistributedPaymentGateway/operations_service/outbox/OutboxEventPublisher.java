package com.DistributedPaymentGateway.operations_service.outbox;

import com.DistributedPaymentGateway.common_lib.enums.EventAggregateType;
import com.DistributedPaymentGateway.operations_service.entity.OutboxEvent;
import com.DistributedPaymentGateway.operations_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {
    private final OutboxEventRepository outboxEventRepository;

    public void publish(EventAggregateType aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .payload(payload)
                .build();
        outboxEventRepository.save(outboxEvent);
    }

    /**
     * Builds an event payload from alternating key/value pairs. Unlike Map.of, null values are allowed,
     * so a missing optional field never fails the surrounding business transaction.
     */
    public static Map<String, Object> payload(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("payload requires an even number of key/value arguments");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }
}
