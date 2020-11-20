#!/usr/bin/env bash

MSG="$1"
if [[ -z "$1" ]]; then
	MSG="update" # default message
fi


git commit -m "$MSG"
