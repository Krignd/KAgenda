package com.kstudio.agenda.model

import android.graphics.Color
import kotlin.math.abs

/** 课程配色（按课程号/名称散列到固定色板，保证同一门课颜色稳定） */
object CoursePalette {

    val base = intArrayOf(
        Color.parseColor("#3B82F6"), // 蓝
        Color.parseColor("#10B981"), // 绿
        Color.parseColor("#F59E0B"), // 橙
        Color.parseColor("#8B5CF6"), // 紫
        Color.parseColor("#EF4444"), // 红
        Color.parseColor("#06B6D4"), // 青
        Color.parseColor("#EC4899"), // 粉
        Color.parseColor("#84CC16"), // 黄绿
    )

    fun colorFor(key: String): Int {
        val h = abs(key.hashCode().let { if (it == Int.MIN_VALUE) 0 else it })
        return base[h % base.size]
    }

    fun colorFor(course: Course): Int = colorFor(course.code.ifBlank { course.title })

    /** 同色系的浅色填充 */
    fun lightOf(color: Int, alpha: Int = 46): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
