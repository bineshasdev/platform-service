package com.flowbinary.platformservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topics.tenant-events}")
    private String tenantEventsTopic;

    @Value("${app.kafka.topics.feature-events}")
    private String featureEventsTopic;

    @Value("${app.kafka.topics.menu-events}")
    private String menuEventsTopic;

    @Value("${app.kafka.topics.user-events}")
    private String userEventsTopic;

    @Value("${app.kafka.topics.notification-events}")
    private String notificationEventsTopic;

    @Value("${app.kafka.topics.dlq-suffix}")
    private String dlqSuffix;

    @Bean
    public NewTopic tenantEventsTopic() {
        return TopicBuilder.name(tenantEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tenantEventsDlqTopic() {
        return TopicBuilder.name(tenantEventsTopic + dlqSuffix)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic featureEventsTopic() {
        return TopicBuilder.name(featureEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic featureEventsDlqTopic() {
        return TopicBuilder.name(featureEventsTopic + dlqSuffix)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic menuEventsTopic() {
        return TopicBuilder.name(menuEventsTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic menuEventsDlqTopic() {
        return TopicBuilder.name(menuEventsTopic + dlqSuffix)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(userEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userEventsDlqTopic() {
        return TopicBuilder.name(userEventsTopic + dlqSuffix)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(notificationEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsDlqTopic() {
        return TopicBuilder.name(notificationEventsTopic + dlqSuffix)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
