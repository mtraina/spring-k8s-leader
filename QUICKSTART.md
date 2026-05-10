# Leader Election Prototype - Quick Start Guide

## What You Have

A complete Spring Boot 2.7 leader election system for Kubernetes with:
- **3 replicas** automatically running
- **Only one leader** executes scheduled tasks at a time
- **Automatic failover** when the leader dies
- **Local testing** with kind (Kubernetes in Docker)

## Quick Setup (3 steps)

### 1. Install Prerequisites (if needed)

```bash
# macOS with Homebrew
brew install kind kubectl docker

# Verify installations
kind version
kubectl version --client
```

### 2. Run the Setup Script

```bash
cd /Users/matteo/dev/cloud/kubernetes/spring-k8s-leader

# Make it executable (first time only)
chmod +x setup.sh

# Run setup
./setup.sh
```

This will:
- Build the Spring Boot application
- Create a Docker image
- Create a kind cluster with 4 nodes
- Deploy 3 replicas to Kubernetes
- Set up RBAC permissions

### 3. Watch the Leader Election

```bash
# Watch logs in real-time
kubectl logs -f deployment/leader-demo --all-containers=true

# In another terminal, watch pod status
./monitor.sh
```

You'll see output like:
```
💡 I AM THE LEADER: leader-demo (pod: leader-demo-abc123)
[2026-05-10 14:23:25] 🎯 LEADER TASK EXECUTED on pod: leader-demo-abc123
[2026-05-10 14:23:35] 🎯 LEADER TASK EXECUTED on pod: leader-demo-abc123
```

## Key Files

| File | Purpose |
|------|---------|
| [ScheduledLeaderTask.java](src/main/java/com/example/leaderdemo/ScheduledLeaderTask.java) | Runs tasks only on leader |
| [LeadershipListener.java](src/main/java/com/example/leaderdemo/LeadershipListener.java) | Listens to leadership changes |
| [LeaderController.java](src/main/java/com/example/leaderdemo/LeaderController.java) | REST endpoint for status check |
| [application.yaml](src/main/resources/application.yaml) | Spring configuration |
| [k8s/deployment.yaml](k8s/deployment.yaml) | Kubernetes deployment (3 replicas) |
| [k8s/rbac.yaml](k8s/rbac.yaml) | RBAC permissions for leader election |
| [kind-config.yaml](kind-config.yaml) | kind cluster configuration |

## How It Works

1. **Spring Cloud Kubernetes** manages leader election using Kubernetes Lease objects
2. **One pod** acquires the lease and becomes the leader
3. **ScheduledLeaderTask** checks who the leader is and only runs tasks on the leader pod
4. **REST API** at `/leader` shows current leadership status
5. **When leader dies**, another pod automatically takes over within seconds

## Testing Failover

Kill the leader pod to test automatic failover:

```bash
# Get the leader pod name from logs
# Then delete it
kubectl delete pod leader-demo-<pod-id>

# Watch logs - another pod should become leader within 5-10 seconds
kubectl logs -f deployment/leader-demo --all-containers=true
```

## REST API

```bash
# Check leader status
curl http://localhost:8080/leader

# Health check
curl http://localhost:8080/health
```

(Note: Port forwarding may be needed depending on your setup)

## Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment/leader-demo --replicas=5

# Scale back
kubectl scale deployment/leader-demo --replicas=3
```

## Cleanup

```bash
# Delete everything
kind delete cluster --name leader-demo
```

## Customizing Scheduled Tasks

Edit `ScheduledLeaderTask.java` to add your business logic in the `leaderOnlyTask()` or `leaderMinutelyTask()` methods.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│           Kubernetes (kind cluster)                 │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │ Pod 1: leader-demo-abc123 (LEADER)          │   │
│  │ ├─ Holds Lease object                       │   │
│  │ ├─ Executes: leaderOnlyTask() ✅            │   │
│  │ └─ Executes: leaderMinutelyTask() ✅        │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │ Pod 2: leader-demo-def456 (Follower)        │   │
│  │ ├─ No Lease                                 │   │
│  │ └─ Tasks skipped ❌                         │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │ Pod 3: leader-demo-ghi789 (Follower)        │   │
│  │ ├─ No Lease                                 │   │
│  │ └─ Tasks skipped ❌                         │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
└─────────────────────────────────────────────────────┘

If Pod 1 dies: Pod 2 or 3 acquires Lease → becomes new leader
```

## Stack

- Spring Boot: 2.7.18
- Java: 11
- Spring Cloud Kubernetes: 2.0.6
- Kubernetes Client: 18.0.0
- Container: Java 11 Eclipse Temurin

## Troubleshooting

### Pods not ready?
```bash
kubectl describe pod <pod-name>
kubectl logs <pod-name>
```

### Check Leases
```bash
kubectl get leases
kubectl describe lease leader-demo
```

### Docker image not found?
```bash
docker build -t leader-demo:latest .
kind load docker-image leader-demo:latest --name leader-demo
kubectl rollout restart deployment/leader-demo
```

## More Info

- See [README.md](README.md) for comprehensive documentation
- Spring Cloud Kubernetes: https://spring.io/projects/spring-cloud-kubernetes
- kind documentation: https://kind.sigs.k8s.io/
