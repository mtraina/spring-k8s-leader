#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="leader-demo"

echo "🚀 Spring Cloud Kubernetes Leader Election Setup"
echo "=================================================="

# Step 1: Check prerequisites
echo ""
echo "📋 Checking prerequisites..."
command -v kind >/dev/null 2>&1 || { echo "❌ kind is not installed"; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "❌ kubectl is not installed"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "❌ docker is not installed"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "❌ maven is not installed"; exit 1; }
echo "✅ All prerequisites found"

# Step 2: Build the application
echo ""
echo "🔨 Building Spring Boot application..."
cd "$PROJECT_ROOT"
mvn clean package -q
echo "✅ Build complete"

# Step 3: Build Docker image
echo ""
echo "🐳 Building Docker image..."
docker build -t leader-demo:latest . > /dev/null 2>&1
echo "✅ Docker image built"

# Step 4: Check if cluster exists
echo ""
echo "🔍 Checking kind cluster..."
if kind get clusters | grep -q "$CLUSTER_NAME"; then
    echo "⚠️  Cluster '$CLUSTER_NAME' already exists"
    read -p "Do you want to delete and recreate it? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🗑️  Deleting existing cluster..."
        kind delete cluster --name "$CLUSTER_NAME"
    fi
else
    echo "✅ Creating new cluster..."
fi

# Step 5: Create cluster
if ! kind get clusters | grep -q "$CLUSTER_NAME"; then
    echo "⚙️  Creating kind cluster with 4 nodes..."
    kind create cluster --config "$PROJECT_ROOT/kind-config.yaml" > /dev/null 2>&1
    echo "✅ Cluster created"
fi

# Step 6: Load Docker image into kind
echo ""
echo "📦 Loading Docker image into kind..."
kind load docker-image leader-demo:latest --name "$CLUSTER_NAME"
echo "✅ Image loaded"

# Step 7: Deploy RBAC and application
echo ""
echo "📝 Deploying RBAC configuration..."
kubectl apply -f "$PROJECT_ROOT/k8s/rbac.yaml" > /dev/null
echo "✅ RBAC deployed"

echo ""
echo "🚀 Deploying application (3 replicas)..."
kubectl apply -f "$PROJECT_ROOT/k8s/deployment.yaml" > /dev/null
echo "✅ Deployment created"

# Step 8: Wait for pods to be ready
echo ""
echo "⏳ Waiting for pods to be ready..."
kubectl wait --for=condition=ready pod -l app=leader-demo --timeout=120s > /dev/null 2>&1
echo "✅ All pods are ready"

# Step 9: Display status
echo ""
echo "======================================================"
echo "✅ Setup Complete!"
echo "======================================================"
echo ""
kubectl get pods -l app=leader-demo -o wide
echo ""
echo "📊 Next steps:"
echo "  1. Watch the logs:"
echo "     kubectl logs -f deployment/leader-demo --all-containers=true"
echo ""
echo "  2. Test failover (kill the leader pod):"
echo "     kubectl delete pod <leader-pod-name>"
echo ""
echo "  3. Scale replicas:"
echo "     kubectl scale deployment/leader-demo --replicas=5"
echo ""
echo "  4. Cleanup:"
echo "     kind delete cluster --name $CLUSTER_NAME"
echo ""
