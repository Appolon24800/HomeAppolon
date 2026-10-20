package dev.appolon.homelab.arr.api.lidarr

import retrofit2.Retrofit

/** Calendar, quality and profile clients sharing the supplied server configuration. */
object LidarrCalendarApis {
    fun calendar(retrofit: Retrofit): CalendarApi = retrofit.create(CalendarApi::class.java)
    fun calendarFeed(retrofit: Retrofit): CalendarFeedApi =
        retrofit.create(CalendarFeedApi::class.java)
    fun qualityDefinition(retrofit: Retrofit): QualityDefinitionApi =
        retrofit.create(QualityDefinitionApi::class.java)
    fun qualityProfile(retrofit: Retrofit): QualityProfileApi =
        retrofit.create(QualityProfileApi::class.java)
    fun qualityProfileSchema(retrofit: Retrofit): QualityProfileSchemaApi =
        retrofit.create(QualityProfileSchemaApi::class.java)
    fun metadataProfile(retrofit: Retrofit): MetadataProfileApi =
        retrofit.create(MetadataProfileApi::class.java)
    fun metadataProfileSchema(retrofit: Retrofit): MetadataProfileSchemaApi =
        retrofit.create(MetadataProfileSchemaApi::class.java)
    fun metadataProviderConfig(retrofit: Retrofit): MetadataProviderConfigApi =
        retrofit.create(MetadataProviderConfigApi::class.java)
    fun releaseProfile(retrofit: Retrofit): ReleaseProfileApi =
        retrofit.create(ReleaseProfileApi::class.java)
    fun delayProfile(retrofit: Retrofit): DelayProfileApi =
        retrofit.create(DelayProfileApi::class.java)
    fun customFormat(retrofit: Retrofit): CustomFormatApi =
        retrofit.create(CustomFormatApi::class.java)
    fun language(retrofit: Retrofit): LanguageApi =
        retrofit.create(LanguageApi::class.java)
}
