# Day 11 Summary: Inventory Mock Service

## Deliverables

✅ **InventoryController.java** - Mock inventory service with 7 endpoints  
✅ **MockServiceConfig.java** - Configuration with initialization logging  
✅ **application-mock.properties** - Service runs on port 9001  
✅ **Route Registration** - JSON payload for gateway integration

## Endpoints Created

| Endpoint | Purpose |
|----------|---------|
| `GET /api/inventory` | All products (10 items) |
| `GET /api/inventory/{id}` | Product by ID |
| `GET /api/inventory/category/{category}` | Products by category |
| `GET /api/inventory/{id}/stock` | Stock availability |
| `GET /api/inventory/slow` | Slow response (retry testing) |
| `GET /api/inventory/flaky` | Random failures (CB testing) |
| `GET /api/inventory/health` | Health check |

## Quick Start

### Start Mock Service
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mock
```

### Register Route in Gateway
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

### Test Through Gateway
```bash
curl http://localhost:8080/inventory
```

## Architecture

```
Client → Gateway (8080) → Inventory Service (9001)
           ↓
        Redis Routes
```

## Commit Ready

**Using Sahil's account to commit to Sahil's repo.**

Ready to commit with message:
```
implemented inventory mock service and define backend routes
```
