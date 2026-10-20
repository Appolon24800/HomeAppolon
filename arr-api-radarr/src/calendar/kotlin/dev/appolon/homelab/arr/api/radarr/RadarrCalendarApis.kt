package dev.appolon.homelab.arr.api.radarr

import retrofit2.Retrofit

/**
 * Builds the calendar and quality clients on a caller-supplied connection.
 * The caller owns the Retrofit instance so `:arr-data` keeps control of base
 * URL, API key header, proxy, and trust, per the architecture doc.
 */
object RadarrCalendarApis {
    fun calendar(retrofit: Retrofit): CalendarApi =
        retrofit.create(CalendarApi::class.java)

    fun calendarFeed(retrofit: Retrofit): CalendarFeedApi =
        retrofit.create(CalendarFeedApi::class.java)

    fun language(retrofit: Retrofit): LanguageApi =
        retrofit.create(LanguageApi::class.java)

    fun qualityDefinition(retrofit: Retrofit): QualityDefinitionApi =
        retrofit.create(QualityDefinitionApi::class.java)

    fun qualityProfile(retrofit: Retrofit): QualityProfileApi =
        retrofit.create(QualityProfileApi::class.java)

    fun qualityProfileSchema(retrofit: Retrofit): QualityProfileSchemaApi =
        retrofit.create(QualityProfileSchemaApi::class.java)

    fun releaseProfile(retrofit: Retrofit): ReleaseProfileApi =
        retrofit.create(ReleaseProfileApi::class.java)

    fun delayProfile(retrofit: Retrofit): DelayProfileApi =
        retrofit.create(DelayProfileApi::class.java)

    fun customFormat(retrofit: Retrofit): CustomFormatApi =
        retrofit.create(CustomFormatApi::class.java)
}
