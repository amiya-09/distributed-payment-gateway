package com.DistributedPaymentGateway.api_gateway_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
@RequiredArgsConstructor
public class PublicRouteMatcher {

    private final SecurityRouteProperties securityRouteProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public boolean isPublic(String path) {
        return securityRouteProperties.getPublicRoutes().stream()
                .anyMatch(publicRoute -> pathMatcher.match(publicRoute, path));
    }
}
