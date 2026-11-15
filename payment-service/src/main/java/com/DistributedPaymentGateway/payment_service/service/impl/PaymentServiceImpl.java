package com.DistributedPaymentGateway.payment_service.service.impl;

import com.DistributedPaymentGateway.common_lib.enums.EventAggregateType;
import com.DistributedPaymentGateway.common_lib.enums.OrderStatus;
import com.DistributedPaymentGateway.common_lib.enums.PaymentEvent;
import com.DistributedPaymentGateway.common_lib.enums.PaymentStatus;
import com.DistributedPaymentGateway.common_lib.exception.BusinessRuleViolationException;
import com.DistributedPaymentGateway.common_lib.exception.ResourceNotFoundException;
import com.DistributedPaymentGateway.payment_service.dto.request.PaymentInitRequest;
import com.DistributedPaymentGateway.payment_service.dto.response.PaymentResponse;
import com.DistributedPaymentGateway.payment_service.entity.OrderRecord;
import com.DistributedPaymentGateway.payment_service.entity.Payment;
import com.DistributedPaymentGateway.payment_service.gateway.PaymentGatewayRouter;
import com.DistributedPaymentGateway.payment_service.gateway.dto.PaymentRequest;
import com.DistributedPaymentGateway.payment_service.gateway.dto.PaymentResult;
import com.DistributedPaymentGateway.payment_service.mapper.PaymentMapper;
import com.DistributedPaymentGateway.payment_service.outbox.OutboxEventPublisher;
import com.DistributedPaymentGateway.payment_service.repository.OrderRepository;
import com.DistributedPaymentGateway.payment_service.repository.PaymentRepository;
import com.DistributedPaymentGateway.payment_service.service.PaymentService;
import com.DistributedPaymentGateway.payment_service.statemachine.PaymentTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRouter paymentGatewayRouter;
    private final PaymentMapper paymentMapper;
    private final PaymentTransitionService paymentTransitionService;
    private final OutboxEventPublisher eventPublisher;

    @Override
    @Transactional
    // Repeatable_Read -- It does not allow another thread to update something which one thread is trying to update
    public PaymentResponse initiate(UUID merchantId, PaymentInitRequest request) {
//        OrderRecord order = orderRepository.findByIdAndMerchantId(request.orderId(), merchantId)
//                .orElseThrow(() -> new ResourceNotFoundException("Order" , request.orderId()));

        OrderRecord order = orderRepository.findByIdAndMerchantIdForUpdate(request.orderId(), merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order" , request.orderId()));

        // Validate that the order is currently eligible for payment initiation.
        if(order.getOrderStatus() != OrderStatus.CREATED && order.getOrderStatus() != OrderStatus.ATTEMPTED){
            throw new BusinessRuleViolationException("ORDER_NOT_PAYBLE",
                    "Order is not in valid state for payment initiation. Current status: "+ order.getOrderStatus());
        }

        order.setOrderStatus(OrderStatus.ATTEMPTED);
        order.setAttempts(order.getAttempts() + 1);

        Payment payment = Payment.builder()
                .order(order)
                .merchantId(merchantId)
                .amount(order.getAmount())
                .status(PaymentStatus.CREATED)
                .method(request.method())
                .idempotencyKey(UUID.randomUUID().toString())
                .methodDetails(request.methodDetails())
                .build();
        payment = paymentRepository.save(payment);

        PaymentRequest paymentRequest = new PaymentRequest(payment.getId(), request.orderId(),
                merchantId, order.getAmount(), request.method(), request.methodDetails());

        paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);
        PaymentResult result = paymentGatewayRouter.initiate(paymentRequest);

        switch (result) {
            case PaymentResult.Pending pending -> payment.setProcessorReference(pending.registrationRef());
            case PaymentResult.Failure failure -> {
//                payment.setStatus(PaymentStatus.FAILED);
                paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
                payment.setErrorCode(failure.errorCode());
                payment.setErrorDescription(failure.errorDescription());
            }
            case PaymentResult.Success success -> {
                log.warn("Invalid State");
                return null;
            }
        }
        payment =  paymentRepository.save(payment);
        orderRepository.save(order);
        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(), "PAYMENT_CREATED",
                OutboxEventPublisher.payload("orderId", order.getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "merchantId", merchantId.toString(),
                        "paymentStatus", payment.getStatus().name(),
                        "amountUnits", order.getAmount().getAmountUnits(),
                        "amountCurrency", order.getAmount().getCurrency(),
                        "paymentMethod", payment.getMethod())
        );
        return paymentMapper.toPaymentResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse capture(UUID merchantId, UUID paymentId) {
//        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
//                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        Payment payment = paymentRepository.findByIdAndMerchantIdForUpdate(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if(payment.getStatus() != PaymentStatus.CREATED){
            throw new BusinessRuleViolationException("PAYMENT_NOT_CAPTUREABLE",
                    "Payment is not in valid state for capture. Current status: "+ payment.getStatus());
        }

        paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_REQUEST);

        PaymentResult result = paymentGatewayRouter.capture(payment.getMethod(), paymentId);

        if(result instanceof PaymentResult.Success success) {
            paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_SUCCESS);
            payment.setCapturedAt(LocalDateTime.now());
            log.info("Payment captured , paymentId: " + paymentId);
        }
        else if(result instanceof PaymentResult.Failure failure) {
            paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_FAIL);
            payment.setErrorCode(failure.errorCode());
            payment.setErrorDescription(failure.errorDescription());
            log.warn("Payment capture failed , paymentId: " + paymentId);
        }
        payment =  paymentRepository.save(payment);

        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(), "PAYMENT_STATUS_CHANGED",
                OutboxEventPublisher.payload("orderId", payment.getOrder().getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "merchantId", payment.getMerchantId().toString(),
                        "paymentStatus", payment.getStatus().name(),
                        "amountUnits", payment.getAmount().getAmountUnits(),
                        "amountCurrency", payment.getAmount().getCurrency(),
                        "paymentMethod", payment.getMethod())
        );

        return paymentMapper.toPaymentResponse(payment);
    }

    @Override
    @Transactional
    public void resolveAuthorization(UUID paymentId, boolean approve, String bankRef, String errorCode, String errorDescription) {
//        Payment payment = paymentRepository.findById(paymentId)
//                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if(payment.getStatus() != PaymentStatus.AUTHORIZING){
            throw new BusinessRuleViolationException("PAYMENT_NOT_RESOLVABLE",
                    "Payment is not in valid state for resolution. Current status: "+ payment.getStatus());
        }

        OrderRecord orderRecord = payment.getOrder();

        if(approve) {
            paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
            payment.setBankReference(bankRef);
            payment.setAuthorizedAt(LocalDateTime.now());
            log.info("Payment authorization approved , paymentId: " + paymentId);

            // Auto-capture immediately after successful authorization.
            paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_REQUEST);
            PaymentResult captureResult = paymentGatewayRouter.capture(payment.getMethod(), paymentId);
            // Handle capture response.
            if(captureResult instanceof PaymentResult.Success success) {
                // On capture success, mark payment captured and order paid.
                paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_SUCCESS);
                payment.setCapturedAt(LocalDateTime.now());
                orderRecord.setOrderStatus(OrderStatus.PAID);
                log.info("Payment captured , paymentId: " + paymentId);
            } else if(captureResult instanceof PaymentResult.Failure failure) {
                paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_FAIL);
                payment.setErrorCode(failure.errorCode());
                payment.setErrorDescription(failure.errorDescription());
            }
        } else {
            // If bank declines authorization, move payment to authorization-failed state.
            paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
            payment.setErrorCode(errorCode);
            payment.setErrorDescription(errorDescription);
            log.warn("Payment authorization declined , paymentId: " + paymentId);
        }
        paymentRepository.save(payment);
        orderRepository.save(orderRecord);
        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(), "PAYMENT_STATUS_CHANGED",
                OutboxEventPublisher.payload("orderId", payment.getOrder().getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "merchantId", payment.getMerchantId().toString(),
                        "paymentStatus", payment.getStatus().name(),
                        "amountUnits", payment.getAmount().getAmountUnits(),
                        "amountCurrency", payment.getAmount().getCurrency(),
                        "paymentMethod", payment.getMethod())
        );
    }

}
