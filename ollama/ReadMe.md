# Ollama Docker Image für Azure & GHCR

Dieses Repository enthält alles, was du brauchst, um das Ollama-Image aus GitHub Container Registry (GHCR) zu klonen, lokal oder in deiner Azure-VM zu betreiben und mit ihm via HTTP API zu kommunizieren.

---

## 📖 Übersicht

Das hier bereitgestellte Docker-Image läuft einen Ollama-Server (Version 0.9.4) mit Llama2:7B auf CPU.  
Du findest es unter:


---

## 🚀 Voraussetzungen

- **Docker** ≥ 20.10  
- **(optional)** Docker Compose ≥ 1.29  
- **(optional)** Azure CLI (wenn du in Azure VM arbeitest)  
- GitHub-Zugang mit Schreib-Rechten auf das GHCR-Repository  
- Ein **Personal Access Token (classic)** mit den Scopes:
  - `repo` (Read & Write)
  - `write:packages` & `read:packages`

---

## 🔑 GHCR-Login

1. **Umgebungsvariablen setzen** (Linux/macOS – ersetze `YOUR_TOKEN` durch deinen PAT):

    ```bash
    export GHCR_USER=ignacantonio
    export GHCR_TOKEN=YOUR_TOKEN
    ```

2. **Bei GHCR einloggen**:

    ```bash
    echo $GHCR_TOKEN | docker login ghcr.io -u $GHCR_USER --password-stdin
    ```

---

## 📥 Image pullen

```bash
docker pull ghcr.io/cms-diplomarbeit/cms:ollama-latest
