#!/usr/bin/env bash
set -euo pipefail

readonly COMMIT="56381e407c0ccfb3a6f71e668a27a901001d22ce"
readonly SHA256="7fd19c03e7d7bb02c07e64c47e0539aeabe54f366a59be822f550b5f012a8751"
readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly DESTINATION="${ROOT}/third_party/llama.cpp"

if [[ -f "${DESTINATION}/CMakeLists.txt" ]]; then
  printf 'llama.cpp already present at %s\n' "${DESTINATION}"
  exit 0
fi

archive="$(mktemp)"
staging="$(mktemp -d)"
trap 'rm -f "${archive}"; rm -rf "${staging}"' EXIT
curl --fail --location --retry 3 "https://github.com/ggml-org/llama.cpp/archive/${COMMIT}.tar.gz" --output "${archive}"
actual="$(sha256sum "${archive}" | awk '{print $1}')"
if [[ "${actual}" != "${SHA256}" ]]; then
  echo "llama.cpp archive SHA-256 mismatch" >&2
  exit 1
fi
mkdir -p "${DESTINATION}"
tar -xzf "${archive}" --strip-components=1 -C "${staging}"
cp -a "${staging}/." "${DESTINATION}/"
printf 'Fetched llama.cpp commit %s\n' "${COMMIT}"
