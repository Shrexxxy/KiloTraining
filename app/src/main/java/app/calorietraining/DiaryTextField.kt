package app.calorietraining

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun DiaryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = if (LocalDiaryDark.current) DarkGlassStart.copy(alpha = .54f)
        else scheme.surfaceContainer.copy(alpha = .64f)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag("diary-field"),
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = glass,
            unfocusedContainerColor = glass,
            disabledContainerColor = glass.copy(alpha = glass.alpha * .6f),
            errorContainerColor = glass,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            errorBorderColor = scheme.error,
        ),
    )
}
