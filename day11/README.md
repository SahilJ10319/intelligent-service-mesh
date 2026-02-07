# Day 11: Inventory Mock Service Setup

## Overview
Created the first mock service to test NeuraGate's routing, circuit breaker, and retry capabilities.

## Mock Service Details

**Service Name**: Inventory Mock Service  
**Port**: 9001  
**Base Path**: `/api/inventory`

## Endpoints

### 1. Get All Inventory
```
GET http://localhost:9001/api/inventory
```
Returns all 10 mock products with details (name, price, stock, category).

### 2. Get Product by ID
```
GET http://localhost:9001/api/inventory/{id}
```
Example: `GET http://localhost:9001/api/inventory/1`

### 3. Get Products by Category
```
GET http://localhost:9001/api/inventory/category/{category}
```
Categories: Electronics, Accessories, Office

### 4. Check Stock Status
```
GET http://localhost:9001/api/inventory/{id}/stock
```
Returns stock availability (IN_STOCK, LOW_STOCK, OUT_OF_STOCK).

### 5. Slow Endpoint (Testing Retries)
```
GET http://localhost:9001/api/inventory/slow?delayMs=3000
```
Simulates slow response for testing retry patterns.

### 6. Flaky Endpoint (Testing Circuit Breakers)
```
GET http://localhost:9001/api/inventory/flaky?failureRate=50
```
Randomly fails to test circuit breaker behavior.

### 7. Health Check
```
GET http://localhost:9001/api/inventory/health
```

## Running the Mock Service

### Start Mock Service (Port 9001)
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run -Dspring-boot.run.profiles=mock
```

### Start Gateway (Port 8080)
```bash
# In a separate terminal
mvn spring-boot:run
```

## Registering Route in Gateway

### Via Admin API
```bash
curl -X POST http://localhost:8080/admin/routes \
  -H "Content-Type: application/json" \
  -d '{
    "id": "inventory-service",
    "uri": "http://localhost:9001",
    "path": "/inventory/**",
    "order": 1
  }'
```

### JSON Payload for Redis
```json
{
  "id": "inventory-service",
  "uri": "http://localhost:9001",
  "predicates": [
    {
      "name": "Path",
      "args": {
        "pattern": "/inventory/**"
      }
    }
  ],
  "filters": [
    {
      "name": "Retry",
      "args": {
        "retries": "3",
        "statuses": "BAD_GATEWAY,SERVICE_UNAVAILABLE",
        "methods": "GET,POST,PUT,DELETE",
        "exceptions": "java.net.ConnectException,java.io.IOException"
      }
    },
    {
      "name": "CircuitBreaker",
      "args": {
        "name": "dynamicRoute",
        "fallbackUri": "forward:/fallback/message"
      }
    },
    {
      "name": "StripPrefix",
      "args": {
        "parts": "1"
      }
    }
  ],
  "order": 1
}
```

## Testing Through Gateway

### Normal Request
```bash
# Through gateway (port 8080)
curl http://localhost:8080/inventory

# Direct to service (port 9001)
curl http://localhost:9001/api/inventory
```

### Test Retry Pattern
```bash
# Slow endpoint - should retry
curl http://localhost:8080/inventory/slow?delayMs=2000
```

### Test Circuit Breaker
```bash
# Make multiple failing requests
for i in {1..10}; do
  curl http://localhost:8080/inventory/flaky?failureRate=80
done

# Circuit should open, subsequent requests get fallback
curl http://localhost:8080/inventory
```

### Verify Routing
```bash
# Get specific product
curl http://localhost:8080/inventory/1

# Get by category
curl http://localhost:8080/inventory/category/Electronics

# Check stock
curl http://localhost:8080/inventory/1/stock
```

## Architecture

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Client    │────────▶│  NeuraGate       │────────▶│   Inventory     │
│             │         │  Gateway         │         │   Mock Service  │
│             │         │  (Port 8080)     │         │   (Port 9001)   │
└─────────────┘         └──────────────────┘         └─────────────────┘
                               │
                               │ Retry + Circuit Breaker
                               │ Dynamic Routing
                               ▼
                        ┌──────────────┐
                        │    Redis     │
                        │  Route Store │
                        └──────────────┘
```

## Files Created

- [`InventoryController.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/mock/InventoryController.java)
- [`MockServiceConfig.java`](file:///Users/applestore/Desktop/mesh/src/main/java/com/neuragate/mock/MockServiceConfig.java)
- [`application-mock.properties`](file:///Users/applestore/Desktop/mesh/src/main/resources/application-mock.properties)

## Next Steps

- Add more mock services (Orders, Users, etc.)
- Implement service discovery
- Add authentication/authorization
- Create integration tests
