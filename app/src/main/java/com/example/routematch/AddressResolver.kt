package com.example.routematch

// Offline address resolution with local alias database and fuzzy matching
object AddressResolver {

    // Common Chinese address keywords for basic tokenization
    private val ADDRESS_KEYWORDS = listOf(
        "路", "街", "道", "巷", "弄", "号", "栋", "幢",
        "单元", "楼", "层", "室", "区", "苑", "园",
        "城", "广场", "大厦", "中心", "酒店", "医院",
        "学校", "超市", "市场", "小区", "村", "镇"
    )

    // Local address alias database
    // Maps short/alias names to full addresses
    private val ALIAS_MAP = mapOf(
        "北京西站" to "北京市丰台区莲花池东路118号",
        "北京站" to "北京市东城区毛家湾胡同甲13号",
        "北京南站" to "北京市丰台区永外大街车站路",
        "上海虹桥站" to "上海市闵行区申贵路1500号",
        "广州南站" to "广州市番禺区石壁街道南站北路"
    )

    // Common area name database for partial matching
    private val AREA_DATABASE = listOf(
        "北京市海淀区", "北京市朝阳区", "北京市东城区", "北京市西城区",
        "北京市丰台区", "北京市通州区", "北京市昌平区", "北京市大兴区",
        "上海市浦东新区", "上海市黄浦区", "上海市徐汇区",
        "广州市天河区", "广州市越秀区", "广州市海珠区",
        "深圳市南山区", "深圳市福田区", "深圳市宝安区",
        "杭州市西湖区", "杭州市上城区", "杭州市下城区",
        "成都市武侯区", "成都市锦江区", "成都市青羊区",
        "武汉市洪山区", "武汉市武昌区", "武汉市江汉区",
        "南京市鼓楼区", "南京市秦淮区", "南京市建邺区",
        "重庆市渝中区", "重庆市江北区", "重庆市南岸区"
    )

    /**
     * Resolve a raw address string using aliases and normalization.
     */
    fun resolve(rawAddress: String): String {
        if (rawAddress.isBlank()) return rawAddress

        // Check exact alias match first
        ALIAS_MAP.forEach { (alias, fullAddress) ->
            if (rawAddress.contains(alias)) {
                return fullAddress
            }
        }

        // Basic normalization: remove extra whitespace
        return rawAddress.trim().replace(Regex("\\s+"), "")
    }

    /**
     * Compute Levenshtein distance similarity between two strings.
     * Returns a value in [0, 1], where 1 = identical.
     */
    fun fuzzyMatch(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val distance = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - distance.toDouble() / maxLen
    }

    /**
     * Standard Levenshtein edit distance.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[a.length][b.length]
    }

    /**
     * Extract address components from a raw address string.
     * Returns the most specific location (street/road level + number).
     */
    fun extractAddressComponents(rawAddress: String): List<String> {
        val components = mutableListOf<String>()
        var remaining = rawAddress

        // Extract area name
        AREA_DATABASE.forEach { area ->
            if (remaining.contains(area)) {
                components.add(area)
                remaining = remaining.replace(area, "")
            }
        }

        // Extract street-level address
        ADDRESS_KEYWORDS.forEach { keyword ->
            val index = remaining.indexOf(keyword)
            if (index >= 0) {
                val start = maxOf(0, index - 15)
                val end = minOf(remaining.length, index + keyword.length + 10)
                val segment = remaining.substring(start, end).trim()
                if (segment.isNotEmpty() && segment !in components) {
                    components.add(segment)
                }
            }
        }

        return components.ifEmpty { listOf(rawAddress) }
    }

    /**
     * Check if two addresses are likely the same location (threshold = 0.75).
     */
    fun isSameLocation(addr1: String, addr2: String, threshold: Double = 0.75): Boolean {
        if (addr1 == addr2) return true
        if (addr1.contains(addr2) || addr2.contains(addr1)) return true
        return fuzzyMatch(addr1, addr2) >= threshold
    }
}
