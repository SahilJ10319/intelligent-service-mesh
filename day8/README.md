# Day 8: Resilience4j Circuit Breaker Integration

## Objective
Integrate Resilience4j circuit breakers to protect the gateway from failing downstream services with graceful degradation.

## What's New

### 1. Circuit Breaker Named Instances

Updated [`application.properties`](file:///Users/applestore/Desktop/mesh/src/main/resources/application.properties#L48-L67) with two named circuit breaker instances:

#### backendService Circuit Breaker
```properties
resilience4j.circuitbreaker.instances.backendService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.backendService.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.backendService.sliding-window-size=10
resilience4j.circuitbreaker.instances.backendService.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.backendService.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.backendService.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.backendService.automatic-transition-from-open-to-half-open-enabled=true
```

**Configuration Explained**:
- **50% failure rate threshold**: Opens circuit if 50% of requests fail
- **10s wait in open state**: Waits 10 seconds before trying again
- **10 request sliding window**: Tracks last 10 requests
- **5 minimum calls**: Needs at least 5 calls before calculating failure rate
- **3 half-open test calls**: Allows 3 test requests when recovering
- **Auto transition**: Automatically moves from OPEN to HALF_OPEN

#### criticalService Circuit Breaker
```properties
resilience4j.circuitbreaker.instances.criticalService.failure-rate-threshold=70
resilience4j.circuitbreaker.instances.criticalService.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.criticalService.sliding-window-size=20
resilience4j.circuitbreaker.instances.criticalService.minimum-number-of-calls=10
```

**Why More Lenient?**
- Critical services get more tolerance (70% vs 50%)
- Longer recovery time (30s vs 10s)
- Larger sliding window (20 vs 10)
- More calls needed before opening (10 vs 5)

### 2. FallbackController

Created [`FallbackController.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/gateway/controller/FallbackController.java) with three fallback endpoints:

#### `/fallback/message` - Generic Fallback
Returns 503 Service Unavailable with helpful message when circuit breaker opens.

#### `/fallback/backend` - Backend Service Fallback
Specific messaging for backend service failures with cached data suggestion.

#### `/fallback/critical` - Critical Service Fallback
Emphasizes temporary nature and automatic recovery for critical services.

**Why Multiple Fallbacks?**
- Different services need different messaging
- Allows service-specific degradation strategies
- Better observability (can track which services are failing)

### 3. Enhanced RouteConfig

Updated [`RouteConfig.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/config/RouteConfig.java#L47-L103) to add circuit breaker filters:

**Maintenance Route**: Uses `backendService` circuit breaker → `/fallback/backend`  
**Health Route**: No circuit breaker (always available)  
**Auth Route**: Uses `criticalService` circuit breaker → `/fallback/critical`

## Circuit Breaker States

### CLOSED (Normal Operation)
- All requests pass through
- Tracking failure rate
- If failure rate exceeds threshold → OPEN

### OPEN (Service Failing)
- All requests immediately fail
- Redirected to fallback endpoint
- After wait duration → HALF_OPEN

### HALF_OPEN (Testing Recovery)
- Limited test requests allowed (3 for backendService)
- If tests succeed → CLOSED
- If tests fail → OPEN

## Testing

### Start Dependencies
```bash
# Start Redis
docker-compose up -d redis

# Start gateway
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run
```

### Test Circuit Breaker

**1. Test normal operation**:
```bash
# Should work normally
curl http://localhost:8080/status/get
```

**2. Simulate service failure**:
```bash
# Make multiple failing requests to trigger circuit breaker
for i in {1..10}; do
  curl http://localhost:8080/status/delay/10  # Will timeout
done
```

**3. Verify circuit breaker opened**:
```bash
# Should get fallback response immediately (no timeout)
curl http://localhost:8080/status/get
```

Expected fallback response:
```json
{
  "status": "degraded",
  "message": "Backend service is currently experiencing issues...",
  "timestamp": "2026-02-04T00:00:00Z",
  "service": "backend",
  "action": "Circuit breaker protection active"
}
```

**4. Test critical service fallback**:
```bash
curl http://localhost:8080/auth/get
```

**5. Check circuit breaker metrics**:
```bash
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls
```

## Architecture Decisions

### 1. Two Circuit Breaker Profiles

**Decision**: Create separate profiles for backend and critical services

**Rationale**:
- Critical services need more tolerance
- Different recovery strategies
- Better observability

### 2. Fast Failure with Fallback

**Decision**: Fail fast and redirect to fallback instead of timeout

**Rationale**:
- Better user experience (immediate response vs 30s timeout)
- Reduces load on failing services
- Preserves Virtual Thread resources

### 3. Automatic Transition to Half-Open

**Decision**: Enable automatic transition for backend services

**Rationale**:
- Faster recovery when service comes back
- No manual intervention needed
- Reduces downtime

### 4. Count-Based Sliding Window

**Decision**: Use COUNT_BASED instead of TIME_BASED

**Rationale**:
- More predictable behavior
- Easier to reason about
- Better for low-traffic scenarios

## Integration with Previous Days

**Day 6**: Circuit breakers complement health monitoring  
**Day 7**: Tests verify circuit breaker behavior  
**Day 8**: Adds protection layer for downstream services

## What's NOT Included (Coming Later)

- Retry policies (Week 2)
- Rate limiting (Week 2)
- Bulkhead pattern (Week 3)
- Time limiter (Week 3)

## Commit Message
```
integrated Resilience4j and implement initial circuit breaker filters
```

## Next Steps (Day 9+)
- Add retry policies with exponential backoff
- Implement rate limiting
- Create circuit breaker dashboard
- Add integration tests for circuit breaker
