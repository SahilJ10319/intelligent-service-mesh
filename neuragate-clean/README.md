# NeuraGate - Intelligent Service Mesh

High-concurrency API Gateway built with Java 21 Virtual Threads, designed for managing traffic between upstream consumers and downstream services with AI-powered routing optimization.

## Core Architecture

### Technology Stack
- **Java 21** - Virtual Threads for massive concurrency
- **Spring Boot 3.4** - Modern reactive framework
- **Spring Cloud Gateway** - Dynamic routing engine
- **Netflix DGS** - GraphQL federation
- **Resilience4j** - Circuit breakers and retry policies
- **Redis** - Distributed route storage
- **Apache Kafka** - Async telemetry streaming
- **Ollama (Llama 3.2)** - AI advisory routing via Spring AI

### Key Features

#### 1. Virtual Threads
Enabled via `spring.threads.virtual.enabled=true`. Each request gets its own lightweight virtual thread, allowing us to handle tens of thousands of concurrent connections without traditional thread pool overhead.

#### 2. Dynamic Routing
Routes are stored in Redis and can be modified at runtime without redeployment. External systems (including the AI advisory service) can modify routes by writing to Redis.

#### 3. Anti-Fragile Design
If Redis becomes unavailable, the gateway automatically falls back to an in-memory cache of critical routes. The system degrades gracefully rather than failing completely.

#### 4. Circuit Breakers
Resilience4j circuit breakers protect downstream services from cascading failures. When a service is unhealthy, requests fail fast with a degraded response instead of timing out.

#### 5. Exponential Backoff with Jitter
Retry policies use exponential backoff with random jitter to prevent thundering herd problems when services recover.

## Project Structure Draft

```
com.neuragate/
├── NeuraGateApplication.java          # Main entry point
├── gateway/
│   ├── config/
│   │   ├── GatewayConfig.java         # Gateway route locator wiring
│   │   ├── RedisConfig.java           # Reactive Redis template
│   │   └── ResilienceConfig.java      # Circuit breaker registries
│   ├── controller/
│   │   ├── FallbackController.java    # Circuit breaker fallback endpoint
│   │   └── RouteAdminController.java  # Admin API for route management
│   ├── model/
│   │   ├── RouteDefinition.java       # Route configuration model
│   │   └── TelemetryEvent.java        # Kafka telemetry event
│   ├── repository/
│   │   ├── RouteRepository.java       # Route storage interface
│   │   ├── RedisRouteRepository.java  # Redis implementation
│   │   └── InMemoryRouteRepository.java # Fallback implementation
│   └── service/
│       └── DynamicRouteService.java   # Core routing logic
```



## License

MIT
