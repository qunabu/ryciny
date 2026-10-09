ARG BUILD_FROM=ghcr.io/home-assistant/aarch64-base-debian:bookworm
FROM ${BUILD_FROM}

# Python 3.11 from Debian (ai-edge-litert ships manylinux wheels), parec to read the microphone from HA's PulseAudio.
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv pulseaudio-utils ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY app/requirements.txt /app/requirements.txt
RUN python3 -m venv /venv && /venv/bin/pip install --no-cache-dir -r /app/requirements.txt
COPY app /app
COPY run.sh /run.sh

WORKDIR /app
ENV PYTHONUNBUFFERED=1
CMD ["/run.sh"]
