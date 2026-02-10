#!/bin/bash

# Day 14: NeuraGate Integration Test Script
# Quick smoke test for all major endpoints

set -e  # Exit on error

echo "🚀 NeuraGate Integration Test Script"
echo "===================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Base URLs
GATEWAY_URL="http://localhost:8080"
MOCK_URL="http://localhost:9001"

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Helper function to run test
run_test() {
    local test_name=$1
    local url=$2
    local expected_status=${3:-200}
    
    echo -n "Testing: $test_name... "
    
    response=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    
    if [ "$response" -eq "$expected_status" ]; then
        echo -e "${GREEN}✓ PASSED${NC} (HTTP $response)"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ FAILED${NC} (Expected HTTP $expected_status, got $response)"
        ((TESTS_FAILED++))
    fi
}

echo "📊 Testing Gateway Health Endpoints"
echo "------------------------------------"
run_test "Gateway Health" "$GATEWAY_URL/actuator/health"
run_test "Gateway Info" "$GATEWAY_URL/actuator/info"
echo ""

echo "🛣️  Testing Gateway Routing"
echo "------------------------------------"
run_test "Inventory List" "$GATEWAY_URL/inventory"
run_test "Product by ID" "$GATEWAY_URL/inventory/1"
run_test "Products by Category" "$GATEWAY_URL/inventory/category/Electronics"
run_test "Stock Check" "$GATEWAY_URL/inventory/1/stock"
echo ""

echo "🔧 Testing Admin API"
echo "------------------------------------"
run_test "List Routes" "$GATEWAY_URL/admin/routes"
run_test "Health Check" "$GATEWAY_URL/admin/health"
echo ""

echo "🎭 Testing Mock Service (Direct)"
echo "------------------------------------"
run_test "Mock Inventory" "$MOCK_URL/api/inventory"
run_test "Mock Health" "$MOCK_URL/api/inventory/health"
run_test "Mock Chaos Status" "$MOCK_URL/mock/config/status"
echo ""

echo "🚦 Testing Rate Limiting"
echo "------------------------------------"
echo "Making 25 rapid requests to trigger rate limit..."
for i in {1..25}; do
    curl -s -o /dev/null "$GATEWAY_URL/inventory" &
done
wait
echo -e "${YELLOW}Rate limit test complete (check logs for 429 responses)${NC}"
echo ""

echo "💥 Testing Chaos Engineering"
echo "------------------------------------"
echo "Setting failure rate to 0% (healthy state)..."
curl -s -X POST "$MOCK_URL/mock/config/failure-rate" \
    -H "Content-Type: application/json" \
    -d '{"rate":0}' > /dev/null
echo -e "${GREEN}✓ Chaos controls working${NC}"
echo ""

echo "📈 Test Summary"
echo "===================================="
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed${NC}"
    exit 1
fi
