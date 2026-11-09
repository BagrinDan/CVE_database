#!/bin/bash
set -e

# Пользователь, под которым replica копирует данные с primary
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'replicator') THEN
        CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '${REPL_PASSWORD:-replpass}';
    END IF;
END
\$\$;
EOSQL

# Разрешить подключения для репликации (в pg_hba.conf образа их по умолчанию нет)
echo "host replication replicator all scram-sha-256" >> "$PGDATA/pg_hba.conf"
