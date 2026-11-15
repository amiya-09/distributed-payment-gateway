package com.DistributedPaymentGateway.common_lib.ratelimit;

public interface RateLimiter {

    RateLimitResult check(String key, int maxRequestAllowed, int windowSeconds);
}
