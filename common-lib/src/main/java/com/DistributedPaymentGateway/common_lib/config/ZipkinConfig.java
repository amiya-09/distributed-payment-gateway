package com.DistributedPaymentGateway.common_lib.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

@AutoConfiguration(before = ZipkinAutoConfiguration.class)
public class ZipkinConfig {

    @Bean
    public BytesMessageSender zipkinSender(@Value("${management.zipkin.tracing.endpoint}") String endpoint) {

        return URLConnectionSender.create(endpoint);

    }
}
