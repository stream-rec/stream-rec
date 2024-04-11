FROM gradle:8.6.0-jdk17-alpine as builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test --no-daemon

FROM amazoncorretto:17-alpine3.19
WORKDIR /app
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Install FFmpeg
RUN apk add --no-cache ffmpeg
# Install Rclone
RUN apk add --no-cache rclone
# Install Streamlink
RUN apk add --no-cache streamlink --repository=https://dl-cdn.alpinelinux.org/alpine/edge/community

# Install SQLite
RUN apk add --no-cache sqlite

# Set timezone
ENV TZ ${TZ:-Europe/Paris}
RUN apk add --no-cache tzdata

EXPOSE 12555

CMD ["java", "-jar", "app.jar"]
