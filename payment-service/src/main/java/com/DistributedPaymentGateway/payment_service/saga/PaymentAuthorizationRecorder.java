package com.DistributedPaymentGateway.payment_service.saga;

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
import com.DistributedPaymentGateway.payment_service.gateway.dto.PaymentResult;
import com.DistributedPaymentGateway.payment_service.mapper.PaymentMapper;
import com.DistributedPaymentGateway.payment_service.outbox.OutboxEventPublisher;
import com.DistributedPaymentGateway.payment_service.repository.OrderRepository;
import com.DistributedPaymentGateway.payment_service.repository.PaymentRepository;
import com.DistributedPaymentGateway.payment_service.statemachine.PaymentTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAuthorizationRecorder {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransitionService paymentTransitionService;
    private final OutboxEventPublisher eventPublisher;
    private final PaymentMapper paymentMapper;

    @Transactional
    public Payment recordPayment(UUID merchantId, PaymentInitRequest request, String idempotencyKey) {
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
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString())
                .methodDetails(request.methodDetails())
                .build();
        payment = paymentRepository.save(payment);
        paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);
        return payment;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> findExistingAttempt(UUID merchantId, String idempotencyKey){
        return paymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .map(paymentMapper::toPaymentResponse);
    }

    @Transactional
    public PaymentResponse compensateAuthorizationFailure(UUID paymentId, String errorCode, String errorDescription) {

        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment" , paymentId));

        paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
        payment.setErrorCode(errorCode);
        payment.setErrorDescription(errorDescription);
        payment =  paymentRepository.save(payment);
        publishStatusEvent(payment, "PAYMENT_AUTHORIZATION_COMPENSATED");
        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional
    public PaymentResponse applyGatewayResult(UUID paymentId, PaymentResult result) {
        log.info("Applying gateway result for paymentId: {}, result: {}", paymentId, result);
        Payment payment =  paymentRepository.findByIdForUpdate(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment" , paymentId));

        switch (result) {
            case PaymentResult.Pending pending -> payment.setProcessorReference(pending.registrationRef());
            case PaymentResult.Failure failure -> {
                paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
                payment.setErrorCode(failure.errorCode());
                payment.setErrorDescription(failure.errorDescription());
            }
            case PaymentResult.Success success -> {
                log.info(String.format("Payment with id %s has been successfully completed", paymentId));
            }
        }
        payment = paymentRepository.save(payment);
        publishStatusEvent(payment, "PAYMENT_CREATED");
        log.info("Payment with id {} has been created", paymentId);
        return paymentMapper.toPaymentResponse(payment);
    }

    private void publishStatusEvent(Payment payment, String eventType){
        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(), eventType,
                Map.of(
                        "orderId", payment.getOrder().getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "merchantId", payment.getMerchantId().toString(),
                        "paymentStatus", payment.getStatus().name(),
                        "amountUnits", payment.getAmount().getAmountUnits(),
                        "amountCurrency", payment.getAmount().getCurrency(),
                        "paymentMethod", payment.getMethod()
                ));
    }
}
