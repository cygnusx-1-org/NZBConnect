package org.cygnusx1.nzbconnect.data.sab

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * SABnzbd's HTTP API. Base URL is dynamic (user-configured), so calls use [@Url] with
 * the absolute `…/api` endpoint. SAB returns clean, stable JSON so we deserialize it.
 */
interface SabnzbdApi {
    @GET
    suspend fun version(@Url url: String, @QueryMap params: Map<String, String>): Response<VersionResponse>

    @GET
    suspend fun queue(@Url url: String, @QueryMap params: Map<String, String>): Response<QueueResponse>

    @GET
    suspend fun history(@Url url: String, @QueryMap params: Map<String, String>): Response<HistoryResponse>

    @GET
    suspend fun categories(@Url url: String, @QueryMap params: Map<String, String>): Response<CategoriesResponse>

    @GET
    suspend fun addUrl(@Url url: String, @QueryMap params: Map<String, String>): Response<AddUrlResponse>

    @GET
    suspend fun status(@Url url: String, @QueryMap params: Map<String, String>): Response<StatusResponse>

    @GET
    suspend fun serverStats(@Url url: String, @QueryMap params: Map<String, String>): Response<ServerStatsResponse>

    @GET
    suspend fun warnings(@Url url: String, @QueryMap params: Map<String, String>): Response<WarningsResponse>

    @GET
    suspend fun simple(@Url url: String, @QueryMap params: Map<String, String>): Response<SimpleStatusResponse>

    @GET
    suspend fun switch(@Url url: String, @QueryMap params: Map<String, String>): Response<SwitchResponse>
}
