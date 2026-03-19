# Skill: Send Notification After Payment

## Purpose

Implement customer notification after a payment result is published to `payment-processed`.
Covers the current log-based simulation and extension to real email/SMS.

## When to Use

- Modifying notification logic in `notification-service`.
- Adding real email sending (JavaMailSender, SendGrid, AWS SES).
- Adding SMS or push notification.
- Debugging why notifications are not firing after payment.

## Prerequisites

- `payment-service` is publishing to `payment-processed` topic.
- `notification-service` is running and connected to Kafka.
- Kafka group `notification-group` is not already consuming from a stale offset.

## Knowledge References

- `.agents/knowledge/orderflow/notificacao.md` — NotificationService full implementation
- `.agents/knowledge/spring/kafka.md` — @KafkaListener error handling
- `.agents/knowledge/architecture/event-driven.md` — payment-processed payload structure

---

## Steps

### Step 1: Verify Kafka Consumer Config (notification-service)

`notification-service/application.yml`:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    consumer:
      group-id: notification-group    # MUST differ from payment-group and order-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

### Step 2: Verify NotificationService Implementation

```java
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "payment-processed", groupId = "notification-group")
    public void listenPaymentStatus(String payload) {
        log.info("Notification event received. Payload: {}", payload);  // Log BEFORE
        try {
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();
            String processedAt = node.path("processedAt").asText();

            if ("PAYMENT_SUCCESS".equals(status)) {
                log.info("[NOTIFICATION] Sending confirmation for order {} (processed at {})",
                         orderId, processedAt);
                sendConfirmationEmail(orderId);
            } else {
                log.warn("[NOTIFICATION] Sending failure alert for order {} (status={})",
                         orderId, status);
                sendFailureAlert(orderId, status);
            }
        } catch (Exception e) {
            log.error("Failed to process payment notification. Payload: {}", payload, e);
            // Never rethrow
        }
    }

    private void sendConfirmationEmail(String orderId) {
        log.info("[EMAIL] To: customer | Subject: Order {} confirmed | " +
                 "Body: Your payment was approved!", orderId);
    }

    private void sendFailureAlert(String orderId, String status) {
        log.warn("[EMAIL] To: customer | Subject: Order {} payment issue | Body: Status={}",
                 orderId, status);
    }
}
```

### Step 3: Add Real Email with JavaMailSender

Add dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

Add to `application.yml`:
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:noreply@orderflow.com}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

Refactor `NotificationService`:
```java
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @KafkaListener(topics = "payment-processed", groupId = "notification-group")
    public void listenPaymentStatus(String payload) {
        log.info("Notification event received. Payload: {}", payload);
        try {
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();

            // Resolve customer email — for now use a placeholder
            // In production: query order-service or include in payload
            String customerEmail = "customer@example.com";

            if ("PAYMENT_SUCCESS".equals(status)) {
                sendEmail(customerEmail,
                          "Order " + orderId + " confirmed",
                          "Your order " + orderId + " payment was approved!");
            } else {
                sendEmail(customerEmail,
                          "Order " + orderId + " payment issue",
                          "Your order " + orderId + " could not be processed. Status: " + status);
            }
        } catch (Exception e) {
            log.error("Failed to process payment notification. Payload: {}", payload, e);
        }
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // Do NOT rethrow — email failure should not kill the Kafka consumer
        }
    }
}
```

### Step 4: Verify notification-service is in the Eureka registry

`application.yml` must have Eureka client config:
```yaml
eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

---

## Validation Checklist

- [ ] `groupId = "notification-group"` in `@KafkaListener` (not `payment-group` or `order-group`)
- [ ] Log present before processing the payload
- [ ] JSON parsing in `try-catch` block
- [ ] Exception caught and NOT rethrown
- [ ] Both `sendConfirmationEmail` and `sendFailureAlert` paths implemented
- [ ] Email exceptions in `sendEmail()` are caught separately (email failure != consumer failure)

## Common Mistakes

- Using `groupId = "payment-group"` — `notification-service` would compete with `payment-service`
  for messages instead of receiving independent copies.
- Rethrowing email exceptions — one failed email would kill the Kafka consumer offset commit.
- Parsing `processedAt` as a date without handling format variations — keep it as a String.
- Not logging the full payload on error — makes debugging nearly impossible.
