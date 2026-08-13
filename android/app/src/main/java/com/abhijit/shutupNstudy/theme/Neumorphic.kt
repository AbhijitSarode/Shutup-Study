package com.abhijit.shutupNstudy.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Theme Colors
val NeuBg = Color(0xFFF0F0F3)
val NeuTextPrimary = Color(0xFF333336)
val NeuTextSecondary = Color(0xFF7E7E82)
val NeuTextMuted = Color(0xFFA0A0A5)

val ColorFocus = Color(0xFFFF6B6B)
val ColorFocusGlow = Color(0x33FF6B6B)
val ColorBreak = Color(0xFF4DADF7)
val ColorBreakGlow = Color(0x334DADF7)
val ColorLongBreak = Color(0xFF51CF66)
val ColorLongBreakGlow = Color(0x3351CF66)

val PauseBtnBg = Color(0xFFFEF3C7)
val PlayBtnBg = Color(0xFFECFDF5)

fun Modifier.neuFlat(
    cornerRadius: Dp = 16.dp,
    shadowOffset: Dp = 6.dp,
    blurRadius: Dp = 8.dp,
    darkShadowColor: Color = Color(0xFFD1D1D6),
    lightShadowColor: Color = Color(0xFFFFFFFF),
    backgroundColor: Color = NeuBg
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val width = size.width
        val height = size.height
        val radiusPx = cornerRadius.toPx()
        val offsetPx = shadowOffset.toPx()
        val blurPx = blurRadius.toPx()

        // 1. Draw dark shadow
        val darkPaint = Paint().apply {
            color = backgroundColor
        }
        val nativeDarkPaint = darkPaint.asFrameworkPaint()
        nativeDarkPaint.setShadowLayer(
            blurPx,
            offsetPx,
            offsetPx,
            darkShadowColor.toArgb()
        )
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = width,
            bottom = height,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = darkPaint
        )

        // 2. Draw light shadow
        val lightPaint = Paint().apply {
            color = backgroundColor
        }
        val nativeLightPaint = lightPaint.asFrameworkPaint()
        nativeLightPaint.setShadowLayer(
            blurPx,
            -offsetPx,
            -offsetPx,
            lightShadowColor.toArgb()
        )
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = width,
            bottom = height,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = lightPaint
        )

        // 3. Draw solid background to cover overlaps
        val bgPaint = Paint().apply {
            color = backgroundColor
            style = androidx.compose.ui.graphics.PaintingStyle.Fill
        }
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = width,
            bottom = height,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = bgPaint
        )
    }
}

fun Modifier.neuPressed(
    cornerRadius: Dp = 16.dp,
    darkShadowColor: Color = Color(0x1F000000), // Soft dark shadow (e.g. black with 12% alpha)
    lightShadowColor: Color = Color(0xD8FFFFFF), // Clean white highlight
    backgroundColor: Color = NeuBg
): Modifier = this.drawWithContent {
    val width = size.width
    val height = size.height
    val radiusPx = cornerRadius.toPx()
    
    // Inset shadow properties matching professional CSS:
    // Box-shadow: inset 3dp 3dp 5dp darkShadow, inset -3dp -3dp 5dp lightShadow
    val offsetPx = 3.dp.toPx()
    val strokeWidthPx = 6.dp.toPx()
    val blurPx = 4.dp.toPx()

    // Create rounded rect path for clipping
    val clipPath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx)
            )
        )
    }

    // Clip all inner shadow layers to stay strictly inside the bounds of the circular/rounded card shape
    clipPath(clipPath) {
        // 1. Fill base background behind content
        drawRoundRect(
            color = backgroundColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx)
        )

        // 2. Let the component draw its own content (e.g. text/icons)
        this@drawWithContent.drawContent()

        // 3. Draw the offset blurred inner shadows
        drawIntoCanvas { canvas ->
            // --- A. Dark Shadow (Offset to bottom-right, casting shadow on top-left inside) ---
            val darkPaint = Paint().apply {
                color = darkShadowColor
                style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                strokeWidth = strokeWidthPx
            }
            darkPaint.asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(
                left = offsetPx,
                top = offsetPx,
                right = width + offsetPx,
                bottom = height + offsetPx,
                radiusX = radiusPx,
                radiusY = radiusPx,
                paint = darkPaint
            )

            // --- B. White Highlight (Offset to top-left, casting highlight on bottom-right inside) ---
            val lightPaint = Paint().apply {
                color = lightShadowColor
                style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                strokeWidth = strokeWidthPx
            }
            lightPaint.asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(
                left = -offsetPx,
                top = -offsetPx,
                right = width - offsetPx,
                bottom = height - offsetPx,
                radiusX = radiusPx,
                radiusY = radiusPx,
                paint = lightPaint
            )
        }
    }
}
