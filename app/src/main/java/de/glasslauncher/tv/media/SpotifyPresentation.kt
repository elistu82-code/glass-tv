package de.glasslauncher.tv.media

/** UI clock interpolation only; no session polling or seeking. */
internal fun songProgress(positionMs:Long,durationMs:Long,updatedAtMs:Long,speed:Float,playing:Boolean,nowMs:Long):Float? {
    if(durationMs<=0 || positionMs<0) return null
    val elapsed=if(playing && updatedAtMs>0 && speed.isFinite() && speed>0) (nowMs-updatedAtMs).coerceAtLeast(0).toDouble()*speed else 0.0
    return ((positionMs.toDouble()+elapsed)/durationMs).coerceIn(0.0,1.0).toFloat()
}
internal fun suitableMusicArtwork(width:Int,height:Int,recycled:Boolean):Boolean =
    !recycled && width>=64 && height>=64 && width.toDouble()/height in .6..1.67
