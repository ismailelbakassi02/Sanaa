#!/bin/sh
set -e

# Generates an RSA 2048 key pair used for JWT signing into ./secrets.
# Run from the repository root: sh scripts/gen-jwt-keys.sh

SECRETS_DIR="${SECRETS_DIR:-./secrets}"
mkdir -p "$SECRETS_DIR"

openssl genpkey -algorithm RSA -out "$SECRETS_DIR/jwt-private.pem" -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in "$SECRETS_DIR/jwt-private.pem" -out "$SECRETS_DIR/jwt-public.pem"

echo "JWT keys written to $SECRETS_DIR"
