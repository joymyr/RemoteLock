package no.nordicsemi.android.nrftoolbox.proximity

import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import org.json.JSONException
import org.json.JSONObject

import java.io.IOException

import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import okio.ByteString
import java.util.*

class HttpTools(val mBinder: ProximityService.ProximityBinder) {

    companion object {
        private val MEDIATYPE_JSON = MediaType.parse("application/json; charset=utf-8")
        private const val TAG = "HttpTools"
    }

    private var okHttpClient: OkHttpClient
    private var lastMessage: Date? = null
    private var watchDog: Runnable
    private var socket: WebSocket? = null
    private var destroy = false

    init {
        val httpLoggingInterceptor = HttpLoggingInterceptor()
        httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        okHttpClient = OkHttpClient.Builder()
                .connectionPool(ConnectionPool())
                .addInterceptor(httpLoggingInterceptor)
                .build()

        watchDog = object: Runnable {
            override fun run() {
                if (destroy) return
                Log.d(TAG, "##### watchdog")
                lastMessage?.time?.let {
                    if (Date().time - it > 1000 * 60) {
                        Log.d(TAG, "##### No ticks for 1 minute")
                        pushLockState("No ticks after 1 minute. Restart")
                        socket?.cancel() ?: setupSocketListener()
                    }
                }
                Handler().postDelayed(this, 1000 * 30)
            }
        }
    }

    fun initSocketListener() {
        setupSocketListener()
        Handler().post(watchDog)
    }

    private fun setupSocketListener(retry: Int? = null) {
        if (destroy) return
        lastMessage = null
        var retry = retry

        val request = okhttp3.Request.Builder()
                .url("wss://stream.pushbullet.com/websocket/$PUSHBULLET_KEY").build()

        val pushBulletListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                lastMessage = Date()
                retry?.let {
                    pushLockState("Socket listener is up again")
                    retry = null
                } ?: pushLockState("Socket listener started")
                Log.d(TAG, "##### onOpen: " + response.body())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d(TAG, "##### Message: $text")
                lastMessage = Date()
                val state = JsonParser().parse(text).asJsonObject.getAsJsonObject("push")?.getAsJsonPrimitive("doorlock")
                state?.let {
                    try {
                        for (device in mBinder.managedDevices) {
                            mBinder.toggleLock(device, it.asString == "lock")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        pushLockState("Failed to toggle lock: $e")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                Log.d(TAG, "##### Bytemessage")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Log.d(TAG, "##### Closing")
                pushLockState("Socket listener closing")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.d(TAG, "##### Closed")
                pushLockState("Socket listener closed. Reason: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                if (destroy) return
                Log.d(TAG, "##### Failure")
                t.printStackTrace()
                if (retry == 1) {
                    pushLockState("Socket listener went down: " + t.message)
                }
                GlobalScope.launch {
                    retry?.let { delay(5000) }
                    setupSocketListener(retry?.plus(1) ?: 1)
                }
            }
        }
        GlobalScope.launch {
            socket = okHttpClient.newWebSocket(request, pushBulletListener)
        }
    }

    fun pushLockState(state: String) { pushLockState(state, null, Date()) }

    fun pushLockState(state: String, retry: Int?, date: Date) {
        val jsonObject = JSONObject()
        val dateStr = DateFormat.format("HH:mm:ss", date.time)
        var title = "Lock status [$dateStr]"
        retry?.let { title = "[$retry] $title" }
        try {
            jsonObject.put("body", state)
            jsonObject.put("title", title)
            jsonObject.put("type", "note")
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val body = RequestBody.create(MEDIATYPE_JSON, jsonObject.toString())
        val respRequest = okhttp3.Request.Builder()
                .url("https://api.pushbullet.com/v2/pushes")
                .addHeader("Access-Token", PUSHBULLET_KEY)
                .post(body)
                .build()
        GlobalScope.launch {
            okHttpClient.newCall(respRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "##### Resp failure", e)
                    e.printStackTrace()
                    GlobalScope.launch {
                        retry?.let { delay(5000) }
                        pushLockState(state, retry?.plus(1) ?: 1, date)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        Log.d(TAG, "##### Resp: " + response.body()?.string())
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    fun destroy() {
        pushLockState("Service was stopped")
        destroy = true
        socket?.cancel()
    }
}
