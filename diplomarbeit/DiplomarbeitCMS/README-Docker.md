# CMS Application - Docker Deployment Guide

This guide explains how to deploy the CMS (Content Management System) application using Docker.

## Processing Flow

1. **File Detection**: Data Processor monitors the `watched/` directory
2. **Text Extraction**: Data Processor uses external Tika to extract text content
3. **Database Storage**: Document metadata and text chunks stored in SQLite
4. **Vectorization Queue**: Chunks marked as `vectorized=false` await processing
5. **Embedding Generation**: Vectorizer processes chunks using external Ollama
6. **Vector Storage**: Embeddings stored in Qdrant with metadata
7. **Completion**: Chunks marked as `vectorized=true`

## Key Benefits

- **Separated Concerns**: Data processing and vectorization are independent
- **External Services**: Uses your existing Ollama and Tika infrastructure
- **Scalability**: Scale each service based on workload
- **Resource Efficiency**: Lower memory footprint than monolithic approach
- **Fault Tolerance**: Services can fail and restart independently
- **Development**: Easier debugging and maintenanceation using Docker with separate microservices.

## 🏗️ Architecture

The application consists of separate Docker services:

- **Data Processor**: File monitoring, text extraction, and database operations
- **Vectorizer**: Embedding generation and vector storage  
- **Qdrant**: Vector database for storing embeddings
- **External Services**: Ollama (LLM) and Tika (document processing) run on your existing servers

### Service Separation Benefits

- **Scalability**: Scale data processing and vectorization independently
- **Resource Management**: Different memory/CPU requirements per service
- **Fault Tolerance**: Services can restart independently
- **Development**: Easier to develop and debug individual components

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- At least 2GB of available RAM
- Access to external Ollama and Tika servers

### Option 1: Using the start script (Recommended)

```bash
# Make the script executable (if not already)
chmod +x start.sh

# Run the start script
./start.sh
```

### Option 2: Manual setup

```bash
# Build and start all services
docker-compose up --build -d

# Download required models for Ollama
docker-compose exec ollama ollama pull mxbai-embed-large
docker-compose exec ollama ollama pull llama2:7b

# Check status
docker-compose ps
```

## Directory Structure

```
.
├── Dockerfile.data-processor    # Data processing service
├── Dockerfile.vectorizer        # Vectorization service
├── docker-compose.yml          # Multi-service orchestration
├── start.sh                    # Quick start script
├── watched/                    # Directory for documents to process
├── documents.db                # SQLite database (mounted)
└── src/                        # Application source code
```


### Environment Variables

The application supports the following environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `QDRANT_URL` | `http://qdrant:6333` | Qdrant service URL |
| `OLLAMA_URL` | `http://file1.lan.elite-zettl.at:11434` | External Ollama service URL |
| `TIKA_URL` | `http://dev1.lan.elite-zettl.at:9998` | External Tika service URL |
| `WATCH_DIR` | `/app/watched` | Directory to monitor for new files |
| `SERVICE_MODE` | `FULL` | Service mode: `DATA_PROCESSOR`, `VECTORIZER`, or `FULL` |
| `JAVA_OPTS` | `-Xmx1g -Xms256m` | JVM options |

### Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Qdrant | 6333 | REST API |
| Qdrant | 6334 | gRPC API |
| Data Processor | - | Internal service (file monitoring) |
| Vectorizer | - | Internal service (embedding generation) |
| Ollama | 11434 | External LLM service |
| Tika | 9998 | External document processing |

## Usage

### Processing Documents

1. Copy documents to the `watched` directory:
   ```bash
   cp your-document.pdf watched/
   ```

2. The application will automatically:
   - **Data Processor**: Extract text using Tika and store in database
   - **Vectorizer**: Create embeddings using Ollama and store vectors in Qdrant
   - Update the SQLite database with metadata

### Monitoring

```bash
# View data processor logs
docker-compose logs -f data-processor

# View vectorizer logs
docker-compose logs -f vectorizer

# View all service logs
docker-compose logs -f

# Check service status
docker-compose ps

# Check service health
docker-compose exec qdrant curl http://localhost:6333/health
docker-compose exec ollama curl http://localhost:11434/api/version
curl http://dev1.lan.elite-zettl.at:9998/version  # External Tika check
```

### Accessing Services

- **Qdrant Web UI**: http://localhost:6333/dashboard
- **Qdrant API**: http://localhost:6333
- **Ollama API**: http://file1.lan.elite-zettl.at:11434 (external)
- **Tika API**: http://dev1.lan.elite-zettl.at:9998 (external)

## Development

### Building Changes

After making code changes:

```bash
# Rebuild and restart a specific service
docker-compose up --build data-processor

# Or rebuild everything
docker-compose up --build
```

### Debugging

```bash
# Access data processor container
docker-compose exec data-processor bash

# Access vectorizer container  
docker-compose exec vectorizer bash

# Check file system
docker-compose exec data-processor ls -la /app/watched
```

## Data Persistence

The following data is persisted across container restarts:

- **Qdrant data**: Vector database storage
- **Ollama models**: Downloaded language models  
- **SQLite database**: Application metadata
- **Application data**: Logs and temporary files

## Stopping and Cleanup

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes all data)
docker-compose down -v

# Remove images
docker-compose down --rmi all
```