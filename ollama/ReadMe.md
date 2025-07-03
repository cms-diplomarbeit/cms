# Ollama Docker-Compose Deployment

Dieses Repository enthält ein Docker-Compose-Setup, mit dem du deinen Ollama (Llama2:7B) Service aus deiner Azure Container Registry (ACR) schnell und reproduzierbar aufsetzen kannst.

---

## Inhaltsübersicht

- [Voraussetzungen](#voraussetzungen)  
- [Repository klonen](#repository-klonen)  
- [ACR-Login und Credentials](#acr-login-und-credentials)  
- [Docker-Compose konfigurieren](#docker-compose-konfigurieren)  
- [Service starten](#service-starten)  
- [Modell nachladen](#modell-nachladen)  
- [Funktionstest](#funktionstest)  
- [Optional: Zugriff von extern](#optional-zugriff-von-extern)  
- [Troubleshooting](#troubleshooting)  
- [weiterführende Hinweise](#weiterführende-hinweise)  

---

## Voraussetzungen

- **Docker** und **Docker-Compose** auf dem Host installiert  
- **Azure CLI** (nur zur einmaligen Einrichtung der ACR Credentials)  
- Zugriff auf deine ACR mit Administrator-Rechten  
- SSH-Zugang zur VM (wenn du auf einer entfernten Maschine deployst)

---

## Repository klonen

Auf dem gewünschten Host (lokal oder VM) führst du aus:

```bash
git clone https://github.com/<dein-user>/ollama-compose.git
cd ollama-compose
