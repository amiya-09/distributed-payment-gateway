package com.DistributedPaymentGateway.payment_service.service;


import com.DistributedPaymentGateway.payment_service.dto.request.CreateOrderRequest;
import com.DistributedPaymentGateway.payment_service.dto.response.OrderResponse;
import com.DistributedPaymentGateway.payment_service.dto.response.PaymentResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(UUID merchantId, CreateOrderRequest orderRequest);

    OrderResponse getById(UUID merchantId, UUID orderId);

    OrderResponse cancel(UUID merchantId, UUID orderId);

    List<PaymentResponse> listPayments(UUID merchantId, UUID orderId);
}
