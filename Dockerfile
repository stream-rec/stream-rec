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

# Install ffmpeg
RUN curl -L https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz | tar -xJ && \
    mv ffmpeg-*-static/ffmpeg /usr/local/bin/ && \
    mv ffmpeg-*-static/ffprobe /usr/local/bin/ && \
    rm -rf ffmpeg-*-static

# Install rclone
RUN curl -O https://downloads.rclone.org/rclone-current-linux-amd64.zip && \
    unzip rclone-current-linux-amd64.zip && \
    cd rclone-*-linux-amd64 && \
    cp rclone /usr/bin/ && \
    chown root:root /usr/bin/rclone && \
    chmod 755 /usr/bin/rclone && \
    rm -rf /rclone-current-linux-amd64.zip /rclone-*-linux-amd64

# Set timezone
ENV TZ=${TZ:-Europe/Paris}

EXPOSE 12555

CMD nscd && java -jar app.jar
