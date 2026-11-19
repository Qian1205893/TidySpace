package com.example.tidyspace.pc5853b2a.data.repository

import android.content.Context
import com.example.tidyspace.pc5853b2a.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

interface ShowroomRepository {
    fun rooms(): List<Showroom>
    fun session(): ShowroomSession
    fun save(game: RoomGame,session: ShowroomSession)
}

class LocalShowroomRepository(context: Context): ShowroomRepository {
    private val context=context.applicationContext
    private val prefs=this.context.getSharedPreferences("tidy-room",Context.MODE_PRIVATE)
    private var current: ShowroomSession?=null
    override fun rooms(): List<Showroom> = parse(context.assets.open("showrooms.json").bufferedReader().use { it.readText() })
    override fun session(): ShowroomSession = current ?: runCatching {
        val json=JSONObject(prefs.getString(KEY,null) ?: "{}")
        ShowroomSession(json.optional("reference"),RoomPersistence.decode(json.optional("undo")),
            json.optional("tutorial"),json.optInt("step").coerceIn(0,5),
            json.optString("title","我的创意改造"),json.optString("note"))
    }.getOrDefault(ShowroomSession()).also { current=it }
    override fun save(game: RoomGame,session: ShowroomSession) {
        val metadata=JSONObject().put("reference",session.reference).put("tutorial",session.tutorial)
            .put("step",session.step).put("title",session.title).put("note",session.note)
            .put("undo",session.undo?.let { RoomPersistence.encode(it) }).toString()
        val expansion=CleaningExpansionPersistence.encode(game)
        val editor=prefs.edit().putString("snapshot",RoomPersistence.encode(game)).putString(KEY,metadata)
        if(expansion==null) editor.remove(CleaningExpansionPersistence.KEY)
        else editor.putString(CleaningExpansionPersistence.KEY,expansion)
        check(editor.commit()) { "小屋未能保存" }
        current=session
    }
    companion object {
        const val KEY="showroom-session-v1"
        fun parse(text: String): List<Showroom> = JSONObject(text).getJSONArray("rooms").objects().map { r ->
            Showroom(r.getString("id"),r.getString("title"),r.getString("style"),r.getString("difficulty"),
                r.getString("brief"),r.getString("feature"),r.getString("challenge"),
                RoomMood.entries.first { it.label==r.getString("mood") },r.getDouble("dayHour").toFloat(),
                r.getDouble("brightness").toFloat(),r.getJSONArray("items").objects().map { t ->
                    val p=t.getJSONArray("position")
                    ShowroomTarget(t.getString("id"),Point3(p.getDouble(0).toFloat(),p.getDouble(1).toFloat(),
                        p.getDouble(2).toFloat()),t.getDouble("yaw").toFloat(),t.optional("support"),t.getString("zone"))
                },r.getJSONArray("steps").objects().map { t ->
                    val a=t.getJSONArray("itemIds")
                    ShowroomStep(t.getString("title"),t.getString("hint"),(0 until a.length()).map { a.getString(it) })
                },r.getJSONArray("details").objects().map { t ->
                    ShowroomDetail(t.getString("title"),t.getString("file").substringAfterLast('/').substringBeforeLast('.'))
                },r.getJSONArray("variants").objects().map { t ->
                    ShowroomVariant(t.getString("title"),t.getString("remove"),t.getString("add"),t.getString("reason"))
                },JSONObject(text).optInt("version",1))
        }
        private fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
        private fun JSONObject.optional(key: String)=if(isNull(key) || !has(key)) null else getString(key)
    }
}
