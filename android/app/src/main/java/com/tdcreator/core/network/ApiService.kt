package com.tdcreator.core.network

import com.tdcreator.core.network.dto.CreateJobRequest
import com.tdcreator.core.network.dto.JobResponse
import com.tdcreator.core.network.dto.LocalUploadResponse
import com.tdcreator.core.network.dto.PresignRequest
import com.tdcreator.core.network.dto.PresignResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * Retrofit service matching the 3DCreator backend contract.
 *
 * NOTE on base path: `backend/app/main.py` mounts routers at the root
 * (`/upload`, `/jobs`) — there is currently NO `/api/v1` prefix. Endpoints below are
 * therefore relative to `BASE_URL/`. If the backend later adds a version prefix, change the
 * @POST/@GET paths here only.
 */
interface ApiService {

    // ---- Upload (presigned PUT to R2) — production path ----
    @POST("upload/presign")
    suspend fun presignUpload(@Body req: PresignRequest): PresignResponse

    /** PUT raw bytes to a presigned URL obtained from [presignUpload]. No auth header needed. */
    @PUT
    suspend fun putToPresignedUrl(@Url url: String, @Body body: RequestBody)

    /** Dev/local upload: multipart POST to `upload/local` (no R2 needed). */
    @Multipart
    @POST("upload/local")
    suspend fun localUpload(@Part file: MultipartBody.Part): LocalUploadResponse

    // ---- Jobs ----
    @POST("jobs")
    suspend fun createJob(@Body req: CreateJobRequest): JobResponse

    @GET("jobs")
    suspend fun listJobs(): List<JobResponse>

    @GET("jobs/{id}")
    suspend fun getJob(@Path("id") id: String): JobResponse

    /**
     * Stream the generated 3D file (GLB/OBJ/...) directly.
     * Dev backend returns the file (FileResponse); prod redirects to a presigned R2 URL.
     * For the WebView viewer, just point it at `{BASE_URL}jobs/{id}/download`.
     */
    @GET("jobs/{id}/download")
    suspend fun downloadResult(@Path("id") id: String): ResponseBody
}
