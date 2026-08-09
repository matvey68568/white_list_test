package com.matvey68568.whitelisttester

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Класс для загрузки списков сайтов из удаленного репозитория
 * https://github.com/matvey68568/white_list_test_lists
 */
class SiteListFetcher {

    companion object {
        const val SITES_JSON_URL = "https://raw.githubusercontent.com/matvey68568/white_list_test_lists/refs/heads/main/sites.json"
        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT = 10000
    }

    data class SiteLists(
        val whitelist: List<String>,
        val external: List<String>
    )

    /**
     * Загружает списки сайтов из удаленного репозитория
     * @return SiteLists со списками whitelist и external
     * @throws IOException при ошибке сети
     * @throws Exception при ошибке парсинга JSON
     */
    suspend fun fetchSiteLists(): SiteLists = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(SITES_JSON_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "GET"
            connection.useCaches = false
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Failed to fetch site lists. Response code: $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            parseSiteLists(responseBody)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Парсит JSON ответ в SiteLists
     */
    private fun parseSiteLists(jsonString: String): SiteLists {
        val jsonObject = JSONObject(jsonString)
        
        val whitelistJson = jsonObject.getJSONArray("whitelist")
        val externalJson = jsonObject.getJSONArray("external")

        val whitelist = (0 until whitelistJson.length()).map { i ->
            ensureHttpsProtocol(whitelistJson.getString(i))
        }

        val external = (0 until externalJson.length()).map { i ->
            ensureHttpsProtocol(externalJson.getString(i))
        }

        return SiteLists(whitelist, external)
    }

    /**
     * Добавляет https:// к URL если его нет
     */
    private fun ensureHttpsProtocol(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
    }

    /**
     * Загружает списки сайтов с fallback на дефолтные значения при ошибке
     * @param defaultLists списки по умолчанию
     * @return SiteLists с загруженными или дефолтными значениями
     */
    suspend fun fetchSiteListsOrDefault(defaultLists: SiteLists): SiteLists {
        return try {
            fetchSiteLists()
        } catch (e: Exception) {
            // При ошибке загрузки используем дефолтные списки
            defaultLists
        }
    }
}
