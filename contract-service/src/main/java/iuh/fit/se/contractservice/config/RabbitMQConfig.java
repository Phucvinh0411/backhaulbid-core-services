package iuh.fit.se.contractservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String CONTRACT_EXCHANGE  = "contract.events";
    public static final String MILESTONE_QUEUE    = "trip.milestone.reached.queue";
    public static final String MILESTONE_ROUTING  = "trip.milestone.reached";

    @Bean
    public TopicExchange contractEventsExchange() {
        return new TopicExchange(CONTRACT_EXCHANGE, true, false);
    }

    @Bean
    public Queue milestoneReachedQueue() {
        return QueueBuilder.durable(MILESTONE_QUEUE).build();
    }

    @Bean
    public Binding milestoneBinding(Queue milestoneReachedQueue,
                                    TopicExchange contractEventsExchange) {
        return BindingBuilder
                .bind(milestoneReachedQueue)
                .to(contractEventsExchange)
                .with(MILESTONE_ROUTING);
    }

    /** Đảm bảo RabbitTemplate gửi message dạng JSON. */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}
