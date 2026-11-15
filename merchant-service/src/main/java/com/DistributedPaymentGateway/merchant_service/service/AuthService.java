package com.DistributedPaymentGateway.merchant_service.service;


import com.DistributedPaymentGateway.merchant_service.dto.request.LoginRequest;
import com.DistributedPaymentGateway.merchant_service.dto.request.MerchantSignupRequest;
import com.DistributedPaymentGateway.merchant_service.dto.response.LoginResponse;
import com.DistributedPaymentGateway.merchant_service.dto.response.MerchantResponse;

public interface AuthService {
    MerchantResponse signup(MerchantSignupRequest request);

    LoginResponse login(LoginRequest request);
}
