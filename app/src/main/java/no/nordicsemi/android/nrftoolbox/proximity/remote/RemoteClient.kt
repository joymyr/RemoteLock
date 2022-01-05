package no.nordicsemi.android.nrftoolbox.proximity.remote

import android.content.Context
import no.nordicsemi.android.nrftoolbox.proximity.ProximityService

class RemoteClient(
    mBinder: ProximityService.ProximityBinder,
    context: Context) {

    var mqttClient: MqttClient = MqttClient(context, mBinder)
    var pushbulletClient: PushbulletClient = PushbulletClient(mBinder, context)

    init {
        pushbulletClient.initSocketListener()
    }

    fun onLockStateChanged(id: String, isLocked: Boolean) {
        mqttClient.onLockStateChanged(isLocked)
        pushbulletClient.onLockStateChanged(id, isLocked)
    }

    fun onInfo(message: String) {
        mqttClient.onInfo(message)
        pushbulletClient.onInfo(message)
    }

    fun onError(message: String) {
        mqttClient.onError(message)
        pushbulletClient.onError(message)
    }

    fun destroy() {
        pushbulletClient.destroy()
        mqttClient.close()
    }
}
