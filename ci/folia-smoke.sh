#!/usr/bin/env bash
# A Folia server starts with the Open Arcade plugin, answers /arcade, and stops.
#
#   ci/folia-smoke.sh <minecraft version> <folder with the plugin jar>
#
# Folia exists from Minecraft 1.19.4. For older versions the same plugin is started
# on Paper, which Folia is built from and which has builds back to 1.12.2.
#
# Passes only when the plugin logs that it is enabled AND the console's /arcade
# lists Open Doctrines with its link. A server that never starts, a plugin that
# does not enable, or a command that answers nothing all fail.
set -euo pipefail

MC="$1"
JARS="$(cd "$2" && pwd)"
mkdir -p folia-server/plugins
cd folia-server

SERVER_KIND=folia
if ! curl -fsSL --retry 5 --retry-all-errors --retry-delay 10 "https://fill.papermc.io/v3/projects/folia/versions/${MC}/builds" -o builds.json 2>/dev/null \
   || ! python3 -c 'import json,sys; b=json.load(open("builds.json")); sys.exit(0 if isinstance(b, list) and b else 1)'; then
  SERVER_KIND=paper
  curl -fsSL --retry 5 --retry-all-errors --retry-delay 10 "https://fill.papermc.io/v3/projects/paper/versions/${MC}/builds" -o builds.json
fi
URL=$(python3 -c 'import json; b=json.load(open("builds.json")); print(b[0]["downloads"]["server:default"]["url"])')
echo "Starting ${SERVER_KIND} ${MC}"
[ -f "${SERVER_KIND}-${MC}.jar" ] || curl -fsSL --retry 5 --retry-all-errors --retry-delay 10 -o "${SERVER_KIND}-${MC}.jar" "$URL"
rm -f plugins/openarcade-folia-*.jar
find "$JARS" -name 'openarcade-folia-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} plugins/ \;
ls -l plugins
echo "eula=true" > eula.txt

rm -f console.fifo server.log
mkfifo console.fifo
# Hold the console open for the whole run, so the server does not see end-of-input.
exec 3<>console.fifo
java -Xmx2G -jar "${SERVER_KIND}-${MC}.jar" --nogui < console.fifo > server.log 2>&1 &
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
  tail -80 server.log; finish; echo "${SERVER_KIND} did not start"; exit 1
fi
if ! grep -q "Open Arcade enabled" server.log; then
  tail -80 server.log; finish; echo "Open Arcade did not enable on ${SERVER_KIND}"; exit 1
fi

echo "arcade" >&3
if ! wait_for "Open Doctrines: https://pr1nted.itch.io/open-doctrines" 30; then
  tail -40 server.log; finish; echo "/arcade did not list Open Doctrines"; exit 1
fi
grep "Open Arcade\|https://" server.log | tail -6
finish
echo "${SERVER_KIND} ${MC} started, Open Arcade enabled, and /arcade answered"
