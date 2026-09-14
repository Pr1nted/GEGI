#!/usr/bin/env bash
# A real client, headless, for Minecraft versions MC-Runtime-Test has no build for
# (1.13 to 1.15): the same steps headlesshq/mc-runtime-test takes, without its mod.
#
#   ci/client-selfdrive.sh <minecraft version> <loader: fabric|forge> <folder with the jars>
#
# HeadlessMC downloads the game and installs the loader, and launches it with only
# Open Arcade in mods/. Nothing joins a world for the mod here, so the job sets
# OPENARCADE_SELFTEST_CREATE_WORLD=1: the self-test creates a world from the title
# screen, runs its checks there, writes the marker and quits the game.
set -euo pipefail

MC="$1"
LOADER="$2"
JARS="$3"
HMC_VERSION="2.10.0"
MCDIR="$HOME/.minecraft"
JAVA_MAJOR=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/.*java.specification.version = \(1\.\)\{0,1\}\([0-9]*\).*/\2/p')

mkdir -p HeadlessMC run/mods "$MCDIR"
cat > HeadlessMC/config.properties <<EOF
hmc.java.versions=$JAVA_HOME/bin/java
hmc.gamedir=$PWD/run
hmc.mcdir=$MCDIR
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
hmc.assets.dummy=true
EOF

curl -fsSL --retry 5 --retry-all-errors --retry-delay 10 -o headlessmc-launcher.jar \
  "https://github.com/3arthqu4ke/headlessmc/releases/download/${HMC_VERSION}/headlessmc-launcher-${HMC_VERSION}.jar"

if [ ! -f "$MCDIR/versions/$MC/$MC.json" ]; then
  java -jar headlessmc-launcher.jar --command download "$MC"
  java -jar headlessmc-launcher.jar --command "$LOADER" "$MC" --java "$JAVA_MAJOR"
fi
ls "$MCDIR/versions"

find "$JARS" -name "openarcade-${LOADER}-*.jar" ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} run/mods/ \;
ls -l run/mods
if ! ls run/mods/openarcade-*.jar >/dev/null 2>&1; then
  echo "::error::no openarcade-${LOADER} jar in $JARS"
  exit 1
fi

cat >> run/options.txt <<EOF
onboardAccessibility:false
pauseOnLostFocus:false
EOF

sudo DEBIAN_FRONTEND=noninteractive apt-get install -y x11-xserver-utils >/dev/null
# The self-test quits the game itself; the timeout is only for a game that hangs.
timeout 1200 xvfb-run java -Dhmc.check.xvfb=true -jar headlessmc-launcher.jar \
  --command launch ".*${LOADER}.*" -regex --jvm "-Djava.awt.headless=true" || true

if [ ! -f run/openarcade-selftest-passed ]; then
  echo "::error::Open Arcade's in-game self-test did not pass on ${MC} ${LOADER}"
  exit 1
fi
echo "self-test passed on ${MC} ${LOADER}"
