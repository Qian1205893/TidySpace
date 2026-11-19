package com.example.tidyspace.pc5853b2a.domain.model

/**
 * Mean texture alpha in the 8x3 material cells of tidy-renovation.glb.
 * Source SHA256: a16eb79a1fddd437bbd75c65e5c6d8e97c70037b74f5c260fc3ac286c33bdb81.
 * Completion and rendering share this footprint; transparent padding is not dirt.
 */
object CleaningVisibility {
    private val footprints=mapOf(
        "dust-0" to listOf(0,98169,353431,346891,301007,244952,56385,1,
            117,240075,547984,600124,606123,582538,482429,72782,
            0,60177,266843,290515,223458,94214,40252,784),
        "dust-1" to listOf(1501,66510,201006,301150,292935,283799,134132,4493,
            47289,359931,445228,500491,597987,472358,310263,9559,
            0,3633,177005,349766,358052,258805,36994,0),
        "wall-0" to listOf(0,43994,238990,310005,333373,170702,5340,0,
            6269,307207,485194,442490,379978,482538,317856,39683,
            3561,128650,283394,243720,272478,218214,73027,1951),
        "wall-1" to listOf(0,93048,361594,289793,246363,148311,43063,31,
            60,263389,522065,503301,455128,444238,445146,60983,
            0,43067,204355,359237,267162,129405,48107,554),
        // Measured mean alpha in the four 256x192 expanded-cleaning atlases.
        "dust-2" to listOf(583,40407,105714,209540,213418,207559,63085,0,
            28706,312059,331594,332801,331815,332269,204031,292,
            610,40543,106106,208969,213646,207152,62967,0),
        "dust-3" to listOf(0,27394,173050,263955,210664,143979,10923,0,
            3316,233347,332451,332218,332381,332525,268663,39304,
            2041,85540,175317,212495,160795,153700,61268,38),
        "spill-0" to listOf(6046,104796,184781,251391,224917,234587,111443,11,
            20311,356697,433489,433886,433574,431809,308060,12723,
            0,24706,171797,331429,300806,233321,22928,0),
        "spill-1" to listOf(0,16446,312991,114556,65811,87178,0,0,
            0,505,135496,61800,129541,164515,3,0,
            0,1354,172613,92207,0,0,0,0),
    ).mapValues { (_,values)->values.map { if(it<10_000) 0f else it/1_000_000f } }

    fun cells(c: CleaningState,m: Mess): List<Float> {
        val raw=CleaningGrid.cells(c,m)
        val weights=footprints[m.id] ?: return raw
        val visible=raw.mapIndexed { i,value->if(weights[i]==0f || value>=.90f) 1f else value }
        return if(weighted(visible,weights)>=.98f) List(CleaningGrid.COUNT) { 1f } else visible
    }
    fun amount(c: CleaningState,id: String): Float {
        val weights=footprints[id] ?: return c.rawAmount(id)
        // Legacy snapshots use a uniform opacity; retain their exact fraction.
        if(id !in c.workTiles) return if(c.rawAmount(id)>=.90f) 1f else c.rawAmount(id)
        val mess=cleaningMess().first { it.id==id }
        return weighted(cells(c,mess),weights)
    }
    private fun weighted(values: List<Float>,weights: List<Float>): Float =
        (values.indices.sumOf { (values[it]*weights[it]).toDouble() }/
            weights.sumOf { it.toDouble() }).toFloat().coerceIn(0f,1f)
}
