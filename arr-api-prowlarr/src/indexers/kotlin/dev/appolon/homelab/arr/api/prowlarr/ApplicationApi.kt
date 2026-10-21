package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Application tag: CRUD on connected *arr applications, bulk edits, schemas
 * and test routes. PUT path ids are strings because the spec declares that
 * type on update routes. Body-less calls return [Response] because the status
 * code is the whole result. Bulk delete uses [HTTP] because Retrofit otherwise
 * rejects a body on DELETE even though the spec documents one.
 */
@JvmSuppressWildcards
interface ApplicationApi {
    @GET("api/v1/applications")
    suspend fun getApplications(): List<ApplicationResource>

    @GET("api/v1/applications/{id}")
    suspend fun getApplication(@Path("id") id: Int): ApplicationResource

    @GET("api/v1/applications/schema")
    suspend fun getApplicationSchemas(): List<ApplicationResource>

    @POST("api/v1/applications")
    suspend fun createApplication(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: ApplicationResource,
    ): ApplicationResource

    @PUT("api/v1/applications/{id}")
    suspend fun updateApplication(
        @Path("id") id: String,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: ApplicationResource,
    ): ApplicationResource

    @PUT("api/v1/applications/bulk")
    suspend fun updateApplications(
        @Body resource: ApplicationBulkResource,
    ): ApplicationResource

    @DELETE("api/v1/applications/{id}")
    suspend fun deleteApplication(@Path("id") id: Int): Response<Unit>

    @HTTP(method = "DELETE", path = "api/v1/applications/bulk", hasBody = true)
    suspend fun deleteApplications(@Body resource: ApplicationBulkResource): Response<Unit>

    @POST("api/v1/applications/test")
    suspend fun testApplication(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: ApplicationResource,
    ): Response<Unit>

    @POST("api/v1/applications/testall")
    suspend fun testAllApplications(): Response<Unit>

    @POST("api/v1/applications/action/{name}")
    suspend fun applicationAction(
        @Path("name") name: String,
        @Body resource: ApplicationResource,
    ): Response<Unit>
}
