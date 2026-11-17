package com.DistributedPaymentGateway.api_gateway_service.security;

import com.DistributedPaymentGateway.api_gateway_service.client.ApiKeyLookupClient;
import com.DistributedPaymentGateway.common_lib.cache.ApiKeyCache;
import com.DistributedPaymentGateway.common_lib.cache.ApiKeyCacheEntry;
import com.DistributedPaymentGateway.common_lib.exception.RateLimitException;
import com.DistributedPaymentGateway.common_lib.ratelimit.RateLimitResult;
import com.DistributedPaymentGateway.common_lib.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthHandler {

    public static final String BASIC_PREFIX = "Basic ";
    private static final String SECRET_VERIFY_PREFIX = "apikey:secret-verified:";
    private static final Duration SECRET_VERIFY_TTL = Duration.ofSeconds(30);
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final ApiKeyLookupClient apiKeyLookupClient;
    private final ApiKeyCache apiKeyCache;
    private final RateLimiter rateLimiter;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.rate-limit.use-case.api-key.requests-per-minute:60}")
    private Integer requestsPerMinute;

    public Map<String, String> authenticate (String authHeader, HttpServletResponse response) {
        String[] credentials = decode(authHeader);
        if(credentials == null){
            throw new GatewayAuthenticationException("Malformed API Key Header");
        }
        String keyId = credentials[0];
        String rawSecret = credentials[1];

        ApiKeyCacheEntry entry = apiKeyCache.get(keyId).orElseGet(() -> loadAndCache(keyId));

        if(entry == null || !entry.enabled() || !validateApiKey(entry, rawSecret)){
                throw new GatewayAuthenticationException("Invalid or missing API Key");
        }

            // Rate limiting check
        RateLimitResult rateLimitResult = rateLimiter.check("apikey:" + keyId, requestsPerMinute, 60); // Example: 100 requests per 60 seconds

        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitException("Too many requests", rateLimitResult.retryAfterSeconds());
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimitResult.remaining()));

        return Map.of(
                "X-Merchant-Id", entry.merchantId().toString(),
                "X-Environment", entry.environment().toString(),
                "X-Key-Id", entry.keyId()
        );
    }

    private ApiKeyCacheEntry loadAndCache(String keyId) {
        try {
            ApiKeyCacheEntry entry = apiKeyLookupClient.findByKeyId(keyId);
            apiKeyCache.put(keyId, entry);
            return entry;
        } catch (Exception e) {
            log.warn("API Key lookup failed for: {}", keyId, e);
            return null;
        }
    }

    private boolean validateApiKey(ApiKeyCacheEntry apiKey, String rawSecret) {

        String cacheKey = SECRET_VERIFY_PREFIX + apiKey.keyId() + ":" + apiKey.keySecretHash() + ":" + sha256(rawSecret);

        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Error occurred while checking API key in Redis: {}", cacheKey, e);
        }

        boolean matches = BCRYPT.matches(rawSecret, apiKey.keySecretHash())
                || (apiKey.isInGracePeriod()
                && apiKey.previousKeySecretHash() != null
                && BCRYPT.matches(rawSecret, apiKey.previousKeySecretHash()));

        if (matches) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey, "true", SECRET_VERIFY_TTL);
            } catch (Exception e) {
                log.warn("Error occurred while updating API key in Redis: {}", cacheKey, e);
            }
        }

        return matches;
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred while hashing value", e);
        }
    }

    private String[] decode(String authorizationHeader) {
        String encoded = authorizationHeader.substring(BASIC_PREFIX.length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        if(colon < 1) return null;
        return new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
    }

}
