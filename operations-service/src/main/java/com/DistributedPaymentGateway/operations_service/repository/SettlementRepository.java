package com.DistributedPaymentGateway.operations_service.repository;

import com.DistributedPaymentGateway.common_lib.enums.SettlementStatus;
import com.DistributedPaymentGateway.operations_service.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    List<Settlement> findByStatus(SettlementStatus settlementStatus);
}
