package org.cygnusx1.nzbconnect.data.nzbget

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * NZBGet's JSON-RPC API. Every method is a POST to `…/jsonrpc` with a
 * `{"method":…, "params":[…]}` body and HTTP Basic auth. Because the `result` shape varies
 * per method, the raw [JsonObject] response is returned and decoded by [NzbgetRepository].
 */
interface NzbgetApi {
    @POST
    suspend fun rpc(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body body: JsonObject,
    ): Response<JsonObject>
}
