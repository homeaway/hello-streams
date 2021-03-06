package com.homeaway.streamplatform.hellostreams.orderprocessor.dao;

import com.google.common.base.Preconditions;
import com.homeaway.streamplatform.hellostreams.orderprocessor.model.Order;
import com.homeaway.streamplatform.hellostreams.orderprocessor.model.OrderPlaced;
import com.homeaway.streamplatform.hellostreams.orderprocessor.processor.OrderStreamProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Repository
@DependsOn("orderStreamProcessor")
@Slf4j
public class OrderDao {
    private KafkaProducer<String, SpecificRecord> kafkaOrderEventProducer;
    private final OrderStreamProcessor orderStreamProcessor;

    @Value("${order-processor.order.commands.stream}")
    private String orderCommandsStream;

    @Value("${order-processor.write.wait.timeout}")
    private long writeWaitTimeout;

    public OrderDao(@Autowired KafkaProducer<String, SpecificRecord> kafkaOrderEventProducer,
                    @Autowired OrderStreamProcessor orderStreamProcessor) {
        Preconditions.checkNotNull(kafkaOrderEventProducer, "kafkaOrderEventProducer cannot be null");
        Preconditions.checkNotNull(orderStreamProcessor, "orderStreamProcessor cannot be null");
        this.kafkaOrderEventProducer = kafkaOrderEventProducer;
        this.orderStreamProcessor = orderStreamProcessor;
    }

    @PostConstruct
    public void init() {}

    @PreDestroy
    public void close() {
        log.info("Shutting down kafkaOrderEventProducer");
        kafkaOrderEventProducer.flush();
        kafkaOrderEventProducer.close(Duration.ofSeconds(30));
    }

    public List<com.homeaway.streamplatform.hellostreams.orderprocessor.model.Order> findAllOrders() {
        return orderStreamProcessor.findAllOrders();
    }

    public OrderPlaced placeOrder(String customerId, String item) {
        Preconditions.checkNotNull(customerId, "customerId cannot be null");
        Preconditions.checkNotNull(item, "item cannot be null");

        // write to persistent location
        com.homeaway.streamplatform.hellostreams.OrderPlaced orderPlacedEvent = createOrderPlaced(customerId, item);
        send(orderPlacedEvent);

        return toDTO(orderPlacedEvent);
    }

    private void waitForWrite(String id, String orderId) {
        // TODO - move hardcode wait time into property
        long timeout = System.currentTimeMillis() + 30000;
        boolean found;
        do {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            Order order = orderStreamProcessor.getOrder(orderId);
            found = order!=null
                    && order.getId().equals(id);
        } while(System.currentTimeMillis() < timeout && !found);
    }

    private OrderPlaced toDTO(com.homeaway.streamplatform.hellostreams.OrderPlaced orderPlacedAvro) {
        OrderPlaced orderPlaced = new OrderPlaced();
        orderPlaced.setId(orderPlacedAvro.getId());
        orderPlaced.setOrderId(orderPlacedAvro.getOrderId());
        orderPlaced.setCustomerId(orderPlacedAvro.getCustomerId());
        orderPlaced.setItem(orderPlacedAvro.getItem());
        orderPlaced.setCreated(orderStreamProcessor.toDTOTime(orderPlacedAvro.getCreated()));
        return orderPlaced;
    }

    private void send(com.homeaway.streamplatform.hellostreams.OrderPlaced orderPlacedEvent) {
        try {
            log.info("Writing orderPlaced={} to kafka.", orderPlacedEvent);
            Future<RecordMetadata> resultFuture = kafkaOrderEventProducer.send(new ProducerRecord<>(
                    orderCommandsStream, null,
                    orderPlacedEvent.getCreated().getMillis(),
                    orderPlacedEvent.getOrderId(), // use order key to make order aggregate easier!!
                    orderPlacedEvent));
            // sync wait for response
            resultFuture.get(writeWaitTimeout, TimeUnit.MILLISECONDS);

            // read your writes!!
            waitForWrite(orderPlacedEvent.getId(), orderPlacedEvent.getOrderId());
        } catch (Exception e) {
            throw new IllegalStateException("Could not write to kafka.", e);
        }
    }

    private com.homeaway.streamplatform.hellostreams.OrderPlaced createOrderPlaced(String customerId, String item) {
        return com.homeaway.streamplatform.hellostreams.OrderPlaced.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setOrderId(UUID.randomUUID().toString())
                .setCustomerId(customerId)
                .setItem(item)
                .setCreated(DateTime.now())
                .build();
    }
}
