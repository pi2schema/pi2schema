# Docker Compatibility

## Using Docker Instead of Podman

If you prefer to use Docker instead of Podman, you can easily adapt the commands:

### Command Mapping

| Podman Command | Docker Equivalent |
|----------------|-------------------|
| `podman-compose -f examples/docker-compose.yaml up -d` | `docker compose -f examples/docker-compose.yaml up -d` |
| `podman-compose -f examples/docker-compose.yaml logs vault-init` | `docker compose -f examples/docker-compose.yaml logs vault-init` |
| `podman-compose -f examples/docker-compose.yaml down` | `docker compose -f examples/docker-compose.yaml down` |
| `podman-compose -f examples/docker-compose.yaml ps` | `docker compose -f examples/docker-compose.yaml ps` |

### Quick Start with Docker

```bash
# 1. Start all services
docker compose -f examples/docker-compose.yaml up -d


### Why We Recommend Podman

1. **Rootless by Default**: Better security model
2. **No Daemon**: Doesn't require a background daemon
3. **Drop-in Replacement**: Compatible with Docker commands
4. **Better Integration**: Works well with systemd and Kubernetes
5. **Open Source**: Fully open source without commercial licensing concerns

### Docker Requirements

If using Docker, ensure you have:
- Docker Engine 20.10+
- Docker Compose v2.0+

### Installation

#### Docker Desktop (macOS/Windows)
Download from [docker.com](https://www.docker.com/products/docker-desktop/)

#### Docker Engine (Linux)
```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group (optional, for rootless)
sudo usermod -aG docker $USER
newgrp docker
```

Both Docker and Podman will work identically with the pi2schema examples. The choice is yours!