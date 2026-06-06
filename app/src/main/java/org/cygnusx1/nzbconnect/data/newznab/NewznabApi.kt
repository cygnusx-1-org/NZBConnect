package org.cygnusx1.nzbconnect.data.newznab

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Newznab is served per-host with a dynamic base URL, so every call uses an absolute
 * [@Url]. Responses are RSS/XML (uniform across implementations), returned raw and
 * parsed by [NewznabParser] rather than relying on each server's inconsistent JSON.
 */
interface NewznabApi {
    @GET
    suspend fun get(
        @Url url: String,
        @QueryMap params: Map<String, String>,
    ): Response<ResponseBody>
}
