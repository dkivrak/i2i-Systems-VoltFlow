package com.voltwise.simulator.config;

import com.voltwise.simulator.service.InvalidRegistrationEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    @Bean
    NewTopic assetRegistrationTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.getAssetRegistrationTopic())
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }

    @Bean
    NewTopic telemetryTopic(KafkaTopicProperties properties) {
        return topic(properties.getTelemetryTopic(), properties);
    }

    @Bean
    NewTopic assetRegistrationDeadLetterTopic(KafkaTopicProperties properties) {
        return topic(properties.getRegistrationDltTopic(), properties);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaProperties kafkaProperties,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicProperties topicProperties
    ) {
        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                new StringDeserializer()
        );
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, failure) -> new TopicPartition(topicProperties.getRegistrationDltTopic(), record.partition())
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(topicProperties.getRetryIntervalMs(), topicProperties.getRetryAttempts())
        );
        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,
                InvalidRegistrationEventException.class
        );
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private NewTopic topic(String name, KafkaTopicProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicas())
                .build();
    }
}
