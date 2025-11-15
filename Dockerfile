FROM gradle:9.2.0-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test

FROM debian:stable-slim
WORKDIR /app
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Install dependencies
RUN apt-get update -y && \
    apt-get install -y --no-install-recommends python3 python3-pip tzdata curl default-jdk-headless rclone ffmpeg && \
    apt-get clean && \
    rm -rf /tmp/* /var/lib/apt/lists/* /var/tmp/* /var/log/* /usr/share/man


# Install streamlink
RUN pip3 install --break-system-packages streamlink
# Install streamlink-ttvlol
RUN INSTALL_DIR="/root/.local/share/streamlink/plugins"; mkdir -p "$INSTALL_DIR" && curl -L -o "$INSTALL_DIR/twitch.py" 'https://github.com/2bc4/streamlink-ttvlol/releases/latest/download/twitch.py'

# Install strev with architecture check
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "x86_64" ]; then \
      URL="https://github.com/hua0512/rust-srec/releases/download/v0.3.3/strev-linux-amd64"; \
    elif [ "$ARCH" = "aarch64" ]; then \
      URL="https://github.com/hua0512/rust-srec/releases/download/v0.3.3/strev-linux-arm64"; \
    fi && \
    curl -L $URL -o strev && \
    mv strev /usr/local/bin/ && \
    chmod +x /usr/local/bin/strev

# Set timezone
ENV TZ=\${TZ:-Europe/Paris}

EXPOSE 12555

CMD ["java", "-jar", "app.jar"]
