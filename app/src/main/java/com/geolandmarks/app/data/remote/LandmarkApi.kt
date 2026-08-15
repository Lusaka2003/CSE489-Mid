package com.geolandmarks.app.data.remote

import com.google.gson.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface LandmarkApi {

    @GET("api.php")
    suspend fun getLandmarks(
        @Query("action") action: String = "get_landmarks",
        @Query("key") key: String
    ): Response<JsonElement>

    @POST("api.php")
    suspend fun visitLandmark(
        @Query("action") action: String = "visit_landmark",
        @Query("key") key: String,
        @Body body: VisitRequest
    ): Response<JsonElement>

    @GET("api.php")
    suspend fun getJobStatus(
        @Query("action") action: String = "get_job_status",
        @Query("key") key: String,
        @Query("job_id") jobId: Long
    ): Response<JsonElement>

    @Multipart
    @POST("api.php")
    suspend fun createLandmark(
        @Query("action") action: String = "create_landmark",
        @Query("key") key: String,
        @Part("title") title: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lon") lon: RequestBody,
        @Part image: MultipartBody.Part?
    ): Response<JsonElement>

    @FormUrlEncoded
    @POST("api.php")
    suspend fun deleteLandmark(
        @Query("action") action: String = "delete_landmark",
        @Query("key") key: String,
        @Field("landmark_id") landmarkId: Int,
        @Field("id") id: Int
    ): Response<JsonElement>

    @FormUrlEncoded
    @POST("api.php")
    suspend fun restoreLandmark(
        @Query("action") action: String = "restore_landmark",
        @Query("key") key: String,
        @Field("landmark_id") landmarkId: Int,
        @Field("id") id: Int
    ): Response<JsonElement>
}

data class VisitRequest(
    val landmark_id: Int,
    val user_lat: Double,
    val user_lon: Double
)
