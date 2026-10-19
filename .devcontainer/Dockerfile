FROM mcr.microsoft.com/devcontainers/java:21-bookworm

# Install native libraries required by JavaFX on Linux
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get install -y --no-install-recommends \
        libgtk-3-0 \
        libgl1 \
        libglx-mesa0 \
        libglu1-mesa \
        libxtst6 \
        libxi6 \
        libasound2 \
        libxrender1 \
        libxxf86vm1 \
        libfontconfig1 \
    && rm -rf /var/lib/apt/lists/*
