package iuh.fit.se.contractservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String CONTRACT_EXCHANGE     = "contract.events";
    public static final String LOCATION_QUEUE        = "trip.location.updated.queue";
    public static final String LOCATION_ROUTING      = "trip.location.updated";
    public static final String AWARD_UPDATED_QUEUE   = "contract.award.updated.queue";
    public static final String AWARD_UPDATED_ROUTING = "contract.award.updated";
    public static final String COMPLAINT_SSE_EXCHANGE = "contract.complaint.sse.fanout";

    @Bean
    public TopicExchange contractEventsExchange() {
        return new TopicExchange(CONTRACT_EXCHANGE, true, false);
    }

    @Bean
    public Queue tripLocationUpdatedQueue() {
        return QueueBuilder.durable(LOCATION_QUEUE).build();
    }

    @Bean
    public Binding tripLocationBinding(Queue tripLocationUpdatedQueue,
                                       TopicExchange contractEventsExchange) {
        return BindingBuilder
                .bind(tripLocationUpdatedQueue)
                .to(contractEventsExchange)
                .with(LOCATION_ROUTING);
    }

    @Bean
    public Queue contractAwardUpdatedQueue() {
        return QueueBuilder.durable(AWARD_UPDATED_QUEUE).build();
    }

    @Bean
    public Binding contractAwardUpdatedBinding(Queue contractAwardUpdatedQueue,
                                               TopicExchange contractEventsExchange) {
        return BindingBuilder
                .bind(contractAwardUpdatedQueue)
                .to(contractEventsExchange)
                .with(AWARD_UPDATED_ROUTING);
    }

    @Bean
    public FanoutExchange complaintSseFanoutExchange() {
        return new FanoutExchange(COMPLAINT_SSE_EXCHANGE, true, false);
    }

    @Bean
    public Queue complaintSseQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding complaintSseBinding(Queue complaintSseQueue, FanoutExchange complaintSseFanoutExchange) {
        return BindingBuilder.bind(complaintSseQueue).to(complaintSseFanoutExchange);
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

    /** Eagerly declares exchanges, queues, and bindings on startup so consumers see topology immediately. */
    @Bean
    public ApplicationRunner declareRabbitTopologyOnStartup(AmqpAdmin amqpAdmin) {
        return args -> {
            if (amqpAdmin instanceof RabbitAdmin rabbitAdmin) {
                try {
                    rabbitAdmin.initialize();
                } catch (Exception ex) {
                    log.warn("[RabbitMQ] Could not initialize topology eagerly on startup: {}", ex.getMessage());
                }
            }
        };
    }
}
