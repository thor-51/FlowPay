package com.example.transactionprocessing.config;

import com.example.transactionprocessing.transaction.event.TransactionCreatedEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manual-ack consumer factory. AckMode.MANUAL_IMMEDIATE means TransactionEventConsumer is
 * responsible for calling Acknowledgment#acknowledge() itself once a message's outcome (success,
 * failure, scheduled-for-retry, dead-lettered) has actually been persisted — never auto-committed
 * on receipt, which would risk losing a transaction if the app crashed mid-processing.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // KafkaProducerConfig disables __TypeId__ headers to keep payloads portable to non-Java
        // consumers, so this side can't infer the target class from headers and must be told
        // explicitly. This service only ever consumes transaction.created internally (the other
        // three topics are produced for external/observability consumers), so one default type
        // is correct here; an additional internal listener topic would need
        // spring.json.type.mapping instead of a single VALUE_DEFAULT_TYPE.
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TransactionCreatedEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.transactionprocessing.*");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        // Kafka-level redelivery is intentionally minimal: a genuinely transient processing
        // failure becomes our own application-level RETRYING status plus RetryService's
        // scheduled, backed-off re-publish onto transaction.created — not container-managed
        // redelivery, which would block this partition while we sort it out. A single immediate
        // retry here only absorbs fleeting deserialization/connection blips; everything else is
        // handled inside TransactionProcessingService, which never lets an exception escape back
        // to the listener (see TransactionEventConsumer).
        return new DefaultErrorHandler(new FixedBackOff(0L, 1L));
    }
}
