#!/usr/bin/env bash
# A Folia server starts with the Open Arcade plugin, answers /arcade, and stops.
#
#   ci/folia-smoke.sh <minecraft version> <folder with the plugin jar>
#
# Passes only when the plugin logs that it is enabled AND the console's /arcade
# lists Open Doctrines with its link. A server that never starts, a plugin that
# does not enable, or a command that answers nothing all fail.
set -euo pipefail

MC="$1"
JARS="$(cd "$2" && pwd)"
mkdir -p folia-server/plugins
cd folia-server

URL=$(curl -fsSL "https://fill.papermc.io/v3/projects/folia/versions/${MC}/builds" \
  | python3 -c 'import json,sys; b=json.load(sys.stdin); print(b[0]["downloads"]["server:default"]["url"])')
[ -f folia.jar ] || curl -fsSL -o folia.jar "$URL"
rm -f plugins/openarcade-folia-*.jar
find "$JARS" -name 'openarcade-folia-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} plugins/ \;
ls -l plugins
echo "eula=true" > eula.txt

rm -f console.fifo server.log
mkfifo console.fifo
# Hold the console open for the whole run, so the server does not see end-of-input.
exec 3<>console.fifo
java -Xmx2G -jar folia.jar --nogui < console.fifo > server.log 2>&1 &
SERVER=$!

wait_for() {
  local pattern="$1" seconds="$2"
  for _ in $(seq 1 "$seconds"); do
    grep -q "$pattern" server.log && return 0
    kill -0 "$SERVER" 2>/dev/null || return 1
    sleep 1
  done
  return 1
}

finish() {
  echo "stop" >&3 || true
  for _ in $(seq 1 60); do kill -0 "$SERVER" 2>/dev/null || break; sleep 1; done
  kill "$SERVER" 2>/dev/null || true
  exec 3>&-
}

if ! wait_for "Done (" 300; then
  tail -80 server.log; finish; echo "Folia did not start"; exit 1
fi
if ! grep -q "Open Arcade enabled" server.log; then
  tail -80 server.log; finish; echo "Open Arcade did not enable on Folia"; exit 1
fi

echo "arcade" >&3
if ! wait_for "Open Doctrines: https://pr1nted.itch.io/open-doctrines" 30; then
  tail -40 server.log; finish; echo "/arcade did not list Open Doctrines"; exit 1
fi
grep "Open Arcade\|https://" server.log | tail -6
finish
echo "Folia started, Open Arcade enabled, and /arcade answered"
