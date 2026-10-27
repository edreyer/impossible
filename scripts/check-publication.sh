#!/usr/bin/env bash
# Checks what publishToMavenLocal produced: every module publishes every target with a
# POM, a sources jar and a non-empty javadoc jar, which Maven Central requires. The JVM-only
# impossible-jackson module has one artifact and no target suffix.
set -euo pipefail

version="$1"
repo="${HOME}/.m2/repository/io/liquidsoftware"
targets=("" -jvm -js -wasm-js -linuxx64 -macosarm64 -iosarm64 -iossimulatorarm64)
status=0

check() {
  local module="$1" target="$2"
  dir="${repo}/${module}${target}/${version}"
  base="${dir}/${module}${target}-${version}"
  for file in "${base}.pom" "${base}-sources.jar" "${base}-javadoc.jar"; do
    if [[ ! -f "${file}" ]]; then
      echo "Missing ${file#"${repo}/"}"
      status=1
    fi
  done
  if [[ -f "${base}-javadoc.jar" ]] && ! unzip -l "${base}-javadoc.jar" | grep -q 'index.html'; then
    echo "Empty javadoc jar ${base#"${repo}/"}-javadoc.jar"
    status=1
  fi
  if [[ -f "${base}.pom" ]] && ! grep -q '<description>.\+</description>' "${base}.pom"; then
    echo "No description in ${base#"${repo}/"}.pom"
    status=1
  fi
}

for module in impossible impossible-testing impossible-serialization; do
  for target in "${targets[@]}"; do
    check "${module}" "${target}"
  done
done
check impossible-jackson ""

if [[ ${status} -eq 0 ]]; then
  echo "Every publication of each module is complete."
fi
exit ${status}
