package com.example.scansense10

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var results: List<DetectionResult> = listOf()

    private val boundingBoxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
    }

    fun setResults(results: List<DetectionResult>) {
        this.results = results
        invalidate() // Redesenha a tela com os novos resultados
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (result in results) {
            // Desenha o retângulo ao redor do objeto detectado
            canvas.drawRect(result.rect, boundingBoxPaint)

            // Prepara o texto com o rótulo e a confiança
            val label = "${result.label} (${String.format("%.2f", result.confidence)})"

            // Desenha o texto acima do retângulo
            canvas.drawText(label, result.rect.left, result.rect.top - 10, textPaint)
        }
    }
}