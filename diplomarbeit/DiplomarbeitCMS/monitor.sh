#!/bin/bash

# CMS Microservices Monitoring Script
# Provides health checks and status monitoring for the CMS application

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== CMS Microservices Health Monitor ===${NC}"
echo ""

# Function to check if a service is running
check_service_status() {
    local service_name=$1
    local container_name=$2
    
    if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "$container_name"; then
        local status=$(docker ps --format "table {{.Names}}\t{{.Status}}" | grep "$container_name" | awk '{print $2, $3}')
        echo -e "${GREEN}✓${NC} $service_name: $status"
        return 0
    else
        echo -e "${RED}✗${NC} $service_name: Not running"
        return 1
    fi
}

# Function to test API endpoints
test_endpoint() {
    local name=$1
    local url=$2
    local expected_status=${3:-200}
    
    if command -v curl >/dev/null 2>&1; then
        local response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$url" 2>/dev/null || echo "HTTPSTATUS:000")
        local status=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
        
        if [ "$status" = "$expected_status" ]; then
            echo -e "${GREEN}✓${NC} $name: Healthy (HTTP $status)"
        else
            echo -e "${RED}✗${NC} $name: Unhealthy (HTTP $status)"
        fi
    else
        echo -e "${YELLOW}⚠${NC} $name: Cannot test (curl not available)"
    fi
}

# Function to check database status
check_database() {
    echo -e "\n${BLUE}Database Status:${NC}"
    
    if [ -f "./documents.db" ]; then
        local size=$(du -h "./documents.db" | cut -f1)
        echo -e "${GREEN}✓${NC} SQLite Database: Present ($size)"
        
        # Check if we can connect to database (if sqlite3 is available)
        if command -v sqlite3 >/dev/null 2>&1; then
            local doc_count=$(sqlite3 "./documents.db" "SELECT COUNT(*) FROM documents;" 2>/dev/null || echo "N/A")
            local chunk_count=$(sqlite3 "./documents.db" "SELECT COUNT(*) FROM chunks;" 2>/dev/null || echo "N/A")
            local vectorized_count=$(sqlite3 "./documents.db" "SELECT COUNT(*) FROM chunks WHERE vectorized = true;" 2>/dev/null || echo "N/A")
            
            echo -e "  Documents: $doc_count"
            echo -e "  Chunks: $chunk_count"
            echo -e "  Vectorized: $vectorized_count"
        fi
    else
        echo -e "${RED}✗${NC} SQLite Database: Not found"
    fi
}

# Function to check processing queue status
check_processing_queue() {
    echo -e "\n${BLUE}Processing Queue Status:${NC}"
    
    if [ -f "./documents.db" ] && command -v sqlite3 >/dev/null 2>&1; then
        local pending_chunks=$(sqlite3 "./documents.db" "SELECT COUNT(*) FROM chunks WHERE vectorized = false;" 2>/dev/null || echo "N/A")
        
        if [ "$pending_chunks" = "0" ]; then
            echo -e "${GREEN}✓${NC} All chunks vectorized"
        elif [ "$pending_chunks" != "N/A" ] && [ "$pending_chunks" -gt 0 ]; then
            echo -e "${YELLOW}⚠${NC} $pending_chunks chunks pending vectorization"
        else
            echo -e "${YELLOW}⚠${NC} Cannot determine queue status"
        fi
    else
        echo -e "${YELLOW}⚠${NC} Cannot check processing queue"
    fi
}

# Main monitoring checks
main() {
    local all_healthy=true
    
    echo -e "${BLUE}Container Status:${NC}"
    check_service_status "Qdrant Vector DB" "cms-qdrant" || all_healthy=false
    check_service_status "Data Processor" "cms-data-processor" || all_healthy=false
    check_service_status "Vectorizer" "cms-vectorizer" || all_healthy=false
    
    echo -e "\n${BLUE}Service Health:${NC}"
    test_endpoint "Qdrant API" "http://localhost:6333/" 200
    test_endpoint "Data Processor Health" "http://localhost:8080/health" 200
    test_endpoint "Vectorizer Health" "http://localhost:8081/health" 200
    test_endpoint "RAG API" "http://localhost:8080/api/ask?q=test" 200
    test_endpoint "Qdrant Dashboard" "http://localhost:6333/dashboard" 200
    
    check_database
    check_processing_queue
    
    echo ""
    if [ "$all_healthy" = true ]; then
        echo -e "${GREEN}=== All Core Services Healthy ===${NC}"
    else
        echo -e "${RED}=== Some Services Need Attention ===${NC}"
    fi
    
    echo ""
    echo -e "${BLUE}Monitoring Commands:${NC}"
    echo "  Full logs:           docker-compose logs -f"
    echo "  Data processor:      docker-compose logs -f data-processor"
    echo "  Vectorizer:          docker-compose logs -f vectorizer"
    echo "  Application health:  curl http://localhost:8080/health"
    echo "  Qdrant dashboard:    http://localhost:6333/dashboard"
    echo "  Service status:      docker-compose ps"
}

# Handle script arguments
case "${1:-}" in
    "status"|"")
        main
        ;;
    "queue")
        check_processing_queue
        ;;
    "watch")
        echo -e "${BLUE}=== Watching Service Status (Ctrl+C to stop) ===${NC}"
        while true; do
            clear
            main
            sleep 30
        done
        ;;
    "help"|"-h"|"--help")
        echo "CMS Microservices Monitor"
        echo ""
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  status    Show complete status (default)"
        echo "  queue     Show processing queue status"
        echo "  watch     Continuously monitor status"
        echo "  help      Show this help message"
        ;;
    *)
        echo "Unknown command: $1"
        echo "Use '$0 help' for usage information"
        exit 1
        ;;
esac