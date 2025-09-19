#!/bin/bash

# setup-solrcloud.sh - Initialize SolrCloud with configurations and collections

set -e

echo "Setting up SolrCloud..."

# Wait for ZooKeeper to be ready
echo "Waiting for ZooKeeper to be ready..."
while ! nc -z zookeeper 2181; do
  echo "Waiting for ZooKeeper..."
  sleep 2
done
echo "ZooKeeper is ready!"

# Wait for Solr nodes to be ready
echo "Waiting for Solr nodes to be ready..."
for port in 8983; do
  for host in solr9-node1 solr9-node2; do
    echo "Checking $host:$port..."
    while ! curl -s "http://$host:$port/solr/admin/info/system" > /dev/null; do
      echo "Waiting for $host:$port..."
      sleep 3
    done
    echo "$host:$port is ready!"
  done
done

# Upload NLA configuration to ZooKeeper
echo "Uploading NLA configuration to ZooKeeper..."
if ! solr zk ls /configs/nla_config -z zookeeper:2181 2>/dev/null; then
  solr zk upconfig -n nla_config -d /opt/solr-config/nla/conf -z zookeeper:2181
  echo "NLA configuration uploaded successfully!"
else
  echo "NLA configuration already exists, updating..."
  solr zk upconfig -n nla_config -d /opt/solr-config/nla/conf -z zookeeper:2181
fi

# Upload Discovery configuration to ZooKeeper
echo "Uploading Discovery configuration to ZooKeeper..."
if ! solr zk ls /configs/discovery_config -z zookeeper:2181 2>/dev/null; then
  solr zk upconfig -n discovery_config -d /opt/solr-config/discovery/conf -z zookeeper:2181
  echo "Discovery configuration uploaded successfully!"
else
  echo "Discovery configuration already exists, updating..."
  solr zk upconfig -n discovery_config -d /opt/solr-config/discovery/conf -z zookeeper:2181
fi

# Create NLA collection with sharding and replication
echo "Creating NLA collection..."
COLLECTION_EXISTS=$(curl -s "http://solr9-node1:8983/solr/admin/collections?action=LIST" | grep -o '"nla"' || echo "")
if [ -z "$COLLECTION_EXISTS" ]; then
  curl -s "http://solr9-node1:8983/solr/admin/collections?action=CREATE&name=nla&numShards=2&replicationFactor=2&configName=nla_config&maxShardsPerNode=2"
  echo "NLA collection created successfully!"
else
  echo "NLA collection already exists"
fi

# Create Discovery collection with sharding and replication
echo "Creating Discovery collection..."
COLLECTION_EXISTS=$(curl -s "http://solr9-node1:8983/solr/admin/collections?action=LIST" | grep -o '"discovery"' || echo "")
if [ -z "$COLLECTION_EXISTS" ]; then
  curl -s "http://solr9-node1:8983/solr/admin/collections?action=CREATE&name=discovery&numShards=2&replicationFactor=2&configName=discovery_config&maxShardsPerNode=2"
  echo "Discovery collection created successfully!"
else
  echo "Discovery collection already exists"
fi

# Wait a bit for collections to be fully initialized
echo "Waiting for collections to initialize..."
sleep 10

# Check collection status
echo "Checking collection status..."
curl -s "http://solr9-node1:8983/solr/admin/collections?action=CLUSTERSTATUS" | python3 -m json.tool || echo "Collections created"

echo "SolrCloud setup completed successfully!"
echo ""
echo "Access points:"
echo "- Solr Node 1: http://localhost:38983/solr"
echo "- Solr Node 2: http://localhost:39983/solr"
echo "- ZooKeeper: localhost:2181"
echo ""
echo "Collections created:"
echo "- nla (2 shards, 2 replicas)"
echo "- discovery (2 shards, 2 replicas)"
