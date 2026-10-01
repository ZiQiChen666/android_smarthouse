package com.example.smarthouse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarthouse.ui.Sample

/**
 * 实时曲线卡片：展示某个测量值最近一段时间的变化。
 */
@Composable
fun LineChartCard(
    label: String,
    unit: String,
    samples: List<Sample>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val current = samples.lastOrNull()?.value
    val minV = samples.minOfOrNull { it.value }
    val maxV = samples.maxOfOrNull { it.value }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = current?.let { formatValue(it) + " " + unit } ?: "--",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            if (samples.size < 2) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "正在采集数据…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ChartCanvas(
                    samples = samples,
                    accent = accent,
                    gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "最低 ${minV?.let { formatValue(it) } ?: "--"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "采样 ${samples.size} 点 · 每秒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "最高 ${maxV?.let { formatValue(it) } ?: "--"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChartCanvas(
    samples: List<Sample>,
    accent: Color,
    gridColor: Color,
    labelColor: Color
) {
    val values = samples.map { it.value }
    val rawMin = values.min()
    val rawMax = values.max()
    // 给上下留 10% 余量；若全部相等则人为撑开一点范围
    val range = (rawMax - rawMin).takeIf { it > 1e-6 } ?: 1.0
    val minV = rawMin - range * 0.15
    val maxV = rawMax + range * 0.15

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val w = size.width
        val h = size.height
        val leftPad = 0f
        val rightPad = 0f
        val plotW = w - leftPad - rightPad

        // 网格线
        val rows = 3
        for (i in 0..rows) {
            val y = h * i / rows
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        fun pointX(index: Int): Float =
            if (samples.size <= 1) leftPad
            else leftPad + plotW * index / (samples.size - 1).toFloat()

        fun pointY(v: Double): Float {
            val ratio = ((v - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f)
            return h - ratio * h
        }

        // 渐变填充
        val fillPath = Path().apply {
            moveTo(pointX(0), h)
            samples.forEachIndexed { i, s -> lineTo(pointX(i), pointY(s.value)) }
            lineTo(pointX(samples.size - 1), h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.02f))
            )
        )

        // 折线
        val linePath = Path().apply {
            samples.forEachIndexed { i, s ->
                val x = pointX(i)
                val y = pointY(s.value)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = linePath,
            color = accent,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        // 最新点高亮
        val lastX = pointX(samples.size - 1)
        val lastY = pointY(samples.last().value)
        drawCircle(color = accent.copy(alpha = 0.25f), radius = 9f, center = Offset(lastX, lastY))
        drawCircle(color = accent, radius = 4.5f, center = Offset(lastX, lastY))

        // 纵轴最大/最小值标注
        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            formatValue(rawMax), 4f, 12.sp.toPx(), paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            formatValue(rawMin), 4f, h - 4f, paint
        )
    }
}

private fun formatValue(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
