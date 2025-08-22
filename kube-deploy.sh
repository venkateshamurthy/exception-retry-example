#!/bin/bash
# Ref: https://medium.com/geekculture/cheatsheet-for-kubernetes-minikube-kubectl-5500ffd2f0d5
# First do a brew install docker minikube colima qemu
#export DOCKER_PATH=/opt/homebrew/bin/docker
#export HOMEBREW_PATH=/opt/homebrew/bin
#export PATH=$DOCKER_PATH:$HOMEBREW_PATH:$PATH
# Next do a ./kube-deploy.sh (which calls anyways mvn clean package)
set -e
source $HOME/.profile
mvn clean package  -DskipTests

# Usage: ./deploy.sh <job-name> <image-name> <job-yaml> <dockerfile> [--keep]

JOB_NAME=${1:-exception-retry-example-job}
IMAGE_NAME=${2:-exception-retry-example:latest}
JOB_YAML=${3:-"src/main/resources/kube-job.yaml"}  # because all yaml files in resources folder
DOCKERFILE=${4:-"src/main/resources/Dockerfile"}   # because Dockerfile is also in resources
KEEP=${5:-false}

if [ "$KEEP" == "--keep" ]; then
  KEEP=true
else
  KEEP=false
fi

if [ "$(minikube status --format='{{.Host}}')" == "Running" ]; then
  echo "Minikube is already running."
else
  echo "Minikube is not running. Starting it now..."
  minikube start
  sleep 5
fi

echo "🔨 Building Docker image: $IMAGE_NAME"
eval "$(minikube docker-env)"
docker build -f $DOCKERFILE -t $IMAGE_NAME .

echo "🧹 Cleaning up old job (if any): $JOB_NAME"
kubectl delete job $JOB_NAME --ignore-not-found

echo "📦 Applying job spec: $JOB_YAML"
kubectl apply -f $JOB_YAML

echo "⏳ Waiting for pod to start..."
sleep 5
POD=$(kubectl get pods -l job-name=$JOB_NAME -o jsonpath='{.items[0].metadata.name}')

if [ -z "$POD" ]; then
  echo "❌ No pod found for job $JOB_NAME"
  exit 1
fi

echo "📜 Logs from job pod: $POD"
kubectl logs -f $POD || true

STATUS=$(kubectl get pod $POD -o jsonpath='{.status.phase}')
echo "✅ Job Pod $POD finished with status: $STATUS"
sleep 10 #ttl in kube-job should be referred to alter this

STATUS=$(kubectl get pod $POD -o jsonpath='{.status.phase}')
if [ "$STATUS" = "Completed" ] || [ "$STATUS" = "Succeeded" ]; then
  echo "⚠️ Job did complete/succeeded. Will close the pod"
else
  echo "Job did not complete so let us describe the pod..."
  kubectl describe pod $POD
fi
sleep 8 #ttl in kube-job should be referred to alter this  (10+8 seconds before ttl=20 seconds expire))

if [ "$KEEP" == false ]; then
  echo "🧹 Cleaning up job and pod: $JOB_NAME"
  kubectl delete job $JOB_NAME
else
  echo "ℹ️ Keeping job and pod for debugging (--keep enabled)"
fi