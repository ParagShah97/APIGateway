# API Gateway (9090)

The API Gateway serves as a single entry point for all incoming API calls from various devices to internal services such as Payment, Order, or Product. It handles authentication, routing, and monitoring of these requests. The gateway routes the calls to the appropriate services based on the request path.

## Project Setup

The project is built using Spring Initializer with the following dependencies:

1. **Spring Reactive Web**: Uses WebFlux for high-concurrency applications.
2. **Spring Boot Actuator**: Provides built-in (or custom) endpoints to monitor and manage the application.
3. **Lombok**: Simplifies code by reducing boilerplate.
4. **Cloud Bootstrap**: Enables cloud configuration.
5. **Eureka Client**: Configures the service as a Eureka client for service registry (Eureka Server).
6. **Config Client**: Connects to the config server to fetch common configurations.
7. **Zipkin**: Enables connection to a centralized debugging server.
8. **Reactive Gateway**: Provides an effective way to route APIs in a Servlet-based application.
9. **Resilience4J**: Adds resilience patterns like circuit breakers (requires manual addition of reactive dependency).

## Configuration

The `application.yaml` file contains the necessary configurations:

```yaml
server:
  port: 9090

spring:
  application:
    name: CloudGateway
  config:
    import: configserver:http://localhost:9296
  cloud:
    gateway:
      routes:
        - id: ORDERSERVICE
          uri: lb://ORDERSERVICE
          predicates:
            - Path=/order/**
        - id: PRODUCTSERVICE
          uri: lb://PRODUCTSERVICE
          predicates:
            - Path=/product/**
        - id: PAYMENTSERVICE
          uri: lb://PAYMENTSERVICE
          predicates:
            - Path=/payment/**

management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

## Circuit Breaker

The Circuit Breaker pattern is used to handle scenarios where one or more services are down. The **Resilience4J** library is used to implement this pattern.

### States of Circuit Breaker

- **Closed State**: Healthy state where requests are allowed.
- **Open State**: If a service fails (e.g., Order Service), the circuit breaker opens after a certain number of failed requests (e.g., 10). A timer (e.g., 10 seconds) is set before transitioning to the Half-Open state.
- **Half-Open State**: Allows a limited number of requests to pass through. If the success rate exceeds a threshold, the circuit breaker returns to the Closed state; otherwise, it goes back to the Open state.

### Circuit Breaker in API Gateway

1. **Add Dependency**:
   ```xml
   <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
   </dependency>
   ```

2. **Configure in `application.yaml`**:
   ```yaml
   cloud:
     gateway:
       routes:
         - id: ORDERSERVICE
           uri: lb://ORDERSERVICE
           predicates:
             - Path=/order/**
           filters:
             - name: CircuitBreaker
               args:
                 name: ORDERSERVICE
                 fallbackuri: forward:/orderServiceFallBack
         - id: PRODUCTSERVICE
           uri: lb://PRODUCTSERVICE
           predicates:
             - Path=/product/**
           filters:
             - name: CircuitBreaker
               args:
                 name: PRODUCTSERVICE
                 fallbackuri: forward:/productServiceFallBack
         - id: PAYMENTSERVICE
           uri: lb://PAYMENTSERVICE
           predicates:
             - Path=/payment/**
           filters:
             - name: CircuitBreaker
               args:
                 name: PAYMENTSERVICE
                 fallbackuri: forward:/paymentServiceFallBack
   ```

3. **Fallback Controller**:
   ```java
   @GetMapping("/orderServiceFallBack")
   public String orderServiceFallBack() {
       return "Order Service is down!";
   }
   ```

4. **Bean Configuration**:
   ```java
   @Bean
   public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
       return factory -> factory.configureDefault(
           id -> new Resilience4JConfigBuilder(id)
               .circuitBreakerConfig(
                   CircuitBreakerConfig.ofDefaults()
               ).build()
       );
   }
   ```

### Circuit Breaker in Other Services

1. **Add Dependency**:
   ```xml
   <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
   </dependency>
   ```

2. **Feign Client with Circuit Breaker**:
   ```java
   @CircuitBreaker(name = "external", fallbackMethod = "fallback")
   @FeignClient(name="PaymentService/payment")
   public interface PaymentService {
       @PostMapping
       ResponseEntity<Long> makePayment(@RequestBody PaymentRequest paymentRequest);

       default void fallback(Exception e) {
           throw new CustomException("Payment Service is down", "UNAVAILABLE", 500);
       }
   }
   ```

3. **Configuration in `application.yaml`**:
   ```yaml
   resilience4j:
     circuitbreaker:
       instances:
         external:
           event-consumer-buffer-size: 10
           failure-rate-threshold: 50
           minimum-number-of-calls: 5
           automatic-transition-from-open-to-half-open-enabled: true
           wait-duration-in-open-state: 5s
           permitted-number-of-calls-in-half-open-state: 3
           sliding-window-size: 10
           sliding-window-type: COUNT_BASED
   ```

## Redis (Rate Limiter)

Redis is used to implement rate limiting in the API Gateway to prevent DDoS attacks. It limits the number of requests per user per second.

1. **Run Redis Docker**:
   ```bash
   docker run --name redis -d -p 6379:6379 redis
   ```

2. **Add Dependency**:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
   </dependency>
   ```

3. **Key Resolver Configuration**:
   ```java
   @Bean
   KeyResolver userKeyResolver() {
       return exchange -> Mono.just("userKey");
   }
   ```

4. **Rate Limiter Configuration in `application.yaml`**:
   ```yaml
   cloud:
     gateway:
       routes:
         - id: ORDERSERVICE
           uri: lb://ORDERSERVICE
           predicates:
             - Path=/order/**
           filters:
             - name: CircuitBreaker
               args:
                 name: ORDERSERVICE
                 fallbackUri: forward:/orderServiceFallBack
             - name: RequestRateLimiter
               args:
                 redis-rate-limiter.replenishRate: 1
                 redis-rate-limiter.burstCapacity: 1
         - id: PRODUCTSERVICE
           uri: lb://PRODUCTSERVICE
           predicates:
             - Path=/product/**
           filters:
             - name: CircuitBreaker
               args:
                 name: PRODUCTSERVICE
                 fallbackUri: forward:/productServiceFallBack
             - name: RequestRateLimiter
               args:
                 redis-rate-limiter.replenishRate: 1
                 redis-rate-limiter.burstCapacity: 1
         - id: PAYMENTSERVICE
           uri: lb://PAYMENTSERVICE
           predicates:
             - Path=/payment/**
           filters:
             - name: CircuitBreaker
               args:
                 name: PAYMENTSERVICE
                 fallbackUri: forward:/paymentServiceFallBack
             - name: RequestRateLimiter
               args:
                 redis-rate-limiter.replenishRate: 1
                 redis-rate-limiter.burstCapacity: 1
   ```

### Rate Limiter Parameters

- **`redis-rate-limiter.replenishRate`**: Defines how many requests per second a client is allowed (e.g., 1 request per second).
- **`redis-rate-limiter.burstCapacity`**: Defines the maximum number of requests a client can make in a short burst (e.g., 1 extra request).
