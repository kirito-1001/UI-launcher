package app.uilauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val MAX_PAGES = 10

/** An app placed on a home screen cell. [id] is unique so the same app can be placed twice. */
data class HomeItem(
    val id: String,
    val page: Int,
    val row: Int,
    val col: Int,
    val key: String,
)

/** Everything the user arranged: pages, the icons on them and the dock. Persisted as JSON. */
data class HomeLayout(
    val pages: Int = 1,
    val items: List<HomeItem> = emptyList(),
    val dock: List<String> = emptyList(),
    val initialized: Boolean = false,
) {
    fun isFree(page: Int, row: Int, col: Int, ignoreId: String? = null): Boolean =
        items.none { it.page == page && it.row == row && it.col == col && it.id != ignoreId }

    fun move(id: String, page: Int, row: Int, col: Int): HomeLayout =
        copy(items = items.map { if (it.id == id) it.copy(page = page, row = row, col = col) else it })

    fun remove(id: String): HomeLayout = copy(items = items.filter { it.id != id })

    fun removePackage(packageName: String): HomeLayout {
        val prefix = "$packageName/"
        return copy(
            items = items.filterNot { it.key.startsWith(prefix) },
            dock = dock.filterNot { it.startsWith(prefix) },
        )
    }

    fun addPage(): HomeLayout = if (pages >= MAX_PAGES) this else copy(pages = pages + 1)

    /** Only an empty page can be removed; pages after it shift down by one. */
    fun removePage(page: Int): HomeLayout {
        if (pages <= 1 || page !in 0 until pages) return this
        if (items.any { it.page == page }) return this
        return copy(
            pages = pages - 1,
            items = items.map { if (it.page > page) it.copy(page = it.page - 1) else it },
        )
    }

    fun hasItemsOn(page: Int): Boolean = items.any { it.page == page }

    /** Puts [key] in the first free cell, starting at [startPage]. Adds a page when everything is full. Null if impossible. */
    fun add(key: String, startPage: Int, cols: Int, rows: Int): HomeLayout? {
        val order = (0 until pages).sortedBy { if (it >= startPage) it - startPage else it + pages }
        for (page in order) {
            for (r in 0 until rows) for (c in 0 until cols) {
                if (isFree(page, r, c)) {
                    return copy(items = items + HomeItem(UUID.randomUUID().toString(), page, r, c, key))
                }
            }
        }
        if (pages >= MAX_PAGES) return null
        return copy(
            pages = pages + 1,
            items = items + HomeItem(UUID.randomUUID().toString(), pages, 0, 0, key),
        )
    }

    fun addToDock(key: String, max: Int): HomeLayout? =
        if (dock.size >= max || dock.contains(key)) null else copy(dock = dock + key)

    fun removeFromDock(key: String): HomeLayout = copy(dock = dock.filterNot { it == key })

    /** After the grid got smaller, moves icons that no longer fit to free cells (adding pages if needed). */
    fun fit(cols: Int, rows: Int): HomeLayout {
        val inside = items.filter { it.row < rows && it.col < cols }
        val outside = items.filter { it.row >= rows || it.col >= cols }.sortedWith(compareBy({ it.page }, { it.row }, { it.col }))
        if (outside.isEmpty()) return this
        var result = copy(items = inside)
        for (item in outside) {
            var placed = false
            search@ for (page in 0 until result.pages) {
                for (r in 0 until rows) for (c in 0 until cols) {
                    if (result.isFree(page, r, c)) {
                        result = result.copy(items = result.items + item.copy(page = page, row = r, col = c))
                        placed = true
                        break@search
                    }
                }
            }
            if (!placed) {
                result = result.copy(
                    pages = result.pages + 1,
                    items = result.items + item.copy(page = result.pages, row = 0, col = 0),
                )
            }
        }
        return result
    }

    fun toJson(): String {
        val itemsJson = JSONArray()
        for (item in items) {
            itemsJson.put(
                JSONObject()
                    .put("id", item.id)
                    .put("p", item.page)
                    .put("r", item.row)
                    .put("c", item.col)
                    .put("k", item.key)
            )
        }
        val dockJson = JSONArray()
        for (key in dock) dockJson.put(key)
        return JSONObject()
            .put("v", 1)
            .put("pages", pages)
            .put("init", initialized)
            .put("items", itemsJson)
            .put("dock", dockJson)
            .toString()
    }

    companion object {
        fun fromJson(text: String?): HomeLayout {
            if (text.isNullOrBlank()) return HomeLayout()
            return try {
                val json = JSONObject(text)
                val itemsJson = json.optJSONArray("items") ?: JSONArray()
                val items = ArrayList<HomeItem>()
                for (i in 0 until itemsJson.length()) {
                    val o = itemsJson.getJSONObject(i)
                    items.add(HomeItem(o.getString("id"), o.getInt("p"), o.getInt("r"), o.getInt("c"), o.getString("k")))
                }
                val dockJson = json.optJSONArray("dock") ?: JSONArray()
                val dock = ArrayList<String>()
                for (i in 0 until dockJson.length()) dock.add(dockJson.getString(i))
                HomeLayout(
                    pages = json.optInt("pages", 1).coerceIn(1, MAX_PAGES),
                    items = items,
                    dock = dock,
                    initialized = json.optBoolean("init", false),
                )
            } catch (e: Exception) {
                HomeLayout()
            }
        }
    }
}
