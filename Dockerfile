FROM gradle:9.2.0-jdk21-alpine AS builder
WORKDIR /app
COPY . .

# Build the application
RUN gradle stream-rec:build -x test --no-daemon

FROM debian:stable-slim
WORKDIR /app

# Set timezone (can be overridden at runtime)
ENV TZ=Europe/Paris
ENV LANG=C.UTF-8

RUN apt-get update -y && \
    apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        python3-venv \
        tzdata \
        curl \
        ca-certificates \
        default-jdk-headless \
        rclone \
        ffmpeg && \
    # Create virtual environment for Python packages
    python3 -m venv /opt/venv && \
    /opt/venv/bin/pip install --no-cache-dir streamlink && \
    # Install streamlink twitch plugin
    mkdir -p /root/.local/share/streamlink/plugins && \
    curl -L -o /root/.local/share/streamlink/plugins/twitch.py \
        'https://github.com/2bc4/streamlink-ttvlol/releases/latest/download/twitch.py' && \
    # Install strev with architecture check
    ARCH=$(uname -m) && \
    if [ "$ARCH" = "x86_64" ]; then \
        URL="https://github.com/hua0512/rust-srec/releases/latest/download/strev-linux-amd64"; \
    elif [ "$ARCH" = "aarch64" ]; then \
        URL="https://github.com/hua0512/rust-srec/releases/latest/download/strev-linux-arm64"; \
    else \
        echo "Unsupported architecture: $ARCH" && exit 1; \
    fi && \
    curl -L $URL -o /usr/local/bin/strev && \
    chmod +x /usr/local/bin/strev && \
    # Clean up to reduce image size
    apt-get clean && \
    rm -rf /tmp/* /var/lib/apt/lists/* /var/tmp/* /var/log/* /usr/share/man /usr/share/doc

# Activate virtual environment
ENV PATH="/opt/venv/bin:$PATH"

# Copy jar from builder
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

EXPOSE 12555

CMD ["java", "-jar", "app.jar"]
