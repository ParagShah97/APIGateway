# API Gateway (9090)

There can be many API calls from the various devices to the services (e.g.: Payment, Order or Product), therefore we need a gateway which can track, authenticate, control the incoming APIs calls. Gateway will be the single point of source for all the internal services, it will identify and and route the call to the respective services. 

Created a spring project using spring initializer, with dependencies are:
1. Spring Reactive Web (Uses WebFlux for: High-Concurrency Applications)
2. Sprint Boot Actuator (Supports built in (or custom) endpoints that let you monitor and manage your application)
3. Lombok 
4. Cloud bootstrap 
5. Eureka Client   (This will be configured as eureka client for service registry (Eureka Server))
6. Config Client (This is for config server to get the common configuration)
7. Zipkin (This will enable to connect with the centralized debugging server) 
8. Reactive Gateway (Provides effective way to route to APIs in Servlet based application.)
9. Resilience4J (Have to add reactive manually at the end of the dependency)

We need to add the properties in `application.yaml` file.
 
`server:`  
  `port: 9090`  
  
`spring:`  
  `application:`  
    `name: CloudGateway`  
   `#Config server: it's another service which give common config.`  
  `config:`  
    `import: configserver:http://localhost:9296`  
  
   `#Cloud coonfiguration for gateway to route the API calls`  
  `cloud:`  
    `gateway:`  
      `routes:`  
        `- id: ORDERSERVICE`  # <- Need to provide application name as ID
          `uri: lb://ORDERSERVICE`  # <- Need to provide  as loadbalance://application name
          `predicates:`  
            `- Path=/order/**`  
        `- id: PRODUCTSERVICE`  
          `uri: lb://PRODUCTSERVICE`  
          `predicates:`  
            `- Path=/product/**`  
        `- id: PAYMENTSERVICE`  
          `uri: lb://PAYMENTSERVICE`  
          `predicates:`  
            `- Path=/payment/**`  
  
`#Zipkin configuration`
`management:`  
  `tracing:`  
    `sampling:`  
      `probability: 1.0`  
  `zipkin:`  
    `tracing:`  
      `endpoint: http://localhost:9411/api/v2/spans`

## Circuit Breaker

Ref: https://www.baeldung.com/resilience4j

This is a design pattern, which can be used for the scenario where any one or more services are down. We will use Resilence4j library.
**Circuit breaker have different states to handle different scenarios.**
Consider we are calling

API Gateway ---> Order Service ---> Product service.

To handle if the Order service down scenario we need to add the circuit breaker at API Gateway level. Similarly to handle for Product Service down scenario we need to add the circuit breaker at Order service level.

Closed ---> Open <---> Half Open ---> Closed
**Closed state**: Health

**Open State:** Consider order service went down, we can define the number of request failed after that the closed circuit change to open state (ex: consider 10 requests are failing). Also there will be timer like consider 10 seconds after that automatically state change from open to half open.

**Half Open:** This will only allow half of the request to pass through other are blocked. Further from the passed requests if the successful count of request is > threshold percentage then we can make the state to closed again, else again we need to move the state to open, as still the failed service not accepting and serving the requests.

Note: We can add the fallback method at the time of open state.

### How to use circuit breaker in API Gateway:
Add dependency to pom.xml for Resilience4j.

`<dependency>`  
    `<groupId>org.springframework.cloud</groupId>`  
    `<artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>`  
`</dependency>`

Add the properties to `application.yaml` file.

`cloud:`  
  `gateway:`  
    `routes:`  
      `- id: ORDERSERVICE`  
        `uri: lb://ORDERSERVICE`  
        `predicates:`  
          `- Path=/order/**`  
        `filters:`                                      # <-- Start here for Circuit Breaker added as filter.
          `- name: CircuitBreaker`  
            `args:`  
              `name: ORDERSERVICE`  
              `fallbackuri: forward:/orderServiceFallBack`  # <--- End here  **fallback URL**
      `- id: PRODUCTSERVICE`  
        `uri: lb://PRODUCTSERVICE`  
        `predicates:`  
          `- Path=/product/**`  
        `filters:`  
          `- name: CircuitBreaker`  
            `args:`  
              `name: PRODUCTSERVICE`  
              `fallbackuri: forward:/productServiceFallBack`  
      `- id: PAYMENTSERVICE`  
        `uri: lb://PAYMENTSERVICE`  
        `predicates:`  
          `- Path=/payment/**`  
        `filters:`  
          `- name: CircuitBreaker`  
            `args:`  
              `name: PAYMENTSERVICE`  
              `fallbackuri: forward:/paymentServiceFallBack`
    
Note: For fallbackuri: we need to provide `forward:/<Mapping for API when state is open>`

Controller: Path : src/main/java/com/parag/CloudGateway/controller/FallBackController.java

`@GetMapping("/orderServiceFallBack")`  
`public String orderServiceFallBack() {`  
    `return "Order Service is down!";`  
`}`

Moreover we need to add the Bean in the Application file, to inform Spring that we added configuration for circuit breaker.

`@Bean`  
`public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {`  
    `return factory -> factory.configureDefault(`  
          `id -> new Resilience4JConfigBuilder(id)`  
                `.circuitBreakerConfig(`  
                      `CircuitBreakerConfig.ofDefaults()`  
                `).build()`  
    `);`  
`}`

### How to use circuit breaker in any other service:

We need to add the dependency in pom.xml file.

`<dependency>`
      `<groupId>org.springframework.cloud</groupId>`
      `<artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>`
`</dependency>`

For example from order service we are calling Product and Payment service, so we need to add the circuit breaker at Feign client level. We know that in order service we declared and defined Feign client in external folder. So we can add the circuit breaker at Feign client.
{Path: src/main/java/com/parag/OrderService/external/client/PaymentService.java}


`@CircuitBreaker(name = "external", fallbackMethod = "fallback")`  
`@FeignClient(name="PaymentService/payment")`  
`public interface PaymentService {`  
    `@PostMapping`  
    `ResponseEntity<Long> makePayment(@RequestBody PaymentRequest paymentRequest);`  
    
     // This fallback method will be used by circuit breaker in case payment service is down.    
    default void fallback(Exception e) { 
        throw new CustomException("Payment Service is down", "UNAVAILABLE", 500); 
    }  
`}`

Note: We need to add the annotation `@CircuitBreaker(name, fallbackMethod)` and in case of service in available the fallback method will return an exception.

In Addition to this we need to add more configuration to the `application.yaml` file in order service.

`# Configuration of resilience4j  
`resilience4j:`  
  `circuitbreaker:`  
    `instances:`  
      `external:`  
        `event-consumer-buffer-size: 10`  
        `failure-rate-threshold: 50`  
        `minimum-number-of-calls: 5`  
        `automatic-transition-from-open-to-half-open-enabled: true`  
        `wait-duration-in-open-state: 5s`  
        `permitted-number-of-calls-in-half-open-state: 3`  
        `sliding-window-size: 10`  
        `sliding-window-type: COUNT_BASED`

### Redis (Rate limiter)
With the help of Redis we will add the rate limiter to the API gateway. This will limit the API inflow and prevent attacks like DDos. Here we will allow how many request per user per second. Rate limiting will be handled used Redis and Resilience4j library.

Redis docker:
`docker run --name redis -d -p 6379:6379 redis`

Add the Redis dependency to the pom.xml file in API Gateway.

`<dependency>`
    `<groupId>org.springframework.boot</groupId>`
    `<artifactId>spring-boot-starter-data-redis-reactive</artifactId>`
`</dependency>`

In Application file, we need to add the configuration for using the rate limiter for user key.
Here currently it is maintain only for one user key, but later if there are 100s of user then it will maintain 100s of user key and track the number of request per users.

{Path: src/main/java/com/parag/CloudGateway/CloudGatewayApplication.java}
`// Return KeyResolver instance: for rate limiter purpose.`  
`@Bean`  
`KeyResolver userKeyResolver() {`  
    `// Currently we don't have users so only one generic key "userKey" is`   
    `// specified. Later we will fix that.`  
    `return exchange -> Mono.just("userKey");`  
`}`

Now, we need to add the configuration for rate limiter in application.yaml file in API gateway.
{Path: src/main/resources/application.yaml}

`cloud:`  
  `gateway:`  
    `routes:`  
      `- id: ORDERSERVICE`  
        `uri: lb://ORDERSERVICE`  
        `predicates:`  
          `- Path=/order/**`  
        `filters:`  
          `- name: CircuitBreaker`  
            `args:`  
              `name: ORDERSERVICE`  
              `fallbackUri: forward:/orderServiceFallBack`  
          `- name: RequestRateLimiter`  
            `args:`  
              `redis-rate-limiter.replenishRate: 1`  
              `redis-rate-limiter.burstCapacity: 1`  
      `- id: PRODUCTSERVICE`  
        `uri: lb://PRODUCTSERVICE`  
        `predicates:`  
          `- Path=/product/**`  
        `filters:`  
          `- name: CircuitBreaker`  
            `args:`  
              `name: PRODUCTSERVICE`  
              `fallbackUri: forward:/productServiceFallBack`  
          `- name: RequestRateLimiter`  
            `args:`  
              `redis-rate-limiter.replenishRate: 1`  
              `redis-rate-limiter.burstCapacity: 1`  
      `- id: PAYMENTSERVICE`  
        `uri: lb://PAYMENTSERVICE`  
        `predicates:`  
          `- Path=/payment/**`  
        `filters:`  
          `- name: CircuitBreaker`  
            `args:`  
              `name: PAYMENTSERVICE`  
              `fallbackUri: forward:/paymentServiceFallBack`  
          `- name: RequestRateLimiter`  
            `args:`  
              `redis-rate-limiter.replenishRate: 1`  
              `redis-rate-limiter.burstCapacity: 1`
    


**`redis-rate-limiter.replenishRate`**
- Defines **how many requests per second** a client is allowed.
- This means **1 new request is allowed per second**.
- If the client makes more than 1 request per second, it will start consuming from the **burst capacity**.

**`redis-rate-limiter.burstCapacity`**
- Defines **the maximum number of requests a client can make in a short burst** before being throttled.
- The client can store **only 1 extra request** beyond the normal rate (`replenishRate`).
- This acts as a **bucket** that temporarily holds excess requests.
