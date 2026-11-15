package com.DistributedPaymentGateway.payment_service.mapper;

import com.DistributedPaymentGateway.payment_service.dto.response.PaymentResponse;
import com.DistributedPaymentGateway.payment_service.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;


@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {

    @Mapping(target = "orderId", source = "order.id")
    PaymentResponse toPaymentResponse(Payment payment);

    List<PaymentResponse> toPaymentResponseList(List<Payment> paymentList);
}
