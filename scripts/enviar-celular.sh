#!/usr/bin/env bash
# Compila o APK e envia para o celular via Taildrop (Tailscale).
# Uso: scripts/enviar-celular.sh [dispositivo]   
set -euo pipefail
cd "$(dirname "$0")/.."

# Aparelho: argumento ou TAILSCALE_DEVICE no local.properties (fora do Git).
DEFAULT_DEVICE="$(grep -E '^TAILSCALE_DEVICE=' local.properties 2>/dev/null | cut -d= -f2- || true)"
DEVICE="${1:-$DEFAULT_DEVICE}"
[ -n "$DEVICE" ] || { echo "Informe o aparelho: scripts/enviar-celular.sh <nome-no-tailscale>"; exit 1; }
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"

echo "> compilando..."
./gradlew assembleRelease --console=plain -q

# Nome com data/hora para não confundir versões na pasta de downloads do celular
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
NAME="Listonatha-$(date +%Y%m%d-%H%M).apk"
# release otimizado (R8), assinado com a chave de debug: instala por cima da versão anterior
cp app/build/outputs/apk/release/Listonatha-*-release.apk "$TMP/$NAME"

echo "> enviando $NAME para $DEVICE..."
tailscale file cp "$TMP/$NAME" "$DEVICE:"
echo "> pronto. No celular: notificação do Tailscale > abrir o arquivo > Instalar."
