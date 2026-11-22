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

/** The [HomeItem.key] of a folder is this prefix + the folder's id (apps have "package/activity" keys). */
const val FOLDER_PREFIX = "folder:"

/** A folder on the home screen: a name and the keys of the apps inside. Its icon is a [HomeItem] on a page. */
data class Folder(val id: String, val name: String, val apps: List<String>)

/** Everything the user arranged: pages, the icons on them, the folders and the dock. Persisted as JSON. */
data class HomeLayout(
    val pages: Int = 1,
    val items: List<HomeItem> = emptyList(),
    val dock: List<String> = emptyList(),
    val initialized: Boolean = false,
    val folders: List<Folder> = emptyList(),
) {
    fun folder(id: String): Folder? = folders.firstOrNull { it.id == id }

    /** The folder this item stands for, or null when it is a plain app. */
    fun folderOf(item: HomeItem): Folder? =
        if (item.key.startsWith(FOLDER_PREFIX)) folder(item.key.removePrefix(FOLDER_PREFIX)) else null

    fun itemOfFolder(folderId: String): HomeItem? = items.firstOrNull { it.key == FOLDER_PREFIX + folderId }

    /** Puts app [key] on [target]: into its folder, or (if it is an app) both become a new folder in that cell. */
    private fun putAppOn(target: HomeItem, key: String, newName: String): HomeLayout {
        val existing = folderOf(target)
        if (existing != null) {
            if (key in existing.apps) return this
            return copy(folders = folders.map { if (it.id == existing.id) it.copy(apps = it.apps + key) else it })
        }
        val id = UUID.randomUUID().toString()
        return copy(
            folders = folders + Folder(id, newName, listOf(target.key, key)),
            items = items.map { if (it.id == target.id) it.copy(key = FOLDER_PREFIX + id) else it },
        )
    }

    /**
     * An icon on the home screen ([dragId]) was dropped on another one ([targetId]): it goes into that
     * folder, or the two apps become a new folder. Folders are not nested, so a folder cannot be dropped.
     */
    fun mergeInto(dragId: String, targetId: String, newName: String): HomeLayout? {
        val drag = items.firstOrNull { it.id == dragId } ?: return null
        val target = items.firstOrNull { it.id == targetId } ?: return null
        if (drag.id == target.id || folderOf(drag) != null) return null
        if (folderOf(target) == null && drag.key == target.key) return null
        val merged = putAppOn(target, drag.key, newName)
        return merged.copy(items = merged.items.filter { it.id != drag.id })
    }

    /** An app from the drawer was dropped on the home item [targetId]. */
    fun dropAppOn(targetId: String, key: String, newName: String): HomeLayout? {
        val target = items.firstOrNull { it.id == targetId } ?: return null
        if (folderOf(target) == null && target.key == key) return null
        return putAppOn(target, key, newName)
    }

    fun rename(folderId: String, name: String): HomeLayout =
        copy(folders = folders.map { if (it.id == folderId) it.copy(name = name) else it })

    /** Takes [key] out of the folder onto the home grid (first free cell from the folder's page). Null if there is no room. */
    fun takeOut(folderId: String, key: String, cols: Int, rows: Int): HomeLayout? {
        val folder = folder(folderId) ?: return null
        if (key !in folder.apps) return null
        val without = copy(folders = folders.map { if (it.id == folderId) it.copy(apps = it.apps - key) else it })
        val placed = without.add(key, itemOfFolder(folderId)?.page ?: 0, cols, rows) ?: return null
        return placed.dropEmptyFolders()
    }

    /** Deletes the folder and keeps its apps: the first takes the folder's cell, the others go to free cells. */
    fun disband(folderId: String, cols: Int, rows: Int): HomeLayout {
        val folder = folder(folderId) ?: return this
        val folderItem = itemOfFolder(folderId)
        var result = copy(
            items = items.filterNot { it.key == FOLDER_PREFIX + folderId },
            folders = folders.filterNot { it.id == folderId },
        )
        for ((index, key) in folder.apps.withIndex()) {
            result = if (index == 0 && folderItem != null) {
                result.copy(
                    items = result.items + HomeItem(UUID.randomUUID().toString(), folderItem.page, folderItem.row, folderItem.col, key),
                )
            } else {
                result.add(key, folderItem?.page ?: 0, cols, rows) ?: result
            }
        }
        return result
    }

    /** A folder with no apps left disappears together with its icon. */
    private fun dropEmptyFolders(): HomeLayout {
        val empty = folders.filter { it.apps.isEmpty() }.map { FOLDER_PREFIX + it.id }.toSet()
        if (empty.isEmpty()) return this
        return copy(folders = folders.filter { it.apps.isNotEmpty() }, items = items.filterNot { it.key in empty })
    }

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
            folders = folders.map { f -> f.copy(apps = f.apps.filterNot { it.startsWith(prefix) }) },
        ).dropEmptyFolders()
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

    /** The free cell closest to ([row], [col]) on [page] as (row, col), or null when the page is full. */
    fun nearestFree(page: Int, row: Int, col: Int, cols: Int, rows: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!isFree(page, r, c)) continue
            val distance = (r - row) * (r - row) + (c - col) * (c - col)
            if (distance < bestDistance) {
                bestDistance = distance
                best = r to c
            }
        }
        return best
    }

    /** Places [key] in an exact cell. The caller must make sure the cell is free. */
    fun addAt(key: String, page: Int, row: Int, col: Int): HomeLayout =
        copy(items = items + HomeItem(UUID.randomUUID().toString(), page, row, col, key))

    fun addToDock(key: String, max: Int): HomeLayout? =
        if (dock.size >= max || dock.contains(key)) null else copy(dock = dock + key)

    /** Puts [key] in the dock at [index] (clamped to the end). Null when the dock is full or already has it. */
    fun insertInDock(key: String, index: Int, max: Int): HomeLayout? {
        if (dock.size >= max || dock.contains(key)) return null
        val list = dock.toMutableList()
        list.add(index.coerceIn(0, list.size), key)
        return copy(dock = list)
    }

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
        val foldersJson = JSONArray()
        for (f in folders) {
            foldersJson.put(JSONObject().put("id", f.id).put("n", f.name).put("a", JSONArray(f.apps)))
        }
        return JSONObject()
            .put("v", 1)
            .put("pages", pages)
            .put("init", initialized)
            .put("items", itemsJson)
            .put("dock", dockJson)
            .put("folders", foldersJson)
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
                // Older saves have no folders.
                val foldersJson = json.optJSONArray("folders") ?: JSONArray()
                val folders = ArrayList<Folder>()
                for (i in 0 until foldersJson.length()) {
                    val o = foldersJson.getJSONObject(i)
                    val appsJson = o.optJSONArray("a") ?: JSONArray()
                    val keys = ArrayList<String>()
                    for (j in 0 until appsJson.length()) keys.add(appsJson.getString(j))
                    folders.add(Folder(o.getString("id"), o.optString("n", ""), keys))
                }
                HomeLayout(
                    pages = json.optInt("pages", 1).coerceIn(1, MAX_PAGES),
                    items = items,
                    dock = dock,
                    initialized = json.optBoolean("init", false),
                    folders = folders,
                )
            } catch (e: Exception) {
                HomeLayout()
            }
        }
    }
}
