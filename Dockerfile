FROM gradle:8.8-jdk21-alpine AS builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test --no-daemon

FROM amazoncorretto:21-al2023-headless
WORKDIR /app
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Install dependencies
RUN yum update -y && \
    yum install -y unzip tar python3 python3-pip which xz tzdata nscd findutils && \
    systemctl enable nscd && \
    pip3 install streamlink && \
    yum clean all && \
    rm -rf /var/cache/yum

# Install ffmpeg with architecture check
RUN if [ "$(uname -m)" = "x86_64" ]; then \
      curl -L https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz | tar -xJ && \
      mv ffmpeg-*-static/ffmpeg /usr/local/bin/ && \
      mv ffmpeg-*-static/ffprobe /usr/local/bin/; \
    elif [ "$(uname -m)" = "aarch64" ]; then \
      curl -L https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-arm64-static.tar.xz | tar -xJ && \
      mv ffmpeg-*-static/ffmpeg /usr/local/bin/ && \
      mv ffmpeg-*-static/ffprobe /usr/local/bin/; \
    fi && \
    rm -rf ffmpeg-*-static

# Install rclone with architecture check
RUN if [ "$(uname -m)" = "x86_64" ]; then \
      curl -L https://downloads.rclone.org/rclone-current-linux-amd64.zip -o rclone.zip && \
      unzip rclone.zip && \
      mv rclone-*-linux-amd64/rclone /usr/bin/ && \
      chown root:root /usr/bin/rclone && \
      chmod 755 /usr/bin/rclone \
    elif [ "$(uname -m)" = "aarch64" ]; then \
      curl -L https://downloads.rclone.org/rclone-current-linux-arm64.zip -o rclone.zip && \
      unzip rclone.zip && \
      mv rclone-*-linux-arm64/rclone /usr/bin/ && \
      chown root:root /usr/bin/rclone && \
      chmod 755 /usr/bin/rclone; \
    fi && \
    rm -rf rclone-*

# Set timezone
ENV TZ=${TZ:-Europe/Paris}

EXPOSE 12555

CMD ["sh", "-c", "nscd && java -jar app.jar"]