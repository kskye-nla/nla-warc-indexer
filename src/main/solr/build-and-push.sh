#!/bin/bash

# Build and push script for Rancher deployment

# Set your container registry details
REGISTRY="your-registry.com"  # Replace with your registry
PROJECT="warc-indexer"
IMAGE_NAME="warc-solr"
TAG="latest"

# Full image name
FULL_IMAGE_NAME="${REGISTRY}/${PROJECT}/${IMAGE_NAME}:${TAG}"

echo "Building Docker image..."
docker build -f Dockerfile9 -t "${FULL_IMAGE_NAME}" .

echo "Pushing image to registry..."
docker push "${FULL_IMAGE_NAME}"

echo "Image pushed: ${FULL_IMAGE_NAME}"
echo ""
echo "Update the image references in the Kubernetes manifests:"
echo "- In k8s/solr-nodes.yaml, replace 'your-registry/warc-solr:latest' with '${FULL_IMAGE_NAME}'"
echo "- In k8s/jobs.yaml, replace 'your-registry/warc-solr:latest' with '${FULL_IMAGE_NAME}'"
