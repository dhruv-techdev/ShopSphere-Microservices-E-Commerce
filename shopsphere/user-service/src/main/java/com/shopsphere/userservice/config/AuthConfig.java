package com.shopsphere.userservice.config;

import com.shopsphere.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public NewTopic emailVerificationRequestedTopic() {
        return TopicBuilder.name(Topics.USER_EMAIL_VERIFICATION_REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic passwordResetRequestedTopic() {
        return TopicBuilder.name(Topics.USER_PASSWORD_RESET_REQUESTED).partitions(3).replicas(1).build();
    }
}
