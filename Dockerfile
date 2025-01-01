# Build stage
FROM gradle:8.8-jdk21-alpine AS builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test --no-daemon --parallel

# Runtime stage
FROM amazoncorretto:21-al2023-headless
WORKDIR /app

# Add environment variables for PUID/PGID
ENV PUID=1000
ENV PGID=1000
ENV HOME=/home/abc

# Copy application jar
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Install dependencies with layer optimization and cleanup in same layer
RUN set -ex && \
    # Install base packages first
    yum update -y && \
    yum install -y \
        python3 \
        python3-pip \
        unzip \
        tar \
        xz \
        tzdata \
        findutils \
        shadow-utils && \
    # Create group and user
    groupadd -g ${PGID} abc && \
    useradd -u ${PUID} -g abc -d ${HOME} -s /bin/bash abc && \
    # Create directories
    mkdir -p ${HOME}/.local/share/streamlink/plugins && \
    # Install streamlink and plugin
    pip3 install --no-cache-dir streamlink && \
    curl -L -o "${HOME}/.local/share/streamlink/plugins/twitch.py" \
        'https://github.com/2bc4/streamlink-ttvlol/releases/latest/download/twitch.py' && \
    # Install ffmpeg based on architecture
    ARCH=$(uname -m) && \
    FFMPEG_URL=$(if [ "$ARCH" = "x86_64" ]; then \
        echo "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"; \
    else \
        echo "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz"; \
    fi) && \
    curl -L $FFMPEG_URL | tar -xJ --strip-components=2 '*/bin/ffmpeg' '*/bin/ffprobe' && \
    mv {ffmpeg,ffprobe} /usr/local/bin/ && \
    chmod +x /usr/local/bin/{ffmpeg,ffprobe} && \
    # Install rclone based on architecture
    RCLONE_URL=$(if [ "$ARCH" = "x86_64" ]; then \
        echo "https://downloads.rclone.org/rclone-current-linux-amd64.zip"; \
    else \
        echo "https://downloads.rclone.org/rclone-current-linux-arm64.zip"; \
    fi) && \
    curl -L $RCLONE_URL -o rclone.zip && \
    unzip -j rclone.zip '*/rclone' -d /usr/bin && \
    chmod 755 /usr/bin/rclone && \
    # Set permissions
    chown -R ${PUID}:${PGID} \
        /app \
        ${HOME} \
        /usr/local/bin/ffmpeg \
        /usr/local/bin/ffprobe \
        /usr/bin/rclone && \
    # Cleanup
    yum clean all && \
    rm -rf \
        /var/cache/yum \
        rclone.zip \
        /root/.cache \
        /tmp/*

# Set timezone with ARG for build-time configuration
ARG TZ=Europe/Paris
ENV TZ=${TZ}

# Switch to non-root user
USER abc

EXPOSE 12555

# Use exec form and set memory limits
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]