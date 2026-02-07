# Day 6: Graceful Degradation and Fail-Safe Routing

## Objective
Implement reactive health monitoring and fail-safe fallback routing to make the gateway "self-aware" of its database health with a plan for when Redis fails.

## What's New

### 1. GatewayHealthIndicator.java (New)
Custom reactive health indicator that monitors gateway state:

**Location**: `src/main/java/com/neuragate/health/GatewayHealthIndicator.java`

**Features**:
- Reactive Redis connectivity check using PING command
- 2-second timeout for health checks
- Three health states:
  - **UP**: Redis connected, dynamic routing active
  - **DEGRADED**: Redis down, using fallback routes
  - **DOWN**: Complete failure
- Detailed health information for observability

**Why Reactive?**
- Non-blocking health checks preserve Virtual Thread benefits
- Can handle thousands of concurrent health check requests
- Integrates seamlessly with Spring Cloud Gateway's reactive stack

### 2. RouteConfig.java (Updated)
Enhanced fallback routes with comprehensive emergency routing:

**Emergency Routes** (3 critical routes):
1. **Health Endpoint** (`/actuator/health/**`) - Always available for monitoring
2. **Maintenance Page** (`/status/**`) - Inform users of degraded state
3. **Critical Auth Service** (`/auth/**`) - Maintain security in degraded mode

**Why These Routes?**
- Health: Monitoring must work even when Redis is down
- Maintenance: Users need to know the system is degraded
- Auth: Security cannot be compromised

### 3. application.properties (Updated)
Health monitoring configuration:

```properties
# Show full health details
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always

# Enable health endpoint
management.endpoints.web.exposure.include=health,info,metrics

# Redis health check
management.health.redis.enabled=true
spring.data.redis.timeout=2000ms
```

### 4. pom.xml (Updated)
Added Spring Boot Actuator dependency for health monitoring.

## Testing

### Start Dependencies
```bash
# Start Redis
docker-compose up -d redis

# Start gateway
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run
```

### Test Health Monitoring

**1. Check health when Redis is UP**:
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "gateway": {
      "status": "UP",
      "details": {
        "redis": "connected",
        "routing": "dynamic (Redis-backed)",
        "fallback": "available"
      }
    }
  }
}
```

**2. Stop Redis to test degraded mode**:
```bash
docker-compose stop redis
```

**3. Check health when Redis is DOWN**:
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "DEGRADED",
  "components": {
    "gateway": {
      "status": "DEGRADED",
      "details": {
        "redis": "disconnected",
        "routing": "fallback (in-memory emergency routes)",
        "fallback": "active",
        "warning": "Dynamic routing unavailable"
      }
    }
  }
}
```

**4. Test fallback routes still work**:
```bash
# Health endpoint should still work
curl http://localhost:8080/actuator/health

# Emergency routes should be active
curl http://localhost:8080/status/get
curl http://localhost:8080/auth/get
```

**5. Restart Redis**:
```bash
docker-compose start redis
```

**6. Verify automatic recovery**:
```bash
curl http://localhost:8080/actuator/health
# Should show "UP" again
```

## Architecture Decisions

### 1. Custom Health Indicator vs. Default
**Decision**: Create custom `GatewayHealthIndicator`

**Why?**
- Default Redis health indicator doesn't understand gateway routing context
- Need to report DEGRADED state (not just UP/DOWN)
- Want to show which routing mode is active
- Provides gateway-specific health details

### 2. Three Emergency Routes
**Decision**: Health, Maintenance, Auth

**Why?**
- **Minimal but sufficient**: Covers monitoring, user communication, and security
- **Prioritized**: Health (order 0), Maintenance (order 1), Auth (order 2)
- **Replaceable**: Uses httpbin.org as placeholder for actual services

### 3. DEGRADED vs. DOWN
**Decision**: Report DEGRADED when Redis is down, not DOWN

**Why?**
- Gateway is still functional (using fallback routes)
- Load balancers won't remove gateway from rotation
- Monitoring systems can differentiate between degraded and failed
- Users can still access critical services

### 4. 2-Second Health Check Timeout
**Decision**: Short timeout for Redis PING

**Why?**
- Health checks should be fast
- Slow Redis is effectively down for routing purposes
- Prevents health endpoint from becoming slow
- Allows quick failover to fallback routes

## Integration with Previous Days

**Day 4**: Uses existing `RedisRouteDefinitionRepository` with fallback logic  
**Day 5**: Health status visible via admin monitoring  
**Day 6**: Adds self-awareness and graceful degradation

## What's NOT Included (Coming Later)

- Circuit breakers (Week 2)
- Kafka telemetry (Week 2)
- AI-driven health analysis (Phase 3)
- Automated failover triggers

## Commit Message
```
Day 6: implemented reactive health monitoring and fail-safe fallback routing

- Add GatewayHealthIndicator with reactive Redis health checking
- Enhance RouteConfig with 3 emergency fallback routes
- Configure health endpoint with full details
- Add Spring Boot Actuator dependency
```

## Next Steps (Day 7+)
- Add metrics collection
- Implement health-based alerting
- Create dashboard for health visualization
- Add automated recovery testing
