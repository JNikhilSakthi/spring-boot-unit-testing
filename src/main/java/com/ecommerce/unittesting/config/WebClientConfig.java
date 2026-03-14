package com.ecommerce.unittesting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${fraud.service.base-url:http://localhost:8081}")
    private String fraudServiceBaseUrl;

    @Value("${bank.service.base-url:http://localhost:8082}")
    private String bankServiceBaseUrl;

    @Bean(name = "fraudServiceClient")
    public WebClient fraudServiceClient() {
        return WebClient.builder()
                .baseUrl(fraudServiceBaseUrl)
                .build();
    }

    @Bean(name = "bankServiceClient")
    public WebClient bankServiceClient() {
        return WebClient.builder()
                .baseUrl(bankServiceBaseUrl)
                .build();
    }
}
