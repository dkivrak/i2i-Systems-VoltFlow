package com.voltwise.core.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "voltwise.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {
    @Bean
    NewTopic registrationTopic(VoltWiseProperties properties) {
        return TopicBuilder.name(properties.getKafka().getAssetRegistrationTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic telemetryTopic(VoltWiseProperties properties) {
        return TopicBuilder.name(properties.getKafka().getTelemetryTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic telemetryDltTopic(VoltWiseProperties properties) {
        return TopicBuilder.name(properties.getKafka().getTelemetryDltTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> operations, VoltWiseProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(operations,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        properties.getKafka().getTelemetryDltTopic(), record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                NullPointerException.class,
                IllegalStateException.class
        );
        return handler;
    }
}
