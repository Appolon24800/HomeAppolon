package dev.appolon.homelab.arr.api.sonarr

import retrofit2.Retrofit

/** Calendar, feed, quality, language and profile API factories. */
object SonarrCalendarApis {
    fun calendar(retrofit: Retrofit): CalendarApi =
        retrofit.create(CalendarApi::class.java)
    fun calendarFeed(retrofit: Retrofit): CalendarFeedApi =
        retrofit.create(CalendarFeedApi::class.java)
    fun qualityDefinition(retrofit: Retrofit): QualityDefinitionApi =
        retrofit.create(QualityDefinitionApi::class.java)
    fun qualityProfile(retrofit: Retrofit): QualityProfileApi =
        retrofit.create(QualityProfileApi::class.java)
    fun qualityProfileSchema(retrofit: Retrofit): QualityProfileSchemaApi =
        retrofit.create(QualityProfileSchemaApi::class.java)
    fun languageProfile(retrofit: Retrofit): LanguageProfileApi =
        retrofit.create(LanguageProfileApi::class.java)
    fun languageProfileSchema(retrofit: Retrofit): LanguageProfileSchemaApi =
        retrofit.create(LanguageProfileSchemaApi::class.java)
    fun releaseProfile(retrofit: Retrofit): ReleaseProfileApi =
        retrofit.create(ReleaseProfileApi::class.java)
    fun delayProfile(retrofit: Retrofit): DelayProfileApi =
        retrofit.create(DelayProfileApi::class.java)
    fun customFormat(retrofit: Retrofit): CustomFormatApi =
        retrofit.create(CustomFormatApi::class.java)
    fun language(retrofit: Retrofit): LanguageApi =
        retrofit.create(LanguageApi::class.java)
}
