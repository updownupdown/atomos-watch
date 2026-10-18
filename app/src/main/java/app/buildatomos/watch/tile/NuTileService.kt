// The tile: the game Nu wants to play, and a rose Play that opens straight into it.

package app.buildatomos.watch.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.buildatomos.core.Ink
import app.buildatomos.watch.AppModel
import app.buildatomos.watch.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES = "1"

class NuTileService : TileService() {
    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val model = AppModel.get(this)
        val game = model.nuPick()
        val c = model.collection
        val building = if (c.done) null else model.atlas.atom(c.building)
        val layout = layout(request.deviceConfiguration, game.id, game.label, building?.let { "Building ${it.name}: ${c.electrons} of ${it.z}" } ?: "Every atom built")
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES)
            // Nu's pick changes each day and after each run; the app asks for a refresh then too.
            .setFreshnessIntervalMillis(60 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())

    private fun layout(device: DeviceParameters, id: String, label: String, line: String): LayoutElementBuilders.LayoutElement {
        val open = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(MainActivity.EXTRA_GAME, ActionBuilders.AndroidStringExtra.Builder().setValue(id).build())
                    .build(),
            )
            .build()
        val click = ModifiersBuilders.Clickable.Builder().setId("play").setOnClick(open).build()
        return PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "Nu wants to play").setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(Ink.ROSE)).build(),
            )
            .setContent(
                Text.Builder(this, label).setTypography(Typography.TYPOGRAPHY_DISPLAY3).setColor(argb(Ink.WHITE)).build(),
            )
            .setSecondaryLabelTextContent(
                Text.Builder(this, line).setTypography(Typography.TYPOGRAPHY_CAPTION2).setColor(argb(Ink.K300)).build(),
            )
            .setPrimaryChipContent(
                Chip.Builder(this, click, device)
                    .setPrimaryLabelContent("Play")
                    .setChipColors(ChipColors(Ink.ROSE, Ink.BLACK))
                    .build(),
            )
            .build()
    }
}
