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
import com.DistributedPaymentGateway.payment_service.saga.PaymentAuthorizationRecorder;
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
    private final PaymentAuthorizationRecorder paymentAuthorizationRecorder;

    @Override
    // Repeatable_Read -- It does not allow another thread to update something which one thread is trying to update
    public PaymentResponse initiate(UUID merchantId, PaymentInitRequest request) {

        Payment payment = paymentAuthorizationRecorder.recordPayment(merchantId, request);

        PaymentRequest paymentRequest = new PaymentRequest(payment.getId(), request.orderId(),
                merchantId, payment.getAmount(), request.method(), request.methodDetails());

        PaymentResult result;
        try {
            result = paymentGatewayRouter.initiate(paymentRequest);
        } catch (Exception e) {
            return paymentAuthorizationRecorder.compensateAuthorizationFailure(payment.getId(), "GATEWAY_ERROR", e.getMessage());
        }

        return paymentAuthorizationRecorder.applyGatewayResult(payment.getId(), result);
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
