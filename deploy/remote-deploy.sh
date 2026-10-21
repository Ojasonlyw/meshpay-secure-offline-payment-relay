#!/usr/bin/env bash
# GitHub runner entry point. Secrets are environment variables, never shell source.
set -Eeuo pipefail
umask 077

for name in DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS DEPLOY_PORT DEPLOY_PATH IMAGE; do
    if [[ -z ${!name:-} ]]; then
        printf 'Missing deployment setting: %s\n' "$name" >&2
        exit 1
    fi
done
[[ $DEPLOY_HOST =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]] || exit 1
[[ $DEPLOY_USER =~ ^[a-zA-Z_][a-zA-Z0-9_-]*$ ]] || exit 1
[[ $DEPLOY_PORT =~ ^[0-9]{1,5}$ ]] || exit 1
[[ $DEPLOY_PATH =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ ]] || exit 1
[[ $IMAGE =~ ^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$ ]] || exit 1
[[ ${GITHUB_SHA:-} =~ ^[a-f0-9]{40}$ ]] || exit 1
[[ ${GITHUB_RUN_ID:-} =~ ^[0-9]+$ && ${GITHUB_RUN_ATTEMPT:-} =~ ^[0-9]+$ ]] || exit 1

temp_dir=$(mktemp -d)
trap 'rm -rf -- "$temp_dir"' EXIT
printf '%s\n' "$DEPLOY_SSH_KEY" > "$temp_dir/key"
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$temp_dir/known_hosts"
ssh_options=(-i "$temp_dir/key" -o "UserKnownHostsFile=$temp_dir/known_hosts"
    -o StrictHostKeyChecking=yes -o BatchMode=yes -o IdentitiesOnly=yes -o ConnectTimeout=15)
target="$DEPLOY_USER@$DEPLOY_HOST"
release="$DEPLOY_PATH/releases/${GITHUB_SHA}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"

ssh "${ssh_options[@]}" -p "$DEPLOY_PORT" "$target" \
    "test -f '$DEPLOY_PATH/.env' && mkdir -p '$release'"
scp "${ssh_options[@]}" -P "$DEPLOY_PORT" compose.production.yaml deploy/deploy.sh "$target:$release/"
ssh "${ssh_options[@]}" -p "$DEPLOY_PORT" "$target" \
    "bash '$release/deploy.sh' '$DEPLOY_PATH' '$IMAGE'"
