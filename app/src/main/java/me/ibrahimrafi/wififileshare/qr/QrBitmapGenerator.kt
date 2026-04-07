package me.ibrahimrafi.wififileshare.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import kotlin.math.max
import kotlin.math.min

object QrBitmapGenerator {
    private const val QUIET_ZONE = 1
    private const val FINDER_PATTERN_SIZE = 7
    private const val CIRCLE_SCALE_DOWN_FACTOR = 0.72f
    private const val BACKGROUND_CORNER_RADIUS_RATIO = 0.08f

    fun generateQrBitmap(text: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        )
        val code = Encoder.encode(text, ErrorCorrectionLevel.H, hints)
        return renderDottedQr(code, sizePx, sizePx)
    }

    private fun renderDottedQr(code: QRCode, width: Int, height: Int): Bitmap {
        val input: ByteMatrix = code.matrix ?: error("QR matrix is null")
        val inputWidth = input.width
        val inputHeight = input.height

        val qrWidth = inputWidth + (QUIET_ZONE * 2)
        val qrHeight = inputHeight + (QUIET_ZONE * 2)
        val outputWidth = max(width, qrWidth)
        val outputHeight = max(height, qrHeight)

        val scale = max(1, min(outputWidth / qrWidth, outputHeight / qrHeight))
        val leftPadding = (outputWidth - (inputWidth * scale)) / 2
        val topPadding = (outputHeight - (inputHeight * scale)) / 2
        val moduleSize = (scale * CIRCLE_SCALE_DOWN_FACTOR).coerceAtLeast(1f)
        val offset = (scale - moduleSize) / 2f

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cornerRadius = min(outputWidth, outputHeight) * BACKGROUND_CORNER_RADIUS_RATIO
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()),
            cornerRadius,
            cornerRadius,
            backgroundPaint,
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }

        for (inputY in 0 until inputHeight) {
            val baseY = topPadding + inputY * scale
            for (inputX in 0 until inputWidth) {
                if (input[inputX, inputY].toInt() != 1) continue
                if (isInsideFinderRegion(inputX, inputY, inputWidth, inputHeight)) continue

                val baseX = leftPadding + inputX * scale
                canvas.drawOval(
                    RectF(
                        baseX + offset,
                        baseY + offset,
                        baseX + offset + moduleSize,
                        baseY + offset + moduleSize,
                    ),
                    paint,
                )
            }
        }

        val finderDiameter = scale * FINDER_PATTERN_SIZE
        drawFinderPatternCircleStyle(canvas, paint, leftPadding, topPadding, finderDiameter)
        drawFinderPatternCircleStyle(
            canvas,
            paint,
            leftPadding + (inputWidth - FINDER_PATTERN_SIZE) * scale,
            topPadding,
            finderDiameter,
        )
        drawFinderPatternCircleStyle(
            canvas,
            paint,
            leftPadding,
            topPadding + (inputHeight - FINDER_PATTERN_SIZE) * scale,
            finderDiameter,
        )

        return bitmap
    }

    private fun isInsideFinderRegion(x: Int, y: Int, width: Int, height: Int): Boolean {
        val topLeft = x < FINDER_PATTERN_SIZE && y < FINDER_PATTERN_SIZE
        val topRight = x >= width - FINDER_PATTERN_SIZE && y < FINDER_PATTERN_SIZE
        val bottomLeft = x < FINDER_PATTERN_SIZE && y >= height - FINDER_PATTERN_SIZE
        return topLeft || topRight || bottomLeft
    }

    private fun drawFinderPatternCircleStyle(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        y: Int,
        diameter: Int,
    ) {
        val outer = diameter.toFloat()
        val whiteDiameter = outer * 5f / 7f
        val whiteOffset = outer / 7f
        val middleDiameter = outer * 3f / 7f
        val middleOffset = outer * 2f / 7f

        paint.color = Color.BLACK
        canvas.drawOval(RectF(x.toFloat(), y.toFloat(), x + outer, y + outer), paint)

        paint.color = Color.WHITE
        canvas.drawOval(
            RectF(
                x + whiteOffset,
                y + whiteOffset,
                x + whiteOffset + whiteDiameter,
                y + whiteOffset + whiteDiameter,
            ),
            paint,
        )

        paint.color = Color.BLACK
        canvas.drawOval(
            RectF(
                x + middleOffset,
                y + middleOffset,
                x + middleOffset + middleDiameter,
                y + middleOffset + middleDiameter,
            ),
            paint,
        )
    }
}
