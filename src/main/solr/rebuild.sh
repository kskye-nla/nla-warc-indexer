docker-compose down
docker volume rm solr_zk_data solr_zk_datalog
docker-compose build --no-cache
docker-compose up -d
