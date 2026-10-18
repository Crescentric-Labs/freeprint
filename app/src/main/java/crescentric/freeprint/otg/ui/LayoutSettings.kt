package crescentric.freeprint.otg.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crescentric.freeprint.otg.R
import crescentric.freeprint.otg.print.Anchor
import crescentric.freeprint.otg.print.FitMode
import crescentric.freeprint.otg.print.MAX_SCALE_PERCENT
import crescentric.freeprint.otg.print.MIN_SCALE_PERCENT
import crescentric.freeprint.otg.print.Orientation
import crescentric.freeprint.otg.print.PageOrder
import crescentric.freeprint.otg.print.Paper
import crescentric.freeprint.otg.print.PerSheet
import crescentric.freeprint.otg.print.PrintOptions
import crescentric.freeprint.otg.print.effectiveMarginMm

/**
 * The layout settings that apply to any sheet, whether it is being printed or saved from a scan. They edit a [PrintOptions]
 * through an `update` callback, so the print screen (its view-model) and the scan save dialog (its own copy) share them.
 */
internal enum class LayoutSetting(@StringRes val titleRes: Int, val icon: ImageVector) {
    PAPER(R.string.setting_paper, Icons.Default.Description),
    COLOUR(R.string.setting_colour, Icons.Default.Palette),
    ORIENTATION(R.string.setting_orientation, Icons.Default.ScreenRotation),
    PER_SHEET(R.string.setting_per_sheet, Icons.Default.GridView),
    SIZING(R.string.setting_sizing, Icons.Default.FitScreen),
    MARGINS(R.string.setting_margins, Icons.Default.Crop),
    POSITION(R.string.setting_position, Icons.Default.OpenWith),
}

/** The current value of [s], shown as a tile's subtitle. */
@Composable
internal fun layoutSubtitle(o: PrintOptions, s: LayoutSetting): String = when (s) {
    LayoutSetting.PAPER -> o.paper.label
    LayoutSetting.COLOUR -> stringResource(if (o.color) R.string.mode_colour else R.string.mode_grayscale)
    LayoutSetting.ORIENTATION -> stringResource(o.orientation.labelRes)
    LayoutSetting.PER_SHEET ->
        if (o.perSheet == PerSheet.ONE) stringResource(R.string.sub_per_sheet_one)
        else stringResource(R.string.sub_per_sheet_n, o.perSheet.n, stringResource(o.order.labelRes)) +
            if (o.border) ", " + stringResource(R.string.sub_with_borders) else ""
    LayoutSetting.SIZING -> stringResource(R.string.sub_sizing, stringResource(o.fit.labelRes), o.scalePercent)
    LayoutSetting.MARGINS -> when (o.marginMm) {
        null -> stringResource(R.string.margin_auto)
        0 -> stringResource(R.string.margin_none)
        else -> stringResource(R.string.margin_mm, o.marginMm)
    }
    LayoutSetting.POSITION ->
        if (o.anchor == Anchor.CENTER && o.offsetXmm == 0 && o.offsetYmm == 0) stringResource(R.string.sub_pos_centre)
        else listOfNotNull(
            stringResource(o.anchor.labelRes),
            if (o.offsetXmm != 0) stringResource(R.string.sub_across, o.offsetXmm) else null,
            if (o.offsetYmm != 0) stringResource(R.string.sub_down, o.offsetYmm) else null,
        ).joinToString(", ")
}

/** The controls for [setting], to be placed inside a dialog; [onDismiss] closes it for the settings that choose in one tap. */
@Composable
internal fun LayoutSettingBody(setting: LayoutSetting, o: PrintOptions, update: (PrintOptions) -> Unit, onDismiss: () -> Unit) {
    when (setting) {
        LayoutSetting.PAPER -> RadioList(Paper.entries, o.paper, { it.label }) { update(o.copy(paper = it)); onDismiss() }
        LayoutSetting.COLOUR -> RadioList(listOf(true, false), o.color, { stringResource(if (it) R.string.colour_opt_colour else R.string.colour_opt_gray) }) {
            update(o.copy(color = it)); onDismiss()
        }
        LayoutSetting.ORIENTATION -> {
            RadioList(Orientation.entries, o.orientation, { stringResource(it.labelRes) }) { update(o.copy(orientation = it)); onDismiss() }
            Text(
                stringResource(R.string.orientation_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LayoutSetting.PER_SHEET -> {
            RadioList(PerSheet.entries, o.perSheet, { if (it == PerSheet.ONE) stringResource(R.string.per_sheet_one_opt) else stringResource(R.string.per_sheet_n_opt, it.n) }) {
                update(o.copy(perSheet = it))
            }
            if (o.perSheet != PerSheet.ONE) {
                HorizontalDivider()
                Text(stringResource(R.string.page_order), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                RadioList(PageOrder.entries, o.order, { stringResource(it.labelRes) }) { update(o.copy(order = it)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.border_each), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = o.border, onCheckedChange = { update(o.copy(border = it)) })
                }
            }
        }
        LayoutSetting.SIZING -> SizingControls(o, update)
        LayoutSetting.MARGINS -> MarginControls(o, update)
        LayoutSetting.POSITION -> PositionControls(o, update)
    }
}

@Composable
internal fun SizingControls(o: PrintOptions, update: (PrintOptions) -> Unit) {
    RadioList(FitMode.entries, o.fit, { stringResource(it.labelRes) }) { update(o.copy(fit = it)) }
    HorizontalDivider()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.scale_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text("${o.scalePercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = o.scalePercent.toFloat(),
        onValueChange = { update(o.copy(scalePercent = it.toInt())) },
        valueRange = MIN_SCALE_PERCENT.toFloat()..MAX_SCALE_PERCENT.toFloat()
    )
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(50, 75, 100, 125, 150, 200).forEach { pct ->
            FilterChip(
                selected = o.scalePercent == pct,
                onClick = { update(o.copy(scalePercent = pct)) },
                label = { Text("$pct%") },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}


@Composable
internal fun MarginControls(o: PrintOptions, update: (PrintOptions) -> Unit) {
    val shown = o.effectiveMarginMm
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = o.marginMm == null,
            onClick = { update(o.copy(marginMm = null)) },
            label = { Text(stringResource(R.string.margin_auto)) },
            shape = RoundedCornerShape(8.dp)
        )
        listOf(0, 5, 13, 25).forEach { mm ->
            FilterChip(
                selected = o.marginMm == mm,
                onClick = { update(o.copy(marginMm = mm)) },
                label = { Text(if (mm == 0) stringResource(R.string.margin_none) else stringResource(R.string.margin_mm, mm)) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
    Text(stringResource(R.string.margin_custom, shown), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Slider(
        value = shown.toFloat(),
        onValueChange = { update(o.copy(marginMm = it.toInt())) },
        valueRange = 0f..50f
    )
    Text(
        stringResource(R.string.margin_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}


@Composable
internal fun PositionControls(o: PrintOptions, update: (PrintOptions) -> Unit) {
    val isCustom = o.anchor != Anchor.CENTER || o.offsetXmm != 0 || o.offsetYmm != 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.pos_anchor), style = MaterialTheme.typography.labelSmall)
            Anchor.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { a ->
                        val active = a == o.anchor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { update(o.copy(anchor = a)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) Icon(Icons.Default.Check, contentDescription = stringResource(a.labelRes), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pos_across, o.offsetXmm), style = MaterialTheme.typography.bodySmall)
            Slider(o.offsetXmm.toFloat(), { update(o.copy(offsetXmm = it.toInt())) }, valueRange = -50f..50f)
            Text(stringResource(R.string.pos_down, o.offsetYmm), style = MaterialTheme.typography.bodySmall)
            Slider(o.offsetYmm.toFloat(), { update(o.copy(offsetYmm = it.toInt())) }, valueRange = -50f..50f)
        }
    }
    Text(
        stringResource(R.string.pos_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (isCustom) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { update(o.copy(anchor = Anchor.CENTER, offsetXmm = 0, offsetYmm = 0)) }) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.pos_reset))
            }
        }
    }
}

