package com.DistributedPaymentGateway.operations_service.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.UUID;

@Embeddable
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class SettlementPaymentId {
    private UUID settlementId;
    private UUID paymentId;
}
