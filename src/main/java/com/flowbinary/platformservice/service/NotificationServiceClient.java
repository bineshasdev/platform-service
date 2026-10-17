package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.exception.OtpDeliveryException;
import com.flowbinary.platformservice.model.OtpChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Synchronous HTTP client to notification-service's internal OTP delivery endpoint.
 * Unlike the rest of the platform's Kafka-based fire-and-forget notification path,
 * OTP delivery needs an immediate success/failure result to return to the caller.
 */
@Slf4j
@Component
public class NotificationServiceClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public NotificationServiceClient(AppProperties appProperties) {
        AppProperties.NotificationServiceProperties props = appProperties.getNotificationService();
        this.internalApiKey = props.getInternalApiKey();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.getConnectTimeoutMs());
        requestFactory.setReadTimeout(props.getReadTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public void sendOtp(OtpChannel channel, String to, String code, String purpose, long expiryMinutes) {
        try {
            restClient.post()
                    .uri("/api/v1/internal/otp/send")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(Map.of(
                            "channel", channel.name(),
                            "to", to,
                            "code", code,
                            "purpose", purpose,
                            "expiryMinutes", expiryMinutes))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Failed to deliver OTP via notification-service: channel={} target={} error={}",
                    channel, to, ex.getMessage());
            throw new OtpDeliveryException("Failed to send verification code. Please try again.", ex);
        }
    }
}
