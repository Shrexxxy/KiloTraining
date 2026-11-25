package app.calorietraining

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun filterChipBorderColor(colors: ColorScheme): Color = colors.onSurfaceVariant

@Composable
internal fun DiaryFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    FilterChip(selected, onClick, label, modifier, enabled, leadingIcon,
        border = FilterChipDefaults.filterChipBorder(enabled, selected,
            borderColor = filterChipBorderColor(MaterialTheme.colorScheme), borderWidth = 1.dp))
}
