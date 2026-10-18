package com.hfmelt.virtualgps

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "خط العرض يجب أن يكون بين ‎-90 و90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "خط الطول يجب أن يكون بين ‎-180 و180" }
    }
    companion object {
        fun parse(latitude: String, longitude: String): Coordinates = Coordinates(
            normalize(latitude).toDoubleOrNull() ?: throw IllegalArgumentException("خط العرض غير صالح"),
            normalize(longitude).toDoubleOrNull() ?: throw IllegalArgumentException("خط الطول غير صالح")
        )
        private fun normalize(value: String): String = buildString {
            for (c in value.trim()) append(when {
                c in '٠'..'٩' -> '0' + (c - '٠')
                c in '۰'..'۹' -> '0' + (c - '۰')
                c == '٫' || c == ',' -> '.'
                c == '−' -> '-'
                else -> c
            })
        }
    }
}
