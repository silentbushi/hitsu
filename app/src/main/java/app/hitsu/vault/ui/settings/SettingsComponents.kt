package app.hitsu.vault.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.hitsu.vault.R
import androidx.compose.ui.res.stringResource
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = HitsuIcons.ArrowLeft,
            contentDescription = stringResource(R.string.cd_back),
            tint = HitsuColors.TextPrimary,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onBack)
                .size(22.dp),
        )
        Text(title, style = HitsuType.Title)
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title.uppercase(),
        style = HitsuType.Meta.copy(letterSpacing = HitsuType.Meta.fontSize * 0.08f),
        color = HitsuColors.TextMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HitsuColors.Stroke),
    )
}

/** One row of the list: title, an optional value in the accent, and a chevron when it leads on. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        SettingsDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = HitsuType.Body, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(value, style = HitsuType.Body, color = HitsuColors.Accent)
            }
            when {
                trailing != null -> {
                    Box(Modifier.padding(start = 12.dp)) { trailing() }
                }
                onClick != null -> Icon(
                    imageVector = HitsuIcons.ChevronRight,
                    contentDescription = null,
                    tint = HitsuColors.TextMuted,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Column {
        SettingsDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(HitsuColors.Bg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) HitsuColors.Accent else HitsuColors.Stroke),
                )
                Box(
                    Modifier
                        .size(if (selected) 12.dp else 18.dp)
                        .clip(CircleShape)
                        .background(HitsuColors.Bg),
                )
                if (selected) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HitsuColors.Accent),
                    )
                }
            }
            Text(label, style = HitsuType.Body)
        }
    }
}

@Composable
fun SettingsNote(text: String) {
    Text(
        text = text,
        style = HitsuType.Caption,
        color = HitsuColors.TextMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}
