#!/usr/bin/env bash
# The Quilt client, headless, with Open Arcade and the MC-Runtime-Test mod.
#
#   ci/quilt-runtime.sh <minecraft version> <folder with the Fabric build of Open Arcade>
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

curl -fsSL -o headlessmc-launcher.jar \
  "https://github.com/3arthqu4ke/headlessmc/releases/download/${HMC_VERSION}/headlessmc-launcher-${HMC_VERSION}.jar"

if [ ! -f "$MCDIR/versions/$MC/$MC.json" ]; then
  java -jar headlessmc-launcher.jar --command download "$MC"
fi

QUILT_INSTALLER=$(curl -fsSL https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/maven-metadata.xml \
  | sed -n 's:.*<release>\(.*\)</release>.*:\1:p')
curl -fsSL -o quilt-installer.jar \
  "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/${QUILT_INSTALLER}/quilt-installer-${QUILT_INSTALLER}.jar"
java -jar quilt-installer.jar install client "$MC" --install-dir="$MCDIR" --no-profile
ls "$MCDIR/versions"

curl -fsSL -o "run/mods/mc-runtime-test-${MC}-${MCRT_VERSION}-fabric-release.jar" \
  "https://github.com/headlesshq/mc-runtime-test/releases/download/${MCRT_VERSION}/mc-runtime-test-${MC}-${MCRT_VERSION}-fabric-release.jar"
find "$JARS" -name 'openarcade-fabric-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} run/mods/ \;
ls -l run/mods

cat >> run/options.txt <<EOF
onboardAccessibility:false
pauseOnLostFocus:false
EOF

sudo DEBIAN_FRONTEND=noninteractive apt-get install -y x11-xserver-utils >/dev/null
xvfb-run java -Dhmc.check.xvfb=true -jar headlessmc-launcher.jar \
  --command launch '.*quilt.*' -regex --jvm "-Djava.awt.headless=true"

# A launch that joins a world and quits is not a pass by itself; see ci.yml.
if [ ! -f run/openarcade-selftest-passed ]; then
  echo "::error::Open Arcade's in-game self-test did not pass on Quilt"
  exit 1
fi
echo "self-test passed on Quilt"
