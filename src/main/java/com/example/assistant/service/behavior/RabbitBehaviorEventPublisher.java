package com.example.assistant.service.behavior;

import com.example.assistant.dto.BehaviorEventMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "assistant.behavior.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitBehaviorEventPublisher implements BehaviorEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public RabbitBehaviorEventPublisher(RabbitTemplate rabbitTemplate,
            @Value("${assistant.behavior.rabbitmq.exchange:habit.behavior.events}") String exchange,
            @Value("${assistant.behavior.rabbitmq.routing-key:behavior.events.created}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public boolean publish(BehaviorEventMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        return true;
    }
}
