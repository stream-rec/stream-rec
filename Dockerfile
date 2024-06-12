FROM gradle:8.7-jdk21-alpine as builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test --no-daemon

FROM amazoncorretto:21-alpine3.19
WORKDIR /app
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Add libc6-compat for Android room
RUN apk add --no-cache libc6-compat ffmpeg sqlite rclone tzdata

# Install Streamlink
#RUN apk add --no-cache streamlink --repository=https://dl-cdn.alpinelinux.org/alpine/edge/community

# Temporal solution until #https://github.com/hua0512/stream-rec/issues/54 is fixed
ENV PIPX_BIN_DIR=/usr/bin
RUN apk add --no-cache python3 py3-pip pipx && \
    pipx install streamlink

# Set timezone
ENV TZ ${TZ:-Europe/Paris}

EXPOSE 12555

CMD ["java", "-jar", "app.jar"]
