package com.example.transactionprocessing.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Spring Boot auto-configures a KafkaAdmin bean whenever spring-kafka is on the classpath; it
 * picks up every NewTopic bean in the context and creates (or reconciles partition count of)
 * the topic on application startup. No RabbitMQ-style queue declarations are needed elsewhere —
 * this is the single source of truth for topic topology.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topics.transaction-created}")
    private String transactionCreatedTopic;

    @Value("${app.kafka.topics.transaction-processed}")
    private String transactionProcessedTopic;

    @Value("${app.kafka.topics.transaction-failed}")
    private String transactionFailedTopic;

    @Value("${app.kafka.topics.transaction-dead-letter}")
    private String transactionDeadLetterTopic;

    @Value("${app.kafka.partitions}")
    private int partitions;

    @Value("${app.kafka.replication-factor}")
    private short replicationFactor;

    @Bean
    public NewTopic transactionCreatedTopic() {
        return TopicBuilder.name(transactionCreatedTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic transactionProcessedTopic() {
        return TopicBuilder.name(transactionProcessedTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic transactionFailedTopic() {
        return TopicBuilder.name(transactionFailedTopic).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic transactionDeadLetterTopic() {
        // Single partition is intentional: dead-lettered transactions are low-volume by
        // definition (they only land here after exhausting the retry budget) and an admin
        // dashboard tailing this topic benefits more from strict ordering than from
        // parallelism.
        return TopicBuilder.name(transactionDeadLetterTopic).partitions(1).replicas(replicationFactor).build();
    }
}
