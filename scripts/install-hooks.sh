#!/bin/sh
set -eu
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "Hooks installed — pre-commit (lint), commit-msg (Conventional Commits),"
echo "and pre-push (owner-lock + force-push guard + buildSmoke) are active."
