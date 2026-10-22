FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

USER root

RUN apt-get update -yqq && \
    apt-get install -y --no-install-recommends \
        libc6-dev-i386 \
        lib32z1 \
        openjdk-17-jdk \
        wget \
        unzip \
        ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

ARG ANDROID_CMDLINE_TOOLS_VERSION=16111833

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip \
        -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted && \
    mv /tmp/cmdline-tools-extracted/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extracted && \
    yes | sdkmanager --sdk_root=${ANDROID_HOME} --licenses >/dev/null && \
    sdkmanager --sdk_root=${ANDROID_HOME} "platform-tools" "platforms;android-37" && \
    chown -R vscode:vscode ${ANDROID_HOME}

USER vscode