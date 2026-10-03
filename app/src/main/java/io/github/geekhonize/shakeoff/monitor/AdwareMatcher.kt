package io.github.geekhonize.shakeoff.monitor

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 广告应用条目。
 *
 * @param packageName 包名
 * @param name 应用名称
 * @param category 分类
 */
data class AdwareEntry(
    val packageName: String,
    val name: String,
    val category: String
)

/**
 * 广告特征库加载与匹配。
 *
 * 特征库来自 assets/adware_packages.json，
 * 参考 AdwareZoo 与 Exodus Privacy 的公开条目。
 */
class AdwareMatcher private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AdwareMatcher"
        private const val ASSET_NAME = "adware_packages.json"

        @Volatile
        private var instance: AdwareMatcher? = null

        /**
         * 获取单例。
         */
        fun getInstance(context: Context): AdwareMatcher =
            instance ?: synchronized(this) {
                instance ?: AdwareMatcher(context.applicationContext).also { instance = it }
            }
    }

    /**
     * 精确匹配表：包名 -> 条目。
     */
    private val exactMatches: Map<String, AdwareEntry> by lazy { loadLibrary() }

    /**
     * 前缀匹配表：用于识别同一开发者的关联包。
     */
    private val prefixMatches: List<Pair<String, AdwareEntry>> by lazy {
        exactMatches.entries.map { (pkg, entry) ->
            // 取到最后一个点作为前缀，例如 com.qihoo360.ads -> com.qihoo360.
            val idx = pkg.lastIndexOf('.')
            if (idx > 0) pkg.substring(0, idx + 1) to entry else pkg to entry
        }
    }

    /**
     * 从 assets 加载特征库。
     */
    private fun loadLibrary(): Map<String, AdwareEntry> {
        return try {
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val array = root.getJSONArray("packages")
            val result = HashMap<String, AdwareEntry>(array.length())

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("package")
                if (pkg.isNotEmpty()) {
                    result[pkg] = AdwareEntry(
                        packageName = pkg,
                        name = obj.optString("name", pkg),
                        category = obj.optString("category", "未知")
                    )
                }
            }
            Log.i(TAG, "已加载广告特征库，共 ${result.size} 条")
            result
        } catch (e: Exception) {
            Log.e(TAG, "加载广告特征库失败", e)
            emptyMap()
        }
    }

    /**
     * 精确匹配单个包名。
     */
    fun match(packageName: String): AdwareEntry? = exactMatches[packageName]

    /**
     * 批量匹配。
     */
    fun matchAll(packageNames: List<String>): Map<String, AdwareEntry> {
        val result = HashMap<String, AdwareEntry>()
        for (pkg in packageNames) {
            match(pkg)?.let { result[pkg] = it }
        }
        return result
    }

    /**
     * 前缀匹配：判断包名是否属于某个已知广告开发者的子包。
     *
     * 例如已知 com.qihoo360.ads，则 com.qihoo360.ads.inner 也算命中。
     */
    fun matchByPrefix(packageName: String): AdwareEntry? {
        if (exactMatches.containsKey(packageName)) return null
        return prefixMatches.firstOrNull { (prefix, _) -> packageName.startsWith(prefix) }?.second
    }

    /**
     * 特征库条目总数。
     */
    fun size(): Int = exactMatches.size

    /**
     * 是否为疑似广告应用（精确或前缀命中）。
     */
    fun isAdware(packageName: String): Boolean =
        exactMatches.containsKey(packageName) || matchByPrefix(packageName) != null
}
