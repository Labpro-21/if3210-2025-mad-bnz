package com.example.purrytify.utils
object CountryUtils {
    private val supportedCountries = mapOf(
        "ID" to "Indonesia",
        "MY" to "Malaysia",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CH" to "Switzerland",
        "DE" to "Germany",
        "BR" to "Brazil"
    )

    /**
     * Convert country code to full country name
     * @param code ISO 3166-1 alpha-2 country code
     * @return Full country name or null if country is not supported
     */
    fun getCountryName(code: String): String? {
        return supportedCountries[code.uppercase()]
    }

    /**
     * Get list of supported country codes
     * @return List of supported ISO 3166-1 alpha-2 country codes
     */
    fun getSupportedCountryCodes(): List<String> {
        return supportedCountries.keys.toList()
    }

    /**
     * Check if country code is supported
     * @param code ISO 3166-1 alpha-2 country code
     * @return true if country is supported, false otherwise
     */
    fun isCountrySupported(code: String): Boolean {
        return supportedCountries.containsKey(code.uppercase())
    }

    /**
     * Get all supported countries as pairs of code and name
     * @return List of Pairs where first is country code and second is country name
     */
    fun getAllCountries(): List<Pair<String, String>> {
        return supportedCountries.toList()
    }
}