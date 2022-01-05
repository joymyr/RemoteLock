# Remote Lock

Update: Support lock/unlock over MQTT, see how to configure in Futurehome: https://forum.futurehome.io/t/virtuell-dorlas/2546/3
Based on [Android-nRF-Toolbox](https://github.com/NordicSemiconductor/Android-nRF-Toolbox)
Modified to remote control Secuyou Bluetooth locks over PushBullet, using a spare Android phone or a Raspberry PI

* Create a free Pushbullet account. Go to "My Account" and create an access token
* Add configuretion to local.properties:
  ```
  pushbulletKey="<your key>"
  mqttUri="tcp://xxx.xxx.x.xxx:1884"
  mqttUsername="<mqtt username>"
  mqttPassword="<mqtt password>"
  mqttLockTopic="<Topic for the door lock>"
  ```
* Install Android on a Raspberry PI or find a spare Android phone that you can leave within reach of your lock, and always connected to power
* Pair the device with your Secuyou lock from bluetooth settings
* Run this app on the PI/phone
* Add the device within the app
* Modify app\src\main\java\no\nordicsemi\android\nrftoolbox\remote\*.kt if needed. This is where the remote communication happens.

To make a request from another system using pushbullet:
* Call url: "https://api.pushbullet.com/v2/ephemerals"
* Headers:
  * "Access-Token": "Your access token"
  * "Content-Type": "application/json"
* Body
  * "{\"push\":{\"doorlock\":\"unlock\"},\"type\":\"push\"}" (to unlock the door)
  * "{\"doorlock\":\"lock\"},\"type\":\"push\"}" (to lock the door)
I have successfully used this with the Android-app "HTTP Request shortcuts".


To integrate with another system using MQTT (Tested with Futurehome):
* Post to topic:
```
{
"serv": "door_lock",
"type": "cmd.lock.set",
"val_t": "bool",
"val": <true or false>,
"src": "<anything other than lock>"
}
```
* Handle MQTT messages sent to topic in this format:
```
{
"serv": "door_lock",
"type": "cmd.lock.set",
"val_t": "bool",
"val": <true or false>,
"src": "lock"
}
```

