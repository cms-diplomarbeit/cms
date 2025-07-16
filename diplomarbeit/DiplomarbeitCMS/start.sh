#!/bin/bash

# Build and run the CMS application with Docker (Microservices Architecture)

set -e

echo "Starting CMS Application Setup (Microservices)..."

# Create necessary directories
mkdir -p watched

echo "Building Docker images..."
docker-compose build

echo "Starting all services..."
docker-compose up -d

echo "Waiting for services to be ready..."
sleep 20

echo "CMS Application is ready!"
echo ""
echo "Service Status:"
echo "- Qdrant: http://localhost:6333"
echo "- Data Processor: Running (file monitoring)"
echo "- Vectorizer: Running (embedding generation)"
echo "- Ollama: http://file1.lan.elite-zettl.at:11434 (external)"
echo "- Tika: http://dev1.lan.elite-zettl.at:9998 (external)"
echo ""
echo "To add documents for processing, copy them to the 'watched' directory"
echo "To view logs:"
echo "  - Data Processor: docker-compose logs -f data-processor"
echo "  - Vectorizer: docker-compose logs -f vectorizer"
echo "  - All services: docker-compose logs -f"
echo "To stop: docker-compose down"
