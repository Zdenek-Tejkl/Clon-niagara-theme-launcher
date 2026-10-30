package cz.tejkl.klid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Vykreslí ikonu do jednotného tvaru (superelipsa), aby všechny ikony
 * působily jako jedna sada bez ohledu na výrobce a styl aplikace.
 */
object IconRenderer {
    private const val EXPONENT = 3.6
    private const val SAMPLES = 96

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(34, 255, 255, 255)
    }
    private val legacyBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 34, 38) }

    fun render(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shape = squircle(size.toFloat())
        canvas.save()
        canvas.clipPath(shape)
        if (drawable is AdaptiveIconDrawable) {
            // Vrstvy mají 108 jednotek, viditelný je střed 72: přesahují o čtvrtinu na každou stranu.
            val bleed = size / 4
            drawable.background?.apply { setBounds(-bleed, -bleed, size + bleed, size + bleed); draw(canvas) }
            drawable.foreground?.apply { setBounds(-bleed, -bleed, size + bleed, size + bleed); draw(canvas) }
        } else {
            // Starší ikony bez vrstev položíme zmenšené na tmavý podklad.
            canvas.drawPath(shape, legacyBackground)
            val inset = (size * 0.16f).toInt()
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.draw(canvas)
        }
        canvas.restore()
        // Jemný světlý lem dodá ikonám "skleněnou" hranu.
        edgePaint.strokeWidth = size / 48f
        canvas.drawPath(shape, edgePaint)
        return bitmap
    }

    private fun squircle(size: Float): Path {
        val r = size / 2f
        val path = Path()
        for (i in 0..SAMPLES) {
            val t = 2 * Math.PI * i / SAMPLES
            val c = cos(t)
            val s = sin(t)
            val x = r + r * sign(c) * abs(c).pow(2 / EXPONENT)
            val y = r + r * sign(s) * abs(s).pow(2 / EXPONENT)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }
}
