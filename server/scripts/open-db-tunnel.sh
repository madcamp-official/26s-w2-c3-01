#!/usr/bin/env bash
set -euo pipefail

vm_host="${MELODY_VM_HOST:-172.10.7.222}"
local_port="${MELODY_DB_LOCAL_PORT:-15432}"

echo "Opening localhost:${local_port} -> ${vm_host}:127.0.0.1:5432"
echo "Keep this terminal open while DBeaver is connected."
exec ssh -N -L "${local_port}:127.0.0.1:5432" "root@${vm_host}"
