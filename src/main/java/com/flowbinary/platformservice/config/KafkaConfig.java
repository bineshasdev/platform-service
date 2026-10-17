package com.flowbinary.platformservice.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${app.kafka.topics.dlq-suffix}")
    private String dlqSuffix;

    /**
     * Default JSON KafkaTemplate built from spring.kafka.producer.* config.
     *
     * Declared explicitly because KafkaAutoConfiguration is @ConditionalOnMissingBean
     * for both ProducerFactory and KafkaTemplate — once KafkaAvroConfig registers its
     * avroKafkaTemplate, auto-configuration skips both beans, leaving kafkaErrorHandler
     * with nothing to inject.
     */
    @Primary
    @Bean("kafkaTemplate")
    public KafkaTemplate<String, Object> kafkaTemplate(KafkaProperties kafkaProperties) {
        return new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null)));
    }

    /**
     * Error handler with 3 retries (500ms apart) then dead-letter.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            @Qualifier("kafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + dlqSuffix, 0)
        );
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
