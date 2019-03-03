#!/bin/sh

RB_PATH="${HOME}/.gem/ruby/2.4.0"

function main() {
    local port=$1
    set -- env "${RB_PATH}/bin/bundle" exec "${RB_PATH}/bin/jekyll" serve --port "${port}"
    echo "$@"
    "$@"
}

port="$1"
if [[ -z "$port" ]]; then
    port="8080"
fi

main $port || exit 1

