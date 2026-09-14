#!/usr/bin/env bash
# The Quilt client, headless, with GEGI and the MC-Runtime-Test mod.
#
#   ci/quilt-runtime.sh <minecraft version> <folder with the Fabric build of GEGI>
#
# headlesshq/mc-runtime-test installs Fabric, Forge and NeoForge but not Quilt, so
# this repeats its steps with the Quilt installer in the middle: HeadlessMC
# downloads vanilla, quilt-installer adds a quilt-loader version beside it, and
# HeadlessMC launches that version by regex. Quilt loads Fabric mods, so the mod
# under test is the Fabric jar, alongside MC-Runtime-Test's Fabric jar.
set -euo pipefail

MC="$1"
JARS="$2"
HMC_VERSION="2.10.0"
MCRT_VERSION="4.5.1"
MCDIR="$HOME/.minecraft"

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

curl -fsSL --retry 8 --retry-all-errors --retry-delay 20 -o headlessmc-launcher.jar \
  "https://github.com/3arthqu4ke/headlessmc/releases/download/${HMC_VERSION}/headlessmc-launcher-${HMC_VERSION}.jar"

if [ ! -f "$MCDIR/versions/$MC/$MC.json" ]; then
  java -jar headlessmc-launcher.jar --command download "$MC"
fi

QUILT_INSTALLER=$(curl -fsSL --retry 8 --retry-all-errors --retry-delay 20 https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/maven-metadata.xml \
  | sed -n 's:.*<release>\(.*\)</release>.*:\1:p')
curl -fsSL --retry 8 --retry-all-errors --retry-delay 20 -o quilt-installer.jar \
  "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/${QUILT_INSTALLER}/quilt-installer-${QUILT_INSTALLER}.jar"
# The installer needs Java 17 or newer, and versions before 1.18 play on an older one: it
# runs on the runner's own newer Java (GitHub's Ubuntu image has 21 and 17), the game
# on the version's.
INSTALLER_JAVA="$JAVA_HOME/bin/java"
for home in "${JAVA_HOME_21_X64:-}" "${JAVA_HOME_17_X64:-}"; do
  if [ -n "$home" ] && [ -x "$home/bin/java" ]; then
    INSTALLER_JAVA="$home/bin/java"
    break
  fi
done
"$INSTALLER_JAVA" -jar quilt-installer.jar install client "$MC" --install-dir="$MCDIR" --no-profile
ls "$MCDIR/versions"

# MC-Runtime-Test has no build for 1.13 to 1.15; there the mod's self-test makes its own
# world and quits the game (GEGI_SELFTEST_CREATE_WORLD).
if ! curl -fsSL --retry 8 --retry-all-errors --retry-delay 20 -o "run/mods/mc-runtime-test-${MC}-${MCRT_VERSION}-fabric-release.jar" \
  "https://github.com/headlesshq/mc-runtime-test/releases/download/${MCRT_VERSION}/mc-runtime-test-${MC}-${MCRT_VERSION}-fabric-release.jar"; then
  rm -f "run/mods/mc-runtime-test-${MC}-${MCRT_VERSION}-fabric-release.jar"
  export GEGI_SELFTEST_CREATE_WORLD=1
  echo "No MC-Runtime-Test for ${MC}: the self-test creates its own world"
fi
find "$JARS" -name 'gegi-fabric-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} run/mods/ \;
ls -l run/mods

cat >> run/options.txt <<EOF
onboardAccessibility:false
pauseOnLostFocus:false
EOF

sudo DEBIAN_FRONTEND=noninteractive apt-get install -y x11-xserver-utils >/dev/null
timeout 1200 xvfb-run java -Dhmc.check.xvfb=true -jar headlessmc-launcher.jar \
  --command launch '.*quilt.*' -regex --jvm "-Djava.awt.headless=true" || true

# A launch that joins a world and quits is not a pass by itself; see ci.yml.
if [ ! -f run/gegi-selftest-passed ]; then
  echo "::error::GEGI's in-game self-test did not pass on Quilt"
  exit 1
fi
echo "self-test passed on Quilt"
