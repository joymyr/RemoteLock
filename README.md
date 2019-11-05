# Remote Lock

Based on [Android-nRF-Toolbox](https://github.com/NordicSemiconductor/Android-nRF-Toolbox)
Modified to remote control Secuyou Bluetooth locks over PushBullet

* Create a free Pushbullet account. Go to "My Account" and create an access token
* Update app\src\main\java\no\nordicsemi\android\nrftoolbox\proximity\PushBulletConfig.kt with your access token
* Pair the device with your Secuyou lock from bluetooth settings
* Run the app on a spare Android phone
* Add the device within the app
* Modify app\src\main\java\no\nordicsemi\android\nrftoolbox\proximity\HttpTools.kt if needed. This is where the communication with pushbullet happens.

To make a request from another system:
* Call url: "https://api.pushbullet.com/v2/ephemerals"
* Headers:
** "Access-Token": "You access token"
** "Content-Type": "application/json"
* Body
** "{\"push\":{\"doorlock\":\"unlock\"},\"type\":\"push\"}" (to unlock the door)
** "{\"doorlock\":\"lock\"},\"type\":\"push\"}" (to lock the door)

I have successfully used this with the Android-app "HTTP Request shortcuts".


