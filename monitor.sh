#!/bin/bash

# Monitor leader election in real-time

echo "🔍 Monitoring Leader Election"
echo "=============================="
echo ""
echo "Watch mode: Updates every 2 seconds"
echo "Press Ctrl+C to exit"
echo ""

watch -n 2 'echo "📊 Pod Status:"; kubectl get pods -l app=leader-demo -o wide; echo ""; echo "🔐 Lease Status:"; kubectl get leases'
