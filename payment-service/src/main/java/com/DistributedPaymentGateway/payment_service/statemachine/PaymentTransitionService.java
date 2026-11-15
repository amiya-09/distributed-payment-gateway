package com.DistributedPaymentGateway.payment_service.statemachine;

import com.DistributedPaymentGateway.common_lib.context.MerchantContext;
import com.DistributedPaymentGateway.common_lib.enums.PaymentActor;
import com.DistributedPaymentGateway.common_lib.enums.PaymentEvent;
import com.DistributedPaymentGateway.common_lib.enums.PaymentStatus;
import com.DistributedPaymentGateway.payment_service.entity.Payment;
import com.DistributedPaymentGateway.payment_service.entity.PaymentTransitionLog;
import com.DistributedPaymentGateway.payment_service.repository.PaymentTransitionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransitionService {

    private final PaymentTransitionLogRepository paymentTransitionLogRepository;
    private final PaymentStateMachine paymentStateMachine;
    private final MerchantContext  merchantContext;

    public PaymentStatus apply(Payment payment, PaymentEvent event) {
        PaymentStatus previous = payment.getStatus();
        PaymentStatus next = paymentStateMachine.transition(previous, event);
        PaymentActor actor = getPaymentActor();
        PaymentTransitionLog log = PaymentTransitionLog.builder()
                .payment(payment)
                .fromStatus(previous)
                .toStatus(next)
                .event(event)
                .actor(actor)
                .occurredAt(LocalDateTime.now())
                .build();
        payment.setStatus(next);
        paymentTransitionLogRepository.save(log);
        return next;
    }

    private PaymentActor getPaymentActor() {
        try {
            String keyId = merchantContext.getKeyId();
            UUID merchantId = merchantContext.getMerchantId();

            if (keyId != null && !keyId.isBlank()) {
                return PaymentActor.CUSTOMER;
            } else if (merchantId != null) {
                return PaymentActor.MERCHANT;
            }
        } catch (Exception ignored) {
        }
        return PaymentActor.SYSTEM;
    }

}
