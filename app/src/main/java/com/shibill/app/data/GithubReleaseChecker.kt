package com.shibill.app.data

import com.shibill.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新（§0：唯一联网点，GitHub Releases JSON）。
 * 静默失败：网络异常/仓库不存在时返回 null，不打扰用户。
 */
object GithubReleaseChecker {

    data class UpdateInfo(val version: String, val url: String, val notes: String)

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/${Constants.UPDATE_REPO}/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ShiBill-Android")
            }
            try {
                if (connection.responseCode != 200) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v")
                val htmlUrl = json.optString("html_url")
                val notes = json.optString("body")
                if (tag.isEmpty() || htmlUrl.isEmpty()) null
                else if (isNewer(tag, currentVersion)) UpdateInfo(tag, htmlUrl, notes)
                else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** 语义化版本比较：a 是否比 b 新（如 1.2.0 > 1.1.9） */
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').mapNotNull { it.toIntOrNull() }
        val b = current.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
