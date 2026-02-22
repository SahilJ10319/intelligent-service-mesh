<div align="center">

# 🧠 NeuraGate

### Intelligent Service Mesh — AI-Driven API Gateway

*A production-grade, self-healing API gateway built with Java 21, Spring Cloud, and autonomous AI operations.*

---

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring_Cloud_Gateway-4.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-7.x-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-latest-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-RBAC-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)

</div>

---

## What is NeuraGate?

NeuraGate is an **intelligent, self-healing API gateway** that goes beyond simple request routing. It observes its own telemetry, detects anomalies in real-time, consults an AI advisor, and — when confidence is high enough — takes corrective action autonomously.

This project demonstrates enterprise-grade engineering concepts including **event-driven architecture**, **reactive programming with Project Loom**, **autonomous AI-driven operations**, and **production-ready observability**, all built on the modern Java 21 + Spring Cloud ecosystem.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 🤖 **Autonomous AI Advisor** | Rule-based heuristics (LLM-ready) generate structured recommendations; confidence-gated auto-execution applies circuit-breaker and rate-limit changes with a full audit trail |
| ⚡ **Virtual Thread Performance** | All I/O runs on Project Loom virtual threads — carrier-thread-safe, sub-millisecond overhead, scales to thousands of concurrent connections |
| 🔄 **Event-Driven Telemetry** | Every request publishes a `GatewayTelemetry` event to Kafka; a parallel consumer pipeline aggregates metrics, detects anomalies, and feeds the AI advisor |
| 🛡️ **Self-Healing Circuit Breakers** | Resilience4j circuit breakers trip on consecutive failures; the AI advisor lowers failure thresholds before they cascade |
| 📊 **Real-Time Dashboard** | Dark-themed SSE-powered UI at `/index.html`; live request feed, 6 metric cards, AI decision log — zero page refreshes |
| 🔐 **Reactive JWT RBAC** | Stateless `SecurityWebFilterChain` with three roles: `ADMIN`, `ADVISOR`, `VIEWER`; path-based rules enforce least-privilege access |
| 🔥 **Built-in Stress Tester** | `POST /admin/test/stress/start` fires 1,000 req/min at chaos endpoints to prove circuit breaker and telemetry behaviour under pressure |
| 📈 **Prometheus Integration** | 11 custom Micrometer gauges (`gateway.latency.*`, `gateway.error.rate`, `gateway.anomalies.total`) scraped by a co-located Prometheus container |
| 🗂️ **Redis Route Store** | Dynamic route definitions persisted in Redis — update routing rules at runtime without restarting the gateway |

---

## 🏗️ Architecture Overview

```
                          ┌──────────────────────────────────────────────┐
                          │              NeuraGate Mesh                  │
                          │                                              │
  Client Request  ──────► │  Spring Cloud Gateway (Virtual Threads)      │
                          │        │           │                          │
                          │   Rate Limiter   Circuit Breaker              │
                          │   (Redis token)  (Resilience4j)               │
                          │        │                                      │
                          │   Telemetry Producer ──► Kafka ──► Consumer  │
                          │                                      │        │
                          │                          MetricsBuffer        │
                          │                          AnomalyDetector      │
                          │                          AI Advisor           │
                          │                          ActionExecutor       │
                          │                                              │
                          │   Redis ◄─── Dynamic Routes                  │
                          │   Prometheus ◄─── Micrometer Gauges          │
                          │   Dashboard ◄─── SSE Stream                  │
                          └──────────────────────────────────────────────┘
```

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full Mermaid diagram and technical deep dive.

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21+ (for local development only)

### One-Command Startup

```bash
# Clone the repository
git clone https://github.com/SahilJ10319/intelligent-service-mesh.git
cd intelligent-service-mesh

# Start the entire mesh (Zookeeper → Kafka → Redis → Prometheus)
docker-compose up -d

# Build and run the gateway
./mvnw spring-boot:run
```

> The gateway starts on **http://localhost:8080**

### Verify the stack is healthy

```bash
# Gateway health
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Live dashboard
open http://localhost:8080/index.html

# Prometheus UI
open http://localhost:9090
```

---

## 🔐 Authentication (RBAC)

NeuraGate uses stateless JWT bearer tokens with three role levels.

### Get a token

```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"neuragate","password":"secret"}'
```

### Role Reference

| Role | Paths | Purpose |
|---|---|---|
| `ROLE_ADMIN` | `/admin/**` | Stress tests, chaos controls, system management |
| `ROLE_ADVISOR` | `/ai/analyze`, `/ai/audit-log`, `/ai/prompt` | AI analysis and recommendations |
| `ROLE_VIEWER` | `/dashboard/**`, `/ai/system-prompt` | Read-only metrics and dashboard |

### Call a secured endpoint

```bash
TOKEN="<your-jwt-here>"

# AI Analysis (requires ADVISOR+)
curl http://localhost:8080/ai/analyze \
  -H "Authorization: Bearer $TOKEN"

# Trigger stress test (requires ADMIN)
curl -X POST http://localhost:8080/admin/test/stress/start \
  -H "Authorization: Bearer $TOKEN"
```

---

## 🧪 AI Advisor Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/ai/analyze` | GET | Run full AI analysis of recent metrics |
| `/ai/analyze?autoExecute=true` | GET | Run analysis + auto-apply if confidence ≥ 80% |
| `/ai/audit-log` | GET | View all autonomous config changes |
| `/ai/prompt` | GET | Inspect the LLM prompt (debug) |
| `/ai/system-prompt` | GET | View the AI's role definition |

---

## 🔥 Stress Testing

```bash
# 1. (Optional) Configure chaos before the test
curl -X POST http://localhost:8080/admin/test/chaos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 30, "latencyMs": 200}'

# 2. Start the load generator (1,000 req/min for 60s)
curl -X POST http://localhost:8080/admin/test/stress/start \
  -H "Authorization: Bearer $TOKEN"

# 3. Watch live SSE progress
curl -N http://localhost:8080/admin/test/stress/events \
  -H "Authorization: Bearer $TOKEN"

# 4. Reset chaos after test
curl -X POST http://localhost:8080/admin/test/chaos/reset \
  -H "Authorization: Bearer $TOKEN"
```

---

## 📁 Project Structure

```
src/main/java/com/neuragate/
├── config/          # Route config, Kafka, rate limiter, circuit breaker
├── gateway/         # Core filter chain, telemetry producer, fallback
├── telemetry/       # Kafka consumer, MetricsBuffer, AnomalyDetector
├── ai/              # AiAdvisorService, ActionExecutor, ConfigUpdateEvent
├── dashboard/       # DashboardController, SseEmitterService
├── security/        # SecurityConfig, JwtAuthManager, Role enum
├── stresstesting/   # LoadTestService, StressTestController
├── mock/            # ChaosSettings, InventoryController, MockConfigController
├── health/          # GatewayHealthIndicator
└── repository/      # RedisRouteDefinitionRepository
```

---

## 🗓️ 30-Day Build Diary

| Days | Milestone |
|---|---|
| 1–5 | Project setup, Spring Cloud Gateway routing, Redis integration |
| 6–10 | Rate limiting, circuit breakers, health checks, Resilience4j |
| 11–15 | Mock chaos service, Kafka integration, event-driven telemetry |
| 16–19 | MetricsBuffer, telemetry consumer pipeline, real-time aggregation |
| 20–21 | Prometheus metrics (11 custom gauges), anomaly detection |
| 22–23 | AI Advisor core, prompt engineering, structured LLM response DTOs |
| 24 | Autonomous ActionExecutor with confidence-gated auto-execution |
| 25 | Real-time SSE dashboard with live request feed and AI decision log |
| 26 | Reactive JWT authentication (HMAC-SHA256, stateless) |
| 27–28 | RBAC (ADMIN/ADVISOR/VIEWER), built-in 1,000 req/min stress tester |
| 29–30 | Production docs, architecture deep dive, docker-compose polish |

---

## 🤝 Contributing

This project was built as a 30-day engineering demonstration. Issues, improvements, and pull requests are welcome.

---

<div align="center">
Built with ☕ and Project Loom by <a href="https://github.com/SahilJ10319">SahilJ10319</a>
</div>
