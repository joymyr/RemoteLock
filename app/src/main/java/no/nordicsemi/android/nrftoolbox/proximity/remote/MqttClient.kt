package no.nordicsemi.android.nrftoolbox.proximity.remote

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import no.nordicsemi.android.nrftoolbox.R
import no.nordicsemi.android.nrftoolbox.proximity.ProximityService
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttClient(private val context: Context, mBinder: ProximityService.ProximityBinder) {
    var mqttUri = context.resources.getString(R.string.mqtt_uri)
    var mqttLockCommandTopic = context.resources.getString(R.string.mqtt_lock_cmd_topic)
    var mqttLockEventTopic = context.resources.getString(R.string.mqtt_lock_evt_topic)
    var mqttAlarmEvtTopic = context.resources.getString(R.string.mqtt_alarm_evt_topic)
    var mqttUsername = context.resources.getString(R.string.mqtt_username)
    var mqttPassword = context.resources.getString(R.string.mqtt_password)

    private val client by lazy {
        val clientId = "AndroidRaspberryPi"
        MqttAndroidClient(context, mqttUri,
            clientId)
    }

    companion object {
        const val TAG = "MqttClient"
    }

    init {
        connect(topics = arrayOf(mqttLockCommandTopic), messageCallBack = { topic, message ->
            val locked = JsonParser().parse(message.toString()).asJsonObject["val"].asBoolean
            Log.d(TAG, "MQTT Set lock status: $locked")
            try {
                for (device in listOf(mBinder.managedDevices, mBinder.disconnectedDevices).flatten()) {
                    mBinder.toggleLock(device, locked)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })
    }

    private fun connect(topics: Array<String>? = null,
                messageCallBack: ((topic: String, message: MqttMessage) -> Unit)? = null) {
        try {
            val options = MqttConnectOptions()
            options.userName = mqttUsername
            options.password = mqttPassword.toCharArray()
            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    topics?.forEach {
                        subscribeTopic(it)
                    }
                    Log.d(TAG, "Connected to: $serverURI")
                }

                override fun connectionLost(cause: Throwable) {
                    Log.d(TAG, "The Connection was lost.")
                }

                @Throws(Exception::class)
                override fun messageArrived(topic: String, message: MqttMessage) {
                    Log.d(TAG, "Incoming message from $topic: " + message.toString())
                    messageCallBack?.invoke(topic, message)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {

                }
            })
            Log.d(TAG, "Connecting to: ${client.serverURI}...")
            val token = client.connect(options)
            token.actionCallback = object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection success")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failed")
                    exception?.printStackTrace()
                }

            }

        } catch (e: MqttException) {
            Log.d(TAG, "Failed to connect to: ${client.serverURI}")
            e.printStackTrace()
        }
    }

    fun onLockStateChanged(locked: Boolean) {
        publishMessage(
            mqttLockEventTopic, """{
                "serv": "door_lock",
                "type": "evt.lock.report",
                "val_t": "bool_map",
                "val": {
                    "door_is_closed": true,
                    "is_secured": $locked
                },
                "src": "lock"
            }""")
    }

    fun onInfo(message: String) {
        publishMessage(
            mqttAlarmEvtTopic, """{
            "serv": "alarm_lock",
            "type": "evt.alarm.report",
            "val_t": "str_map",
            "val": {
                "event": "$message",
                "status": "activ"
            },
            "src": "lock"
        }""") // TODO: Send correct events
    }

    fun onError(message: String) {
        publishMessage(
            mqttAlarmEvtTopic, """{
            "serv": "alarm_lock",
            "type": "evt.alarm.report",
            "val_t": "str_map",
            "val": {
                "event": "$message",
                "status": "activ"
            },
            "src": "lock"
        }""") // TODO: Send correct events
    }

    private fun publishMessage(topic: String, msg: String) {
        try {
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            client.publish(topic, message.payload, 0, true)
            Log.d(TAG, "$msg published to $topic")
        } catch (e: MqttException) {
            Log.d(TAG, "Error Publishing to $topic: " + e.message)
            e.printStackTrace()
        }

    }

    private fun subscribeTopic(topic: String, qos: Int = 0) {
        client.subscribe(topic, qos).actionCallback = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                Log.d(TAG, "Subscribed to $topic")
            }

            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                Log.d(TAG, "Failed to subscribe to $topic")
                exception.printStackTrace()
            }
        }
    }

    fun close() {
        client.apply {
            unregisterResources()
            close()
        }
    }
}