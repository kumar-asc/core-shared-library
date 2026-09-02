# VBOX Core Shared Library

## Overview

The VBOX Core Shared Library provides foundational utilities for building resilient, observable Spring Boot microservices with OpenTelemetry instrumentation and SQL-backed idempotent event processing.

## Components

### 1. OpenTelemetry Structured Logging (OTelStructuredLogger)

A comprehensive logging utility that integrates OpenTelemetry tracing with structured logging capabilities.

#### Features:
- Automatic trace context propagation
- Structured key-value logging
- Error logging with exception details
- Metric logging
- Audit trail logging
- Correlation ID and trace ID tracking

#### Usage Example:

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final OTelStructuredLogger otelLogger;

    public void processOrder(String orderId) {
        Map<String, String> context = new HashMap<>();
        context.put("orderId", orderId);
        context.put("customerId", "CUST-123");
        context.put("amount", "1500.00");
        
        otelLogger.logWithContext("Processing order", context);
        
        try {
            // business logic
        } catch (Exception e) {
            otelLogger.logError("Order processing failed", e, context);
        }
    }
}
```

### 2. SQL-Backed IdempotentConsumer Engine

A framework for building idempotent event consumers with automatic duplicate detection and retry logic.

#### Features:
- Automatic duplicate event detection
- Configurable retry strategy with exponential backoff
- Persistent event log storage
- Correlation and request ID tracking
- Scheduled retry processing
- Event cleanup policies
- Status tracking (PENDING, PROCESSING, COMPLETED, FAILED, PERMANENTLY_FAILED, RETRYING)

#### Core Components:

**IdempotentEventLog (Entity)**: Stores event processing state and history.

**IdempotentEventService**: Manages event lifecycle and persistence.

**IdempotentConsumer (Abstract Class)**: Base class for implementing event consumers.

**IdempotentEventRetryScheduler**: Automatically processes retryable events.

#### Usage Example:

```java
@Component
public class OrderEventConsumer extends IdempotentConsumer<OrderPayload, OrderResult> {
    
    private final OrderService orderService;

    public OrderEventConsumer(
            IdempotentEventService eventService,
            OTelStructuredLogger otelLogger,
            ObjectMapper objectMapper,
            OrderService orderService) {
        super(eventService, otelLogger, objectMapper);
        this.orderService = orderService;
    }

    @Override
    protected Class<OrderPayload> getPayloadType() {
        return OrderPayload.class;
    }

    @Override
    protected OrderResult processPayload(OrderPayload payload, IdempotentEventRequest request) throws Exception {
        return orderService.createOrder(payload);
    }

    @Override
    protected Long calculateBackoffDelay(int retryCount) {
        return (long) Math.min(1000L * (long) Math.pow(2, retryCount), 300000L);
    }
}
```

#### Processing an Event:

```java
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {
    
    private final OrderEventConsumer orderEventConsumer;

    @PostMapping("/orders")
    public ResponseEntity<IdempotentEventResponse> processOrderEvent(
            @RequestBody OrderEventRequest request) {
        
        IdempotentEventRequest idempotentRequest = IdempotentEventRequest.builder()
            .eventId(request.getEventId())
            .consumerName("OrderEventConsumer")
            .eventPayload(objectMapper.writeValueAsString(request.getPayload()))
            .correlationId(request.getCorrelationId())
            .requestId(request.getRequestId())
            .sourceSystem("order-api")
            .targetSystem("order-service")
            .maxRetries(3)
            .retryDelayMs(5000L)
            .build();

        IdempotentEventResponse response = orderEventConsumer.consume(idempotentRequest);
        return ResponseEntity.ok(response);
    }
}
```

#### Querying Event Status:

```java
@Service
@RequiredArgsConstructor
public class EventStatusService {
    
    private final IdempotentEventService eventService;

    public IdempotentEventResponse getEventStatus(String eventId, String consumerName) {
        return eventService.getEventStatus(eventId, consumerName)
            .map(this::buildResponse)
            .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    public List<IdempotentEventLog> getEventsByCorrelationId(String correlationId) {
        return eventService.getEventsByCorrelationId(correlationId);
    }

    public long getFailedEventCount(String consumerName) {
        return eventService.getFailedEventCount(consumerName);
    }
}
```

## Database Schema

The IdempotentEventLog table is created with the following structure:

- **id**: Primary key (BIGINT, AUTO_INCREMENT)
- **event_id**: Unique event identifier (VARCHAR 255, NOT NULL)
- **consumer_name**: Consumer identifier (VARCHAR 255, NOT NULL)
- **event_payload**: Serialized event data (LONGTEXT, NOT NULL)
- **status**: Event processing status (VARCHAR 50, NOT NULL)
- **result_payload**: Processing result (LONGTEXT, nullable)
- **error_message**: Error description (VARCHAR 2000, nullable)
- **error_stack_trace**: Full stack trace (LONGTEXT, nullable)
- **retry_count**: Current retry attempt count (INT, default 0)
- **max_retries**: Maximum retry attempts (INT, default 3)
- **created_at**: Event creation timestamp (TIMESTAMP)
- **updated_at**: Last update timestamp (TIMESTAMP)
- **processed_at**: Processing completion timestamp (TIMESTAMP, nullable)
- **next_retry_at**: Next scheduled retry time (TIMESTAMP, nullable)
- **correlation_id**: Correlation identifier (VARCHAR 255, nullable)
- **request_id**: Request identifier (VARCHAR 255, nullable)
- **source_system**: Source system name (VARCHAR 255, nullable)
- **target_system**: Target system name (VARCHAR 255, nullable)

Indexes are created on: (event_id, consumer_name), (consumer_name, status), created_at, retry_count, next_retry_at, status, correlation_id, request_id, and processed_at.

## Configuration

### Spring Boot Application Configuration

Add to `application.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

otel:
  sdk:
    disabled: false
  exporter:
    otlp:
      protocol: grpc
```

### Enable Component Scanning

Ensure your main application class enables component scanning for the core-shared-library package:

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.volvo.vbox.core",
    "com.volvo.vbox.your-service"
})
@EntityScan("com.volvo.vbox.core.idempotent")
@EnableJpaRepositories("com.volvo.vbox.core.idempotent")
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

## Dependencies

The library requires:
- Java 25
- Spring Boot 4.1.x
- Hibernate 7.4.x
- OpenTelemetry SDK 1.41.0+
- OpenTelemetry Instrumentation 2.5.0+

## Retry Strategy

The default retry strategy uses exponential backoff:
- Retry delay = MIN(1000 * 2^retryCount, 300000) milliseconds
- Default max retries = 3

This can be customized by overriding the `calculateBackoffDelay` method in your consumer implementation.

## Scheduled Tasks

The library includes:
- **Retry Scheduler**: Processes retryable events every 60 seconds (initial delay: 30 seconds)
- **Cleanup Scheduler**: Removes events older than 30 days every hour (initial delay: 1 hour)

## Error Handling

Events can have the following final states:
- **COMPLETED**: Successfully processed
- **PERMANENTLY_FAILED**: Maximum retries exceeded
- **FAILED**: Transient failure (will be retried)

All exceptions are logged with full context including correlation IDs and stack traces via OpenTelemetry.

## Performance Considerations

- Connection pooling is configured with a maximum of 20 connections
- Hibernate batch size is set to 20 for INSERT/UPDATE operations
- Indexes are created on frequently queried columns for fast lookups
- Old completed events are periodically cleaned up to prevent table bloat

## Development

### Running Tests

```bash
mvn test -DskipIntegrationTests=false
```

### Building

```bash
mvn clean package
```

### Code Generation

The library uses Lombok for annotation processing and Hibernate's metamodel generator for type-safe queries.

## License

VBOX Multi-Module Project

## Support

For issues or questions, refer to the VBOX project documentation.