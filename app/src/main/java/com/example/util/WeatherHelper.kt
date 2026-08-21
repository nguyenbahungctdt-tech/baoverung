package com.baoverung.app.util

import android.content.Context
import android.location.Geocoder
import java.util.*

object WeatherHelper {

    /**
     * Lấy thông tin thời tiết dựa trên thời gian và ngữ cảnh.
     * Trong thực tế có thể gọi API OpenWeatherMap.
     * Ở đây ta trả về thông tin mô phỏng chuyên nghiệp cho lâm nghiệp.
     */
    fun getWeatherAutoInfo(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val baseWeather = when (hour) {
            in 5..10 -> "Nắng nhẹ, sương mù rải rác buổi sớm. Nhiệt độ ~22-25°C."
            in 11..15 -> "Nắng gắt, tầm nhìn xa tốt. Nhiệt độ ~28-32°C. Cảnh báo cháy rừng cấp III."
            in 16..19 -> "Trời dịu, mây rải rác. Nhiệt độ ~24-26°C."
            else -> "Trời quang, lặng gió. Nhiệt độ ~18-21°C."
        }
        
        return "Thời tiết hệ thống: $baseWeather"
    }
}
