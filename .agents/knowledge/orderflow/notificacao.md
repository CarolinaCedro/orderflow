# Notification Flow — OrderFlow

`notification-service:8083` — Kafka consumer only, no REST endpoints, no database.

---

## Responsibility

`notification-service` consumes `payment-processed` events and sends customer notifications.
Currently logs simulated emails. Extension point for real email/SMS/push notifications.

---

## NotificationService Implementation

`org.cedro.notificationservice.service.NotificationService`

```java
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "payment-processed", groupId = "notification-group")
    public void listenPaymentStatus(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();
            String processedAt = node.path("processedAt").asText();

            if ("PAYMENT_SUCCESS".equals(status)) {
                log.info("[NOTIFICATION] Order {} approved at {}. Sending confirmation to customer.",
                         orderId, processedAt);
                sendConfirmationEmail(orderId);
            } else {
                log.warn("[NOTIFICATION] Order {} payment failed (status={}). Sending failure alert.",
                         orderId, status);
                sendFailureAlert(orderId, status);
            }
        } catch (Exception e) {
            log.error("Failed to process payment notification. Payload: {}", payload, e);
        }
    }

    private void sendConfirmationEmail(String orderId) {
        log.info("[EMAIL] To: customer | Subject: Order {} confirmed | Body: Your payment was approved!",
                 orderId);
    }

    private void sendFailureAlert(String orderId, String status) {
        log.warn("[EMAIL] To: customer | Subject: Order {} payment issue | Body: Status={}", orderId, status);
    }
}
```

---

## Kafka Configuration (notification-service)

`application.yml`:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9091
    consumer:
      group-id: notification-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

`notification-service` only consumes. No producer config needed (it does not publish events).

---

## Implementing Real Email with JavaMailSender

Add dependency to `notification-service/pom.xml`:
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
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

Implement the email sending:
```java
@Service
public class NotificationService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @KafkaListener(topics = "payment-processed", groupId = "notification-group")
    public void listenPaymentStatus(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();

            if ("PAYMENT_SUCCESS".equals(status)) {
                sendConfirmationEmail(orderId, "customer@example.com");
            } else {
                sendFailureAlert(orderId, status, "customer@example.com");
            }
        } catch (Exception e) {
            log.error("Failed to process payment notification. Payload: {}", payload, e);
        }
    }

    private void sendConfirmationEmail(String orderId, String to) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Order " + orderId + " confirmed");
            message.setText("Your order " + orderId + " has been successfully paid and confirmed!");
            mailSender.send(message);
            log.info("Confirmation email sent for order {} to {}", orderId, to);
        } catch (MailException e) {
            log.error("Failed to send confirmation email for order {}: {}", orderId, e.getMessage());
            // Do NOT rethrow — email failure should not affect the Kafka consumer lifecycle
        }
    }

    private void sendFailureAlert(String orderId, String status, String to) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Order " + orderId + " payment issue");
            message.setText("Your order " + orderId + " could not be processed. Status: " + status);
            mailSender.send(message);
            log.info("Failure alert sent for order {} to {}", orderId, to);
        } catch (MailException e) {
            log.error("Failed to send failure alert for order {}: {}", orderId, e.getMessage());
        }
    }
}
```

---

## Implementing with SendGrid

For transactional email at scale:

```xml
<dependency>
    <groupId>com.sendgrid</groupId>
    <artifactId>sendgrid-java</artifactId>
    <version>4.10.2</version>
</dependency>
```

```java
@Service
public class SendGridEmailService {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    public void sendEmail(String to, String subject, String body) {
        Email from = new Email("noreply@orderflow.com");
        Email toEmail = new Email(to);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, toEmail, content);

        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            log.info("Email sent via SendGrid, status: {}", response.getStatusCode());
        } catch (IOException e) {
            log.error("SendGrid API error: {}", e.getMessage());
        }
    }
}
```

---

## Implementing Push Notifications (Firebase)

For mobile push notifications, add Firebase Admin SDK and send via FCM:

```java
@Service
public class PushNotificationService {

    public void sendPush(String fcmToken, String title, String body) {
        Message message = Message.builder()
            .setToken(fcmToken)
            .setNotification(Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build())
            .build();
        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push sent. Response: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Firebase push notification failed: {}", e.getMessage());
        }
    }
}
```

---

## Customer ID Resolution

Currently `notification-service` only receives the `orderId`. To send an email, it needs
the customer's email address. Options:

1. **Include customer email in the `payment-processed` payload** (denormalized):
   ```json
   {"orderId":"abc","status":"PAYMENT_SUCCESS","processedAt":"...","customerEmail":"user@example.com"}
   ```
   This requires `payment-service` to query `order-service` for the order details before publishing.

2. **Query `order-service` via gateway** from `notification-service` (sync call):
   This creates coupling but is a valid option for non-critical notifications.

3. **Store customer contact info in a dedicated `notification-service` database** (event sourcing):
   Subscribe to order creation events and maintain a local projection of customer info.

Option 1 is recommended for simplicity. Update `payment-service` to enrich the payload.
