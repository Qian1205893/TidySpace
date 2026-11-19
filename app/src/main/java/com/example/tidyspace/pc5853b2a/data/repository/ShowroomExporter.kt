package com.example.tidyspace.pc5853b2a.data.repository

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.ShowroomUseCase
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export the player's current arrangement, never a stock panorama mislabeled as their work. */
class ShowroomExporter(private val context: Context) {
    fun create(game: RoomGame,reference: Showroom?,title: String,note: String): File {
        check(game.selectedId==null && game.cardDrag==null)
        val folder=File(context.cacheDir,"creations").apply { mkdirs() }
        val file=File(folder,"TidySpace-creation-${System.currentTimeMillis()}.zip")
        val json=JSONObject().put("version",1).put("title",title).put("note",note)
            .put("referenceId",reference?.id).put("referenceTitle",reference?.title)
            .put("preview","current-layout.png").put("previewType","当前摆放俯视图")
            .put("changes",org.json.JSONArray(reference?.let { ShowroomUseCase().changed(game,it) }.orEmpty()))
            .put("snapshot",RoomPersistence.encode(game))
        val bitmap=preview(game,title)
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("creation.json"));zip.write(json.toString(2).toByteArray());zip.closeEntry()
                zip.putNextEntry(ZipEntry("current-layout.png"));bitmap.compress(Bitmap.CompressFormat.PNG,100,zip);zip.closeEntry()
                zip.putNextEntry(ZipEntry("说明.txt"))
                zip.write("这是玩家当前摆放的作品包，包含物品位置、朝向、库存与参考来源。预览为当前摆放俯视图。可通过系统分享发送给朋友。\n$title\n$note".toByteArray())
                zip.closeEntry()
            }
        } finally { bitmap.recycle() }
        return file
    }
    fun share(file: File): File {
        val uri=FileProvider.getUriForFile(context,context.packageName+".creations",file)
        val intent=Intent(Intent.ACTION_SEND).setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,"我的 TidySpace 创意改造")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData=ClipData.newRawUri("TidySpace 创意改造",uri)
        context.startActivity(Intent.createChooser(intent,"分享我的创意改造").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return file
    }
    private fun preview(game: RoomGame,title: String): Bitmap {
        val bitmap=Bitmap.createBitmap(1400,1300,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(244,240,226))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.rgb(41,56,47);paint.textSize=40f
        canvas.drawText(title.take(28),70f,64f,paint)
        paint.textSize=23f;canvas.drawText("当前摆放俯视图 · ${game.mood.label} · 可继续自由改造",70f,104f,paint)
        fun x(value: Float)=80f+(value+1.58f)*325f
        fun z(value: Float)=180f+(value+1.42f)*265f
        paint.color=Color.rgb(221,209,184)
        for(area in listOf(floatArrayOf(-1.533f,-1.38f,1.575f,.899f),
            floatArrayOf(-1.533f,.899f,-.103f,2.259f),floatArrayOf(-.103f,.899f,1.575f,2.48f),
            floatArrayOf(1.575f,-1.38f,2.132f,.899f)))
            canvas.drawRect(x(area[0]),z(area[1]),x(area[2]),z(area[3]),paint)
        game.items.filter { !it.packed && it.spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) }
            .sortedBy { it.position.y }.forEach { i ->
                canvas.save();canvas.translate(x(i.position.x),z(i.position.z));canvas.rotate(-i.rotation.yaw())
                paint.color=if(i.spec.kind.playable) Color.rgb(185,142,91) else Color.rgb(117,143,104)
                val w=i.spec.size.x*325;val h=i.spec.size.z*265
                canvas.drawRoundRect(-w/2,-h/2,w/2,h/2,5f,5f,paint)
                paint.color=Color.rgb(41,56,47);paint.textSize=12f
                canvas.drawText(i.spec.name.take(7),-w/2,-h/2-3,paint)
                canvas.restore()
            }
        return bitmap
    }
}
