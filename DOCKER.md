# NeuraGate Docker Infrastructure

This directory contains Docker Compose configuration for NeuraGate's infrastructure dependencies.

## Services

### Kafka (Port 9092)
Event streaming platform for telemetry and observability.
- **Topics**: gateway-telemetry, gateway-errors, gateway-routes
- **Partitions**: 3 (telemetry), 2 (errors), 1 (routes)
- **Retention**: 7 days (telemetry), 30 days (errors/routes)

### Zookeeper (Port 2181)
Coordination service for Kafka cluster.

### Redis (Port 6379)
In-memory data store for:
- Dynamic route definitions
- Rate limiting token buckets
- Circuit breaker state

### Kafka UI (Port 8090)
Web interface for Kafka management and monitoring.
- View topics, partitions, messages
- Monitor consumer groups
- Inspect message payloads

## Quick Start

```bash
# Start all services
docker-compose up -d

# Check service health
docker-compose ps

# View logs
docker-compose logs -f kafka

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

## Service URLs

- **Kafka**: localhost:9092
- **Kafka UI**: http://localhost:8090
- **Redis**: localhost:6379
- **Zookeeper**: localhost:2181

## Health Checks

All services include health checks:
- Kafka: Broker API versions check
- Redis: PING command
- Zookeeper: Port connectivity check

## Production Considerations

For production deployments:
1. Increase Kafka replication factor to 3
2. Use external Zookeeper ensemble
3. Enable Kafka authentication (SASL/SSL)
4. Configure Redis persistence (AOF/RDB)
5. Set up monitoring (Prometheus/Grafana)
6. Use managed services (AWS MSK, Redis ElastiCache)
