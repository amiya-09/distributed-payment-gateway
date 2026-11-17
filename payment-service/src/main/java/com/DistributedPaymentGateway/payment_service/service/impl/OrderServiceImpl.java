package com.DistributedPaymentGateway.payment_service.service.impl;

import com.DistributedPaymentGateway.common_lib.dto.FindOrCreateCustomerRequest;
import com.DistributedPaymentGateway.common_lib.enums.EventAggregateType;
import com.DistributedPaymentGateway.common_lib.enums.OrderStatus;
import com.DistributedPaymentGateway.common_lib.exception.BusinessRuleViolationException;
import com.DistributedPaymentGateway.common_lib.exception.DuplicateResourceException;
import com.DistributedPaymentGateway.common_lib.exception.ResourceNotFoundException;
import com.DistributedPaymentGateway.payment_service.client.CustomerServiceGateway;
import com.DistributedPaymentGateway.payment_service.dto.request.CreateOrderRequest;
import com.DistributedPaymentGateway.payment_service.dto.response.OrderResponse;
import com.DistributedPaymentGateway.payment_service.dto.response.PaymentResponse;
import com.DistributedPaymentGateway.payment_service.entity.OrderRecord;
import com.DistributedPaymentGateway.payment_service.entity.Payment;
import com.DistributedPaymentGateway.payment_service.mapper.OrderMapper;
import com.DistributedPaymentGateway.payment_service.mapper.PaymentMapper;
import com.DistributedPaymentGateway.payment_service.outbox.OutboxEventPublisher;
import com.DistributedPaymentGateway.payment_service.repository.OrderRepository;
import com.DistributedPaymentGateway.payment_service.repository.PaymentRepository;
import com.DistributedPaymentGateway.payment_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    @Value("${order.default.expiry.minutes:15}")
    private int defaultOrderExpiryMinutes;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final CustomerServiceGateway customerServiceGateway;
    private final OutboxEventPublisher eventPublisher;
    private final OrderPersistenceService orderPersistenceService;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse createOrder(UUID merchantId, CreateOrderRequest orderRequest) {
        // If merchant sends a receipt, enforce uniqueness per merchant.
        if(orderRequest.receipt() != null && orderRepository.existsByMerchantIdAndReceipt(merchantId, orderRequest.receipt())){
            throw new DuplicateResourceException("ORDER_ALREADY_EXISTS", "Order with receipt "
                    + orderRequest.receipt() + " already exists for merchant " + merchantId);
        }

        UUID customerId = null;
        if(orderRequest.customer() != null){
            customerId = customerServiceGateway.findOrCreate(new FindOrCreateCustomerRequest(
                    merchantId,
                    orderRequest.customer().email(),
                    orderRequest.customer().name(),
                    orderRequest.customer().phone()));
        }

        return orderPersistenceService.persist(merchantId, orderRequest, customerId, defaultOrderExpiryMinutes);

    }

    @Override
    public OrderResponse getById(UUID merchantId, UUID orderId) {
        // Scope read by merchantId so one merchant cannot access another merchant's order.
        OrderRecord order =  orderRepository.findByIdAndMerchantId(merchantId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order with id " + orderId + " not found for merchant " + merchantId));
        // Convert entity to API response DTO.
//        return new OrderResponse(
//                order.getId(),
//                order.getMerchantId(),
//                order.getReceipt(),
//                order.getAmount(),
//                order.getOrderStatus(),
//                order.getAttempts(),
//                order.getNotes(),
//                order.getExpiresAt(),
//                null
//        );
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancel(UUID merchantId, UUID orderId) {
        OrderRecord order =  orderRepository.findByIdAndMerchantId(merchantId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order with id " + orderId + " not found for merchant " + merchantId));
        if(order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.PAID){
            throw new BusinessRuleViolationException("ORDER_ALREADY_CANCELLED", "Order with id " + orderId + " is already cancelled");
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        eventPublisher.publish(EventAggregateType.ORDER, order.getId(), "ORDER_CANCELLED",
                OutboxEventPublisher.payload("orderId", order.getId(),
                        "merchantId", merchantId.toString(),
                        "orderStatus", order.getOrderStatus().name(),
                        "amountUnits", order.getAmount().getAmountUnits(),
                        "amountCurrency", order.getAmount().getCurrency())
        );
        return orderMapper.toOrderResponse(order);
    }

    @Override
    public List<PaymentResponse> listPayments(UUID merchantId, UUID orderId) {
        OrderRecord order =  orderRepository.findByIdAndMerchantId(merchantId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order with id " + orderId + " not found for merchant " + merchantId));
        List<Payment> paymentList = paymentRepository.findByOrder_Id(order);
//        return paymentList.stream()
//                .map(paymentMapper::toPaymentResponse)
//                .collect(Collectors.toList());
        // MapStruct can also map lists directly.
        // See PaymentMapper#toPaymentResponseList(List<Payment>).
        return paymentMapper.toPaymentResponseList(paymentList);
    }
}
