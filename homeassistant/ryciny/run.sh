#!/usr/bin/with-contenv bash
# with-contenv: s6 hands the program the container's environment (SUPERVISOR_TOKEN, RYCINY_*).
exec /venv/bin/python -m ryciny
