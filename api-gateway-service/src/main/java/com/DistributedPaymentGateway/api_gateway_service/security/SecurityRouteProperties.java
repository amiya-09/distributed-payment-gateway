package com.DistributedPaymentGateway.api_gateway_service.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.security")
public class SecurityRouteProperties {
    private List<String> publicRoutes = List.of();

}
