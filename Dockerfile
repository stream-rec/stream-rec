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
RUN curl -O https://downloads.rclone.org/rclone-current-linux-$(uname -m).zip && \
    unzip rclone-current-linux-$(uname -m).zip && \
    cd rclone-*-linux-$(uname -m) && \
    cp rclone /usr/bin/ && \
    chown root:root /usr/bin/rclone && \
    chmod 755 /usr/bin/rclone && \
    rm -rf /rclone-current-linux-$(uname -m).zip /rclone-*-linux-$(uname -m)

# Set timezone
ENV TZ=${TZ:-Europe/Paris}

EXPOSE 12555

CMD ["sh", "-c", "nscd && java -jar app.jar"]