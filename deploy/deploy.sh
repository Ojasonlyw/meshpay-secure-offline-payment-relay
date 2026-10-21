#!/usr/bin/env bash
# Run on the Linux deployment host, from a release uploaded over verified SSH.
set -Eeuo pipefail
umask 077

root=${1:?Usage: deploy.sh /absolute/deployment/path [image@sha256:digest]}
release=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
[[ $root == /* && $root != / && -d $root && -f $root/.env ]] || {
    echo 'Create the deployment directory and its .env first.' >&2; exit 1;
}
root=$(cd -- "$root" && pwd -P)
[[ $root != / ]] || exit 1
[[ $release == "$root/releases/"* ]] || { echo 'Script must run inside a release directory.' >&2; exit 1; }

for command in docker flock; do command -v "$command" >/dev/null; done
exec 9>"$root/deploy.lock"
flock -w 600 9
previous=
if [[ -L $root/current ]]; then
    previous=$(readlink -f -- "$root/current")
    [[ $previous == "$root/releases/"* && -f $previous/image.env ]] || {
        echo 'Current release metadata is invalid.' >&2; exit 1;
    }
elif [[ -e $root/current ]]; then
    echo 'Current must be a release symlink.' >&2; exit 1
fi
image=${2:-}
if [[ -z $image && -f $release/image.env ]]; then
    IFS= read -r image < "$release/image.env"
    image=${image#MESHPAY_IMAGE=}
fi
[[ $image =~ ^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$ ]] || {
    echo 'Deployment requires a GHCR image reference pinned by SHA-256 digest.' >&2; exit 1;
}
# Do not overwrite the rollback target with a new image.
if [[ -f $release/image.env ]] && ! grep -Fxq "MESHPAY_IMAGE=$image" "$release/image.env"; then
    echo 'A release directory cannot be reused for a different image.' >&2; exit 1
fi
printf 'MESHPAY_IMAGE=%s\n' "$image" > "$release/image.env"

compose() {
    local directory=$1
    shift
    docker compose --project-name meshpay --env-file "$root/.env" \
        --env-file "$directory/image.env" -f "$directory/compose.production.yaml" "$@"
}
compose "$release" config --quiet
compose "$release" pull app
compose "$release" up -d --wait --wait-timeout 120 postgres redis

# Take a consistent logical backup before Flyway can migrate the database.
mkdir -p "$root/backups"
backup="$root/backups/$(date -u +%Y%m%dT%H%M%S)-$(basename -- "$release").dump"
# Expansion must happen inside the PostgreSQL container, not on the host.
# shellcheck disable=SC2016
compose "$release" exec -T postgres sh -c \
    'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup.tmp"
test -s "$backup.tmp"
mv -- "$backup.tmp" "$backup"

rollback() {
    local status=$?
    trap - ERR
    echo 'Release failed readiness; deployment is marked failed.' >&2
    if [[ -n $previous && $previous != "$release" && -f $previous/image.env ]]; then
        if compose "$previous" up -d --wait --wait-timeout 180 app; then
            echo 'Previous application image restored; database migrations were not reversed.' >&2
        else
            echo 'Rollback failed. Inspect the host and restore from backup if required.' >&2
        fi
    else
        echo 'No earlier release is available to restore.' >&2
    fi
    exit "$status"
}
trap rollback ERR
compose "$release" up -d --wait --wait-timeout 180 app
container=$(compose "$release" ps -q app)
test -n "$container"
actual_image=$(docker inspect --format '{{.Config.Image}}' "$container")
test "$actual_image" = "$image"
trap - ERR

# Promote metadata only after the exact image is healthy.
if [[ -n $previous && $previous != "$release" ]]; then
    ln -sfn -- "$previous" "$root/previous.next"
    mv -Tf -- "$root/previous.next" "$root/previous"
fi
ln -sfn -- "$release" "$root/current.next"
mv -Tf -- "$root/current.next" "$root/current"
printf 'Healthy release deployed: %s\n' "$image"
