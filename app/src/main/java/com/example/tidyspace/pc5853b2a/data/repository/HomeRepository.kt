package com.example.tidyspace.pc5853b2a.data.repository

import android.content.Context
import com.example.tidyspace.pc5853b2a.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

interface HomeRepository {
    fun load(): HomeLibrary?
    /** Library and compatibility mirror must commit together or throw. */
    fun save(library: HomeLibrary)
}

class LocalHomeRepository(context: Context,preferenceName: String="tidy-room"): HomeRepository {
    private val prefs=context.applicationContext.getSharedPreferences(preferenceName,Context.MODE_PRIVATE)
    override fun load(): HomeLibrary? =
        prefs.getString(KEY,null)?.let { HomeLibraryCodec.decode(it) }

    override fun save(library: HomeLibrary) {
        val encoded=HomeLibraryCodec.encode(library)
        // Validate every snapshot before publishing a new index. Unknown formats
        // and corrupt originals are never replaced by an empty room.
        HomeLibraryCodec.decode(encoded)
        val home=library.active
        val expansion=CleaningExpansionPersistence.encode(home.game)
        val editor=prefs.edit().putString(KEY,encoded)
            .putString("snapshot",RoomPersistence.encode(home.game))
            .putString(LocalShowroomRepository.KEY,HomeLibraryCodec.session(home.session).toString())
        if(expansion==null) editor.remove(CleaningExpansionPersistence.KEY)
        else editor.putString(CleaningExpansionPersistence.KEY,expansion)
        check(editor.commit()) { "小屋未能保存" }
    }

    companion object { const val KEY="home-library-v1" }
}

object HomeLibraryCodec {
    fun session(s: ShowroomSession)=JSONObject().put("reference",s.reference)
        .put("tutorial",s.tutorial).put("step",s.step).put("title",s.title).put("note",s.note)
        .put("undo",s.undo?.let(RoomPersistence::encode))
    private fun readSession(j: JSONObject)=ShowroomSession(j.optional("reference"),
        j.optional("undo")?.let { checkNotNull(RoomPersistence.decode(it)) },
        j.optional("tutorial"),j.optInt("step"),j.optString("title","我的小屋"),j.optString("note"))
    fun encode(library: HomeLibrary): String=JSONObject().put("version",1)
        .put("activeId",library.activeId).put("achievements",JSONArray(library.achievements.sorted()))
        .put("playCounts",JSONObject(library.playCounts)).put("nextSerial",library.nextSerial)
        .put("tutorialComplete",library.tutorialComplete)
        .put("homes",JSONArray(library.homes.map { h ->
            JSONObject().put("id",h.id).put("name",h.name).put("savedAt",h.savedAt)
                .put("revision",h.revision).put("sourceTemplateId",h.sourceTemplateId)
                .put("sourceTemplateVersion",h.sourceTemplateVersion)
                .put("snapshot",RoomPersistence.encode(h.game))
                .put("cleaning",CleaningExpansionPersistence.encode(h.game))
                .put("session",session(h.session))
        })).toString()

    fun decode(text: String): HomeLibrary {
        val j=JSONObject(text)
        require(j.getInt("version")==1) { "不支持的小屋存档版本" }
        val a=j.getJSONArray("homes")
        require(a.length()>0)
        val homes=(0 until a.length()).map { i ->
            val h=a.getJSONObject(i)
            val game=checkNotNull(CleaningExpansionPersistence.restore(h.getString("snapshot"),h.optional("cleaning"))) {
                "小屋存档无法读取，原始数据已保留"
            }
            SavedHome(h.getString("id"),h.getString("name"),game,readSession(h.getJSONObject("session")),
                h.getLong("savedAt"),h.getLong("revision"),h.optional("sourceTemplateId"),
                if(h.isNull("sourceTemplateVersion")) null else h.getInt("sourceTemplateVersion")).also {
                require(it.id.isNotBlank() && it.name.isNotBlank() && it.revision>0)
            }
        }
        require(homes.map { it.id }.distinct().size==homes.size)
        val active=j.getString("activeId")
        require(homes.any { it.id==active })
        val counts=j.getJSONObject("playCounts")
        val achievements=j.getJSONArray("achievements")
        return HomeLibrary(homes,active,(0 until achievements.length()).map { achievements.getString(it) }.toSet(),
            counts.keys().asSequence().associateWith { counts.getLong(it).also { n->require(n in 0..1_000_000_000L) } },
            j.getInt("nextSerial").also { require(it>0) },j.getBoolean("tutorialComplete"))
    }
    private fun JSONObject.optional(key: String)=if(!has(key) || isNull(key)) null else getString(key)
}
