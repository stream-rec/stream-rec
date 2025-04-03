/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.utils

import java.io.File

/**
 * Functions for operating system related tasks
 * @author hua0512
 * @date : 2024/5/26 20:19
 */

/**
 * Check if the current OS is windows
 *
 * @return true if the current OS is windows
 */
fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win", true)

/**
 * Check if the current OS is unix based
 *
 * @return true if the current OS is unix based
 */
fun isUnix(): Boolean =
  System.getProperty("os.name").contains("nix", true) || System.getProperty("os.name")
    .contains("nux", true) || System.getProperty("os.name")
    .contains("aix", true)


/**
 * Check if it is running in a docker container
 *
 * [source](https://stackoverflow.com/questions/20010199/how-to-determine-if-a-process-runs-inside-lxc-docker/25518345#25518345)
 * @return true if it is running in a docker container else false
 *
 */
fun isDockerized() =
  File("/.dockerenv").exists() || File("/.dockerinit").exists() || isUnix() && File("/proc/1/cgroup").readLines().any {
    it.contains("docker") || it.contains("kubepods")
  }