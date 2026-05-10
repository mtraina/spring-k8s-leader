# Spring Boot Kubernetes Leader Election Demo

A simple yet effective prototype demonstrating leader election in a Spring Boot application running on Kubernetes. Only one pod out of three replicas will execute scheduled tasks at any given time.

## Architecture

- **Framework**: Spring Boot 2.7 with Spring Cloud Kubernetes
- **Leader Election**: Uses Kubernetes Lease objects for distributed locking
- **Java Version**: 11
- **Scheduled Tasks**: Cron-like tasks that execute only on the leader

## Key Components

### 1. **ScheduledLeaderTask** (`ScheduledLeaderTask.java`)
- Runs scheduled tasks only on the leader node
- Two example tasks:
  - `leaderOnlyTask()`: Executes every 10 seconds
  - `leaderMinutelyTask()`: Executes every 60 seconds
- Uses `LeadershipInitializer.getLeader()` to check leadership status

### 2. **LeadershipListener** (`LeadershipListener.java`)
- Listens to leader election events
- Prints events when leadership is granted or revoked
- Useful for logging and monitoring

### 3. **Spring Cloud Kubernetes Configuration**
- Leader election enabled via `application.yaml`
- RBAC rules allow pod to create and manage Lease objects
- Automatic fallback if leader dies

## Prerequisites

1. **Docker** - For building and running container images
2. **kind** - Kubernetes in Docker for local testing
3. **kubectl** - Kubernetes CLI
4. **Maven** - For building the application
5. **Java 11+** - Runtime environment

Install them on macOS:
```bash
# Install kind (if using Homebrew)
brew install kind

# Or download directly
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-darwin-arm64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# Install kubectl
brew install kubectl

# Verify installations
kind version
kubectl version --client
```

## Setup & Local Testing

### Step 1: Create a kind Cluster

```bash
cat > kind-config.yaml << EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: leader-demo
nodes:
  - role: control-plane
  - role: worker
  - role: worker
  - role: worker
EOF

kind create cluster --config kind-config.yaml
```

Verify the cluster:
```bash
kubectl cluster-info
kubectl get nodes
```

### Step 2: Build the Application

```bash
# Navigate to project root
cd /path/to/spring-k8s-leader

# Build with Maven
mvn clean package

# Build Docker image and load into kind
docker build -t leader-demo:latest .
kind load docker-image leader-demo:latest --name leader-demo
```

Verify the image is loaded:
```bash
kubectl get images
# or check via Docker
docker image ls | grep leader-demo
```

### Step 3: Deploy to Kubernetes

```bash
# Apply RBAC configuration
kubectl apply -f k8s/rbac.yaml

# Apply deployment
kubectl apply -f k8s/deployment.yaml

# Verify deployment
kubectl get deployments
kubectl get pods -o wide
kubectl get serviceaccounts
```

### Step 4: Monitor the Leader Election

Watch the logs to see which pod becomes the leader and executes tasks:

```bash
# Watch all pods (real-time)
kubectl logs -f deployment/leader-demo --all-containers=true --tail=50

# Or watch individual pods
kubectl logs -f pod/leader-demo-XXXXXXX
```

You should see output like:
```
[2026-05-10 14:23:15] 💡 I AM THE LEADER: leader-demo
[2026-05-10 14:23:25] 🎯 LEADER TASK EXECUTED on pod: leader-demo-abc123
[2026-05-10 14:23:35] 🎯 LEADER TASK EXECUTED on pod: leader-demo-abc123
```

### Step 5: Test Failover (Leader Pod Crash)

Delete the leader pod to trigger failover:

```bash
# Find the leader pod from logs
LEADER_POD=$(kubectl get pods -l app=leader-demo -o jsonpath='{.items[0].metadata.name}')

# Delete it
kubectl delete pod $LEADER_POD

# Watch logs - another pod should become leader within a few seconds
kubectl logs -f deployment/leader-demo --all-containers=true
```

You'll see:
```
❌ Leadership revoked: leader-demo
💡 I AM THE LEADER: leader-demo    <-- New leader takes over
```

## Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment/leader-demo --replicas=5

# Scale back to 3
kubectl scale deployment/leader-demo --replicas=3

# Only one will execute the scheduled tasks
```

## Cleanup

```bash
# Delete deployment
kubectl delete -f k8s/deployment.yaml

# Delete RBAC
kubectl delete -f k8s/rbac.yaml

# Delete kind cluster
kind delete cluster --name leader-demo
```

## How It Works

1. **Lease-Based Election**: Spring Cloud Kubernetes uses Kubernetes Lease objects (in `coordination.k8s.io/v1` API group) for leader election
2. **Automatic Renewal**: The leader pod continuously renews its lease
3. **Timeout & Failover**: If a leader dies, its lease expires (default 15s), and another pod takes over
4. **Spring Integration**: The `LeadershipInitializer` provides easy access to leadership status

### Leader Election Flow

```
Pod A, B, C start
   ↓
All try to acquire Lease
   ↓
One succeeds (becomes leader)
   ↓
Leader continuously renews lease
   ↓
Leader schedules tasks run
   ↓
If leader fails:
  - Lease expires
  - Another pod acquires it
  - New leader takes over
```

## Customizing Scheduled Tasks

Edit `ScheduledLeaderTask.java` to add your business logic:

```java
@Scheduled(fixedDelay = 30000)  // Every 30 seconds
public void myCustomTask() {
    if (leadershipInitializer.getLeader("leader-demo")) {
        // Your code here - runs only on leader
        System.out.println("Executing critical operation...");
    }
}
```

## Troubleshooting

### Pods not becoming ready
```bash
# Check pod events
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name>
```

### Leader election not working
```bash
# Verify RBAC permissions
kubectl get role leader-role -o yaml
kubectl get rolebinding leader-rolebinding -o yaml

# Check Lease objects
kubectl get leases
kubectl describe lease leader-demo
```

### Image not found
```bash
# Reload image
docker build -t leader-demo:latest .
kind load docker-image leader-demo:latest --name leader-demo

# Restart pods to pull new image
kubectl rollout restart deployment/leader-demo
```

## Production Considerations

1. Use image registries (Docker Hub, ECR, etc.) instead of local images
2. Configure resource limits and requests
3. Use ConfigMaps for configuration instead of hardcoding
4. Implement proper logging and monitoring
5. Consider using Helm charts for easier deployment management
6. Set up proper ingress for external access
7. Use namespaces for better organization

## References

- [Spring Cloud Kubernetes Documentation](https://spring.io/projects/spring-cloud-kubernetes)
- [Kubernetes Leader Election](https://kubernetes.io/docs/concepts/architecture/leader-election/)
- [kind Documentation](https://kind.sigs.k8s.io/)
