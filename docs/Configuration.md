# 1. Logging in

After starting the tool, you will be redirected to the login page. Enter your credentials and click on the `Sign in` button.

The default credentials are set to `stream-rec` for both the username and password. But if you set the `LOGIN_SECRET` environment variable, the
password will be set to that value.

![login.png](https://github.com/hua0512/stream-rec-front/blob/master/docs/en/login.png)

# 2. General settings

Navigate to the settings page by clicking on the settings icon in the left navigation bar.

After that, you will see the general settings page. Configure the settings as needed and click on the `Update setttings` button to save the changes.

> [!NOTE]\
> Things to note:
> - `Output folder`: The directory where the recorded streams will be saved. If you are running the tool in a Docker container, make sure the path is
    a container path bind to host. Otherwise, you may not be able to locate the recorded streams. For example, assuming you deployed
    with `/path/to/host/folder:/path/to/container/folder`. Then, you should put `/path/to/container/folder` as the output folder.
> - If you want to disable parted downloads, set the `Maximum parted download duration` to a high value, e.g., `1` day.
> - Read the comments of each setting for more information. You can also open a discussion if you have any questions.

![global_settings.png](https://github.com/hua0512/stream-rec-front/blob/master/docs/en/settings.png)

# 3. Platform settings

Navigate to the platform settings page by clicking on `Platform settings` in the left navigation bar of the settings page.

Here you can configure each platform's settings, please read the comments of each setting for more information.

Leave it as default if you are not sure what to do.

![platform_settings.png](https://github.com/hua0512/stream-rec-front/blob/master/docs/en/platform_settings.png)

> [!NOTE]\
> Things to note:
> - The update button is only attached to the platform you are currently editing. Make sure to click on the `Update settings` button after you finish
    editing each platform's settings.

# 4. Adding a new streamer to record

Navigate to the streamers list page by clicking on `Streamers` (the second icon) in the left navigation bar.

![streamers.png](https://github.com/hua0512/stream-rec-front/blob/master/docs/en/streamers.png)

Click on the `Add streamer` button to add a new streamer.

In the page, fill the form with the streamer's information. If a correct URL is provided, the tool will identify the platform and show proper platform
configuration.

> [!NOTE]\
> Things to note:
> - Each streamer has its own download settings. These settings are empty by default, which means the global settings of each platform will be used.
    Changing these settings overrides the global settings for this streamer.
> - Changing these settings will take effect **immediately** if the streamer is recording. The current recording will be cancelled, and a new
    recording
    will start with the new settings.

# 4.1. Streamer callbacks

Streamers can have callbacks. These callbacks are executed when a part of the stream is downloaded, or when the stream is finished.

These callbacks are executed concurrently but parallel with the download process. This means that you can run a script or a command without
interrupting the download process.

For example, you can run a script to convert the downloaded part to another format, or you can run a script to upload the downloaded part to a cloud,
etc.

Uploads by Rclone are supported out of the box. You can use the `rclone` command to upload the downloaded part to a cloud.

> [!IMPORTANT]\
> Things to note:
> - Configuring callbacks is optional. You can leave it empty if you don't need it.
> - Configuring callbacks disables the `Global delete files flag` global flag. You can still delete files by setting a `delete` action in the
    callback.
> - If a callback action fails, subsequent actions will not be executed. So, make sure your callback actions are reliable.
> - The path of downloaded files is passed to the first argument of the callback action. (separated by new line if multiple files are downloaded).

# 4.2. Template users

You can use the `Template users` feature to create a base configuration for multiple streamers. This feature is useful when you have multiple
streamers with the same configuration.

> [!NOTE]\
> Things to note:
> - Using a template user will disable the streamer's download settings. The streamer will use the template user's download settings instead.
> - Changing the template user's settings will affect all streamers that use this template user, and if the streamer is recording, the changes will
    take effect **immediately**, cancelling the current recording and starting a new one with the new settings.
> - Template users **cannot be deleted** if there are streamers that use them. Neither can be converted to a normal streamer if there are streamers
    that use them.

# 5. Recording status

Navigate to the dashboard page by clicking on the dashboard icon in the left navigation bar.

![dashboard.png](https://github.com/hua0512/stream-rec-front/blob/master/docs/en/dashboard.png)

Here you can see the status of the recordings, whether they are running or disabled. You can also see the download speed and the duration of the
recording.

> [!NOTE]\
> Things to note:
> - The download speed/recording duration will no longer be updated after a long time of inactivity on this page. This is to reduce server load.
    Refresh the page to update this information.