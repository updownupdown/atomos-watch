// The Nu complication: Nu, and Play. A tap opens straight into the next run for the atom being
// built, with the game Nu wants to play. Short-text slots show Play and the game; image slots
// show Nu.

package app.buildatomos.watch.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.buildatomos.watch.AppModel
import app.buildatomos.watch.MainActivity
import app.buildatomos.watch.R

class NuComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = make(type, "Orbit")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        make(request.complicationType, AppModel.get(this).nuPick().label)

    private fun make(type: ComplicationType, game: String): ComplicationData? {
        val open = Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_BUILD, true)
        val tap = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val said = text("Play $game with Nu")
        val nu = Icon.createWithResource(this, R.drawable.ic_nu)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text("Play"), said)
                    .setTitle(text(game))
                    .setMonochromaticImage(MonochromaticImage.Builder(nu).build())
                    .setTapAction(tap)
                    .build()
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(MonochromaticImage.Builder(nu).build(), said).setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(SmallImage.Builder(nu, SmallImageType.ICON).build(), said).setTapAction(tap).build()
            else -> null
        }
    }

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}
