package com.DistributedPaymentGateway.payment_service.service.impl;

import com.DistributedPaymentGateway.common_lib.enums.EventAggregateType;
import com.DistributedPaymentGateway.common_lib.enums.OrderStatus;
import com.DistributedPaymentGateway.payment_service.dto.request.CreateOrderRequest;
import com.DistributedPaymentGateway.payment_service.dto.response.OrderResponse;
import com.DistributedPaymentGateway.payment_service.entity.OrderRecord;
import com.DistributedPaymentGateway.payment_service.mapper.OrderMapper;
import com.DistributedPaymentGateway.payment_service.outbox.OutboxEventPublisher;
import com.DistributedPaymentGateway.payment_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher eventPublisher;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse persist(UUID merchantId, CreateOrderRequest request, UUID customerId, int defaultOrderExpiryMinutes) {
        OrderRecord orderRecord = OrderRecord.builder()
                .receipt(request.receipt())
                .amount(request.amount())
                .notes(request.notes())
                .merchantId(merchantId)
                .customerId(customerId)
                .orderStatus(OrderStatus.CREATED)
                .expiresAt(request.expiresAt()!=null ? request.expiresAt() : LocalDateTime.now().plusMinutes(defaultOrderExpiryMinutes))
                .build();

        orderRecord = orderRepository.save(orderRecord);

        eventPublisher.publish(EventAggregateType.ORDER, orderRecord.getId(), "ORDER_CREATED",
                OutboxEventPublisher.payload("orderId", orderRecord.getId(),
                        "merchantId", merchantId.toString(),
                        "orderStatus", orderRecord.getOrderStatus().name(),
                        "amountUnits", orderRecord.getAmount().getAmountUnits(),
                        "amountCurrency", orderRecord.getAmount().getCurrency())
        );
        return orderMapper.toOrderResponse(orderRecord);
    }
}
