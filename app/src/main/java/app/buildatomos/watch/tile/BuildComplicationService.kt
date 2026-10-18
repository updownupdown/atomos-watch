// The complication: the atom being built, as far as it has come. A ring on faces that take a
// ranged value, its symbol and count on short-text slots, the mark on small-image slots.

package app.buildatomos.watch.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.buildatomos.watch.AppModel
import app.buildatomos.watch.MainActivity
import app.buildatomos.watch.R

class BuildComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = make(type, "C", 6, 3)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val model = AppModel.get(this)
        val c = model.collection
        if (c.done) return make(request.complicationType, "Og", 118, 118)
        return make(request.complicationType, model.atlas.atom(c.building).symbol, c.building, c.electrons)
    }

    private fun make(type: ComplicationType, symbol: String, z: Int, electrons: Int): ComplicationData? {
        val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val said = text("$symbol: $electrons of $z electrons")
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(electrons.toFloat(), 0f, z.toFloat(), said)
                    .setText(text(symbol))
                    .setTapAction(tap)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(symbol), said)
                    .setTitle(text("$electrons/$z"))
                    .setTapAction(tap)
                    .build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(Icon.createWithResource(this, R.drawable.ic_mark), SmallImageType.ICON).build(),
                    text("ATOMOS"),
                ).setTapAction(tap).build()
            else -> null
        }
    }

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}
