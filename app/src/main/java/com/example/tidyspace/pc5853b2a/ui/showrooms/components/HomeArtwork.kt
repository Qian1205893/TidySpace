package com.example.tidyspace.pc5853b2a.ui.showrooms.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.theme.HomeColors

/** Snapshot-derived illustration using the same furniture artwork as the inventory. */
@SuppressLint("DiscouragedApi")
@Composable
internal fun HomeArtwork(home: SavedHome,modifier: Modifier,centerBuilding: Boolean=false) {
    val context=LocalContext.current
    val placed=remember(home.game.items) {
        home.game.items.filter { !it.packed && it.spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) }
            .sortedBy { it.position.x+it.position.z }
    }
    val pictures=placed.map { item ->
        val resource=remember(item.spec.assetId) {
            context.resources.getIdentifier("furniture_"+item.spec.assetId.replace('-','_'),
                "drawable",context.packageName)
        }
        item to if(resource!=0) painterResource(resource) else null
    }
    Canvas(modifier.semantics { contentDescription="${home.name}的存档布置示意" }) {
        val rectangles=listOf(
            floatArrayOf(RoomLayout.left,RoomLayout.right,RoomLayout.back,RoomLayout.front),
            floatArrayOf(BathroomLayout.left,BathroomLayout.right,BathroomLayout.back,BathroomLayout.front),
            floatArrayOf(GamingLayout.left,GamingLayout.right,GamingLayout.back,GamingLayout.front),
            floatArrayOf(RoomLayout.right,RoomLayout.balconyRight,RoomLayout.back,RoomLayout.front))
        fun project(x: Float,y: Float,z: Float)=Offset((x-z)*105f,(x+z)*53f-y*145f)
        val points=rectangles.flatMap { (l,r,b,f) ->
            listOf(project(l,-.32f,b),project(r,-.32f,b),project(r,-.32f,f),project(l,-.32f,f))
        }
        val left=points.minOf { it.x };val right=points.maxOf { it.x }
        val artworkTop=placed.minOfOrNull {
            project(it.position.x,it.position.y+it.spec.size.y/2,it.position.z).y-30f
        }
        val top=minOf(points.minOf { it.y }-.23f*145f,
            if(centerBuilding) Float.POSITIVE_INFINITY else artworkTop ?: Float.POSITIVE_INFINITY)
        val bottom=points.maxOf { it.y }+if(centerBuilding) .06f*145f else 0f
        val scale=minOf(size.width/(right-left+40f),size.height/(bottom-top+40f))
        fun point(x: Float,y: Float,z: Float): Offset {
            val p=project(x,y,z)
            return Offset((p.x-(left+right)/2)*scale+size.width/2,(p.y-(top+bottom)/2)*scale+size.height/2)
        }
        for((l,r,b,f) in rectangles) {
            val t=listOf(point(l,-.32f,b),point(r,-.32f,b),point(r,-.32f,f),point(l,-.32f,f))
            val low=listOf(point(l,-.38f,b),point(r,-.38f,b),point(r,-.38f,f),point(l,-.38f,f))
            polygon(listOf(t[1],t[2],low[2],low[1]),HomeColors.floorEdge)
            polygon(listOf(t[2],t[3],low[3],low[2]),HomeColors.floorEdge)
            polygon(t,HomeColors.floorOak)
            // Thin timber seams belong to the saved-room illustration.
            var plank=l+.16f
            while(plank<r) {
                drawLine(HomeColors.floorEdge.copy(alpha=.3f),point(plank,-.32f,b),point(plank,-.32f,f),
                    strokeWidth=.8f)
                plank+=.16f
            }
            polygon(listOf(point(l,-.32f,b),point(r,-.32f,b),point(r,-.09f,b),point(l,-.09f,b)),HomeColors.wallCream)
        }
        pictures.forEach { (item,painter) ->
            if(painter!=null) {
                val p=item.position
                val anchor=point(p.x,p.y-item.spec.size.y/2,p.z)
                val extent=maxOf(item.spec.size.x*105f,item.spec.size.z*105f,item.spec.size.y*145f)
                val side=(extent*scale*1.15f).coerceAtLeast(10f)
                translate(anchor.x-side/2,anchor.y-side*.82f) {
                    with(painter) { draw(Size(side,side)) }
                }
            }
        }
    }
}

private fun DrawScope.polygon(points: List<Offset>,color: Color) {
    val path=Path().apply {
        moveTo(points.first().x,points.first().y)
        points.drop(1).forEach { lineTo(it.x,it.y) }
        close()
    }
    drawPath(path,color)
}
