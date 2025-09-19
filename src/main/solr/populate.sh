#!/bin/bash

# populate.sh - Load sample data into Solr (supports both standalone and SolrCloud)

SOLR_URL=${SOLR_URL:-"http://solr9-node1:8983/solr/discovery"}
SOLR_MODE=${SOLR_MODE:-"standalone"}
DELAY=${DELAY:-30}
DATA_FILE=${DATA_FILE:-"/opt/scripts/solr-sample.json.gz"}

echo "Populating Solr with sample data..."
echo "Mode: ${SOLR_MODE}"
echo "URL: ${SOLR_URL}"
echo "Waiting ${DELAY} seconds for Solr to be ready..."

sleep ${DELAY}

# For SolrCloud, we need to ensure collections are created first
if [ "${SOLR_MODE}" = "cloud" ]; then
    echo "Checking if collections exist..."

    # Wait for collections to be available
    MAX_ATTEMPTS=30
    ATTEMPT=0
    while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
        if curl -s "${SOLR_URL}/admin/ping" | grep -q '"status":"OK"'; then
            echo "Collection is ready!"
            break
        fi
        echo "Waiting for collection to be ready... (attempt $((ATTEMPT+1))/$MAX_ATTEMPTS)"
        sleep 5
        ATTEMPT=$((ATTEMPT+1))
    done

    if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
        echo "ERROR: Collection not ready after ${MAX_ATTEMPTS} attempts"
        exit 1
    fi
fi

# Load the sample data
echo "Loading sample data from ${DATA_FILE}..."
if [ -f "${DATA_FILE}" ]; then
    gunzip -c "${DATA_FILE}" | curl "${SOLR_URL}/update?commit=true" --data-binary @- -H "Content-type:application/json"

    if [ $? -eq 0 ]; then
        echo "Sample data loaded successfully!"

        # Show some stats
        echo "Checking document count..."
        DOC_COUNT=$(curl -s "${SOLR_URL}/select?q=*:*&rows=0" | grep -o '"numFound":[0-9]*' | cut -d: -f2)
        echo "Documents indexed: ${DOC_COUNT:-"unknown"}"
    else
        echo "ERROR: Failed to load sample data"
        exit 1
    fi
else
    echo "ERROR: Sample data file ${DATA_FILE} not found"
    exit 1
fi

