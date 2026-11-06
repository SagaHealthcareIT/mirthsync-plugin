#!/usr/bin/env bash

# ==========================================================================
# Signing Configuration (self-signed for local development builds)
#
# Official release signing is handled externally after the GitHub Actions
# build produces mirthsync-plugin.zip.
# ==========================================================================

SELFSIGNED_KEYSTORE="certificate/keystore.jks"
SELFSIGNED_STOREPASS="storepass"
SELFSIGNED_ALIAS="selfsigned"

# ==========================================================================
# sign_jar - Sign a JAR file using the self-signed certificate
# ==========================================================================
sign_jar() {
  local jar="$1"
  local signedjar="${2:-}"  # Optional: output to different file

  if [ -n "$signedjar" ]; then
    jarsigner \
      -keystore "$SELFSIGNED_KEYSTORE" \
      -storetype PKCS12 \
      -storepass "$SELFSIGNED_STOREPASS" \
      -signedjar "$signedjar" \
      "$jar" "$SELFSIGNED_ALIAS"
  else
    jarsigner \
      -keystore "$SELFSIGNED_KEYSTORE" \
      -storetype PKCS12 \
      -storepass "$SELFSIGNED_STOREPASS" \
      "$jar" "$SELFSIGNED_ALIAS"
  fi
}

echo "########################################"
echo
echo "   Building jars..."
echo
echo "########################################"
rm -f plugin.xml sampleplugin.zip
mvn install package
STATUS=$?
if [ $STATUS -ne 0 ]; then
  echo "---------- Building failed"
  exit 1
fi

PLUGIN_PATH=$(mvn exec:exec --non-recursive --quiet -Dexec.executable="echo" -Dexec.args='${mirth.plugin.path}')
ARTIFACT_ID=$(mvn exec:exec --non-recursive --quiet -Dexec.executable="echo" -Dexec.args='${project.artifactId}')

echo "########################################"
echo
echo "   Copying libraries..."
echo
echo "########################################"
rm -rf "$PLUGIN_PATH" # basically clean
mkdir -p "$PLUGIN_PATH/libs"

copy_jars() {
  local pattern=$1
  shopt -s nullglob
  local jars=($pattern)
  shopt -u nullglob
  if [ ${#jars[@]} -gt 0 ]; then
    cp "${jars[@]}" "$PLUGIN_PATH/libs/"
  fi
}

copy_jars "libs/runtime/client/*.jar"
copy_jars "libs/runtime/shared/*.jar"
copy_jars "server/target/runtime-libs-selected/*.jar"

echo "########################################"
echo
echo "   Signing shared libraries..."
echo
echo "########################################"

if [ -d "$PLUGIN_PATH/libs" ]; then
  for jar in "$PLUGIN_PATH"/libs/*.jar; do
    if [ -f "$jar" ]; then
      if zipinfo -1 "$jar" | grep -Eq '^META-INF/.*\.(SF|RSA|DSA)$'; then
        echo "skipping already-signed jar $jar"
        continue
      fi
      echo "signing $jar"
      if ! sign_jar "$jar"; then
        echo "---------- Failed to sign $jar"
        exit 1
      fi
    fi
  done
fi

echo "########################################"
echo
echo "   Generating plugin.xml..."
echo
echo "########################################"
mvn -N com.kaurpalang:mirth-plugin-maven-plugin:3.0.0:generate-plugin-xml

STATUS=$?
if [ $STATUS -ne 0 ]; then
  echo "---------- Plugin.xml generation failed"
  exit 1
fi

cp plugin.xml "$PLUGIN_PATH/"

if [ -d "$PLUGIN_PATH/libs" ]; then
  LIB_LIST=$(find "$PLUGIN_PATH/libs" -maxdepth 1 -type f -name "*.jar" -printf "%f\n" | sort)
  if [ -n "$LIB_LIST" ]; then
    PLUGIN_PATH="$PLUGIN_PATH" LIB_LIST="$LIB_LIST" python3 <<'PY'
import os
import sys
from xml.etree import ElementTree as ET

plugin_xml = os.path.join(os.environ['PLUGIN_PATH'], 'plugin.xml')
libs = [line.strip() for line in os.environ['LIB_LIST'].splitlines() if line.strip()]

tree = ET.parse(plugin_xml)
root = tree.getroot()

existing = {lib.get('path') for lib in root.findall('library') if lib.get('path')}

for jar in libs:
    jar_path = f"libs/{jar}"
    if jar_path in existing:
        continue
    element = ET.Element('library', {'path': jar_path, 'type': 'SHARED'})
    root.append(element)

tree.write(plugin_xml, encoding='UTF-8', xml_declaration=True)
PY
  fi
fi

echo "########################################"
echo
echo "   Signing jars..."
echo
echo "########################################"

mkdir "$PLUGIN_PATH/signing_input/"
cp {client,server,shared}/target/*.jar "$PLUGIN_PATH/signing_input/"

for module in server client shared; do
  current_jar="$PLUGIN_PATH/signing_input/$ARTIFACT_ID-$module.jar"
  signed_jar="$PLUGIN_PATH/$ARTIFACT_ID-$module.jar"
  echo "signing $current_jar"
  if ! sign_jar "$current_jar" "$signed_jar"; then
    echo "---------- Failed to sign $current_jar"
    exit 1
  fi
done

rm -rf "$PLUGIN_PATH/signing_input/"

echo "########################################"
echo
echo "   Packaging plugin..."
echo
echo "########################################"
# zip updates an existing archive in place and never drops entries, so a
# stale jar from an earlier local build would ride along. Start fresh.
rm -f "$PLUGIN_PATH.zip"
zip -r $PLUGIN_PATH "$PLUGIN_PATH"

rm -rf "$PLUGIN_PATH"
rm -f plugin.xml
