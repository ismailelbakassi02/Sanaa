package com.sanaa.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient used to call course-service.
 *
 * <p>Short timeouts keep the payment request bounded when the catalog is slow: a hung call must
 * fail with 503 instead of holding the HTTP worker. The caller's bearer token is forwarded by
 * {@code CourseServiceClient} so course-service applies the same tenant and role rules.
 */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class CourseClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient courseRestClient(PaymentProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        // Built directly instead of injecting Boot's builder: the payment module does not include
        // the RestClient starter, and a bounded client needs nothing from it.
        return RestClient.builder()
                .baseUrl(properties.courseServiceUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
