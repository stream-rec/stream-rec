FROM gradle:8.8-jdk21-alpine AS builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test

FROM amazoncorretto:21-al2023-headless
WORKDIR /app
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Install dependencies
RUN yum update -y && \
    yum install -y unzip tar python3 python3-pip which xz tzdata findutils && \
    pip3 install streamlink && \
    yum clean all && \
    rm -rf /var/cache/yum

# Install ffmpeg with architecture check
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "x86_64" ]; then \
      URL="https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"; \
    elif [ "$ARCH" = "aarch64" ]; then \
      URL="https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz"; \
    fi && \
    curl -L $URL | tar -xJ && \
    mv ffmpeg-*-linux*/bin/{ffmpeg,ffprobe,ffplay} /usr/local/bin/ && \
    chmod +x /usr/local/bin/{ffmpeg,ffprobe,ffplay} && \
    rm -rf ffmpeg-*

# Install rclone with architecture check
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "x86_64" ]; then \
      URL="https://downloads.rclone.org/rclone-current-linux-amd64.zip"; \
    elif [ "$ARCH" = "aarch64" ]; then \
      URL="https://downloads.rclone.org/rclone-current-linux-arm64.zip"; \
    fi && \
    curl -L $URL -o rclone.zip && \
    unzip rclone.zip && \
    mv rclone-*-linux*/rclone /usr/bin/ && \
    chown root:root /usr/bin/rclone && \
    chmod 755 /usr/bin/rclone && \
    rm -rf rclone.zip rclone-*

# Set timezone
ENV TZ=\${TZ:-Europe/Paris}

EXPOSE 12555

CMD ["java", "-jar", "app.jar"]