# Day 10: Retry Patterns with Exponential Backoff and Jitter

## Objective
Implement safe retry patterns using exponential backoff and jitter to handle transient failures gracefully without causing retry storms.

## What's New

### 1. Enhanced Retry Configuration

Updated [`application.properties`](file:///Users/applestore/Desktop/mesh/src/main/resources/application.properties) with comprehensive retry patterns:

#### Default Retry Configuration
```properties
resilience4j.retry.configs.default.max-attempts=3
resilience4j.retry.configs.default.wait-duration=500ms
resilience4j.retry.configs.default.enable-exponential-backoff=true
resilience4j.retry.configs.default.exponential-backoff-multiplier=2
resilience4j.retry.configs.default.enable-random-jitter=true
resilience4j.retry.configs.default.retry-exceptions=java.net.ConnectException,java.net.SocketTimeoutException,java.io.IOException
```

**Exponential Backoff Calculation**:
- Attempt 1: 500ms + jitter
- Attempt 2: 1000ms (500ms × 2) + jitter
- Attempt 3: 2000ms (1000ms × 2) + jitter

**Why Jitter?**
- Prevents thundering herd problem
- Distributes retry attempts over time
- Reduces load spikes on recovering services

#### Three Retry Instances

**dynamicRouteRetry** - For all dynamic routes:
```properties
max-attempts=3
wait-duration=500ms
exponential-backoff-multiplier=2
```

**backendRetry** - More aggressive for backend services:
```properties
max-attempts=4
wait-duration=300ms
exponential-backoff-multiplier=2
```

**criticalRetry** - Conservative for critical services:
```properties
max-attempts=2
wait-duration=1000ms
exponential-backoff-multiplier=1.5
```

### 2. RetryEventLogger

Created [`RetryEventLogger.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/config/RetryEventLogger.java) for comprehensive retry logging:

**Features**:
- Logs all retry attempts with attempt number
- Different log levels based on attempt number
- Tracks success after retry
- Logs exhausted retries (all attempts failed)
- Tracks ignored errors (non-retryable)

**Log Examples**:
```
🔄 Retry 'dynamicRouteRetry' - Attempt #1 after 500ms - ConnectException: Connection refused
⚠️  Retry 'dynamicRouteRetry' - Attempt #2 after 1000ms - ConnectException: Connection refused
🚨 Retry 'dynamicRouteRetry' - Attempt #3 after 2000ms - ConnectException: Connection refused
✅ Retry 'dynamicRouteRetry' succeeded after 2 attempt(s)
❌ Retry 'dynamicRouteRetry' failed after 3 attempt(s): Connection refused
```

### 3. Automatic Retry Filter Injection

Enhanced [`RedisRouteDefinitionRepository.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/repository/RedisRouteDefinitionRepository.java) to inject Retry filters:

**Filter Order** (critical!):
1. **Retry** (outermost) - Handles transient failures
2. **CircuitBreaker** (inner) - Prevents cascading failures

**Why This Order?**
- Retry wraps CB to retry when CB is closed
- CB protects against retry storms
- Retry handles transient failures, CB handles persistent failures

**Retry Filter Configuration**:
```java
retryFilter.addArg("retries", "3");
retryFilter.addArg("statuses", "BAD_GATEWAY,SERVICE_UNAVAILABLE");
retryFilter.addArg("methods", "GET,POST,PUT,DELETE");
retryFilter.addArg("exceptions", "java.net.ConnectException,java.io.IOException");
```

## Retry Comparison

| Instance | Max Attempts | Initial Wait | Multiplier | Use Case |
|----------|--------------|--------------|------------|----------|
| dynamicRouteRetry | 3 | 500ms | 2.0 | All dynamic routes |
| backendRetry | 4 | 300ms | 2.0 | Backend services (more aggressive) |
| criticalRetry | 2 | 1000ms | 1.5 | Critical services (conservative) |

## Exponential Backoff Visualization

### dynamicRouteRetry (500ms base, 2x multiplier)
```
Attempt 1: 500ms  + jitter (0-250ms)  = 500-750ms
Attempt 2: 1000ms + jitter (0-500ms)  = 1000-1500ms
Attempt 3: 2000ms + jitter (0-1000ms) = 2000-3000ms
Total: ~3.5-5.25 seconds
```

### backendRetry (300ms base, 2x multiplier)
```
Attempt 1: 300ms  + jitter = 300-450ms
Attempt 2: 600ms  + jitter = 600-900ms
Attempt 3: 1200ms + jitter = 1200-1800ms
Attempt 4: 2400ms + jitter = 2400-3600ms
Total: ~4.5-6.75 seconds
```

### criticalRetry (1000ms base, 1.5x multiplier)
```
Attempt 1: 1000ms + jitter = 1000-1500ms
Attempt 2: 1500ms + jitter = 1500-2250ms
Total: ~2.5-3.75 seconds
```

## Testing

### Create Dynamic Route
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run

# Create route
curl -X POST http://localhost:8080/admin/routes \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-retry",
    "uri": "http://httpbin.org",
    "path": "/test/**",
    "order": 1
  }'
```

### Verify Filter Injection
Check logs for:
```
Injected retry filter into route 'test-retry'
Injected circuit breaker filter into route 'test-retry'
```

### Trigger Retry
```bash
# Request that will fail transiently
curl http://localhost:8080/test/status/503
```

### Observe Retry Attempts
Watch logs for:
```
🔄 Retry 'dynamicRouteRetry' - Attempt #1 after 500ms
⚠️  Retry 'dynamicRouteRetry' - Attempt #2 after 1000ms
🚨 Retry 'dynamicRouteRetry' - Attempt #3 after 2000ms
```

### Verify Success After Retry
If service recovers:
```
✅ Retry 'dynamicRouteRetry' succeeded after 2 attempt(s)
```

### Verify Circuit Breaker Integration
If retries exhausted, circuit breaker opens:
```
❌ Retry 'dynamicRouteRetry' failed after 3 attempt(s)
⚠️  Circuit breaker 'dynamicRoute' opened: CLOSED → OPEN
```

## Architecture Decisions

### 1. Exponential Backoff with Jitter

**Decision**: Enable both exponential backoff and random jitter

**Rationale**:
- Exponential backoff reduces load on failing services
- Jitter prevents thundering herd
- Combination provides optimal retry behavior

### 2. Filter Order (Retry → CircuitBreaker)

**Decision**: Retry wraps CircuitBreaker

**Rationale**:
- Retry handles transient failures
- CB handles persistent failures
- Retry can retry when CB is closed
- CB prevents retry storms

### 3. Exception-Specific Retry

**Decision**: Only retry specific exceptions

**Rationale**:
- Don't retry business logic errors (4xx)
- Only retry network/infrastructure errors
- Prevents unnecessary retries
- Faster failure for non-retryable errors

### 4. Different Retry Profiles

**Decision**: Three retry instances with different configurations

**Rationale**:
- Critical services need conservative retries
- Backend services can be more aggressive
- Dynamic routes get balanced approach

## Integration with Previous Days

**Day 8**: Built circuit breaker foundation  
**Day 9**: Extended to all dynamic routes  
**Day 10**: Added retry patterns for transient failures

## Files Created/Modified

### Main Project
- [`RetryEventLogger.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/config/RetryEventLogger.java) - New
- [`RedisRouteDefinitionRepository.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/repository/RedisRouteDefinitionRepository.java) - Updated
- [`application.properties`](file:///Users/applestore/Desktop/mesh/src/main/resources/application.properties) - Updated

## Commit Message
```
implemented Resilience4j retries with exponential backoff and jitter
```

## Next Steps (Day 11+)
- Add rate limiting
- Implement bulkhead pattern
- Create retry metrics dashboard
- Add integration tests for retry behavior
