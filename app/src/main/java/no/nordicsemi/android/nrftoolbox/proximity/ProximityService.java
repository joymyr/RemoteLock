/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package no.nordicsemi.android.nrftoolbox.proximity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.log.LogContract;
//import no.nordicsemi.android.nrftoolbox.FeaturesActivity;
import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.ToolboxApplication;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;
import no.nordicsemi.android.nrftoolbox.proximity.remote.RemoteClient;
import no.nordicsemi.android.nrftoolbox.utility.DebugLogger;

public class ProximityService extends BleMulticonnectProfileService implements ProximityManagerCallbacks, ProximityServerManagerCallbacks {
	@SuppressWarnings("unused")
	private static final String TAG = "ProximityService";

	private final static String ACTION_DISCONNECT = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_DISCONNECT";
	private final static String ACTION_FIND = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_FIND";
	private final static String ACTION_SILENT = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_SILENT";
	private final static String EXTRA_DEVICE = "no.nordicsemi.android.nrftoolbox.proximity.EXTRA_DEVICE";

	private final static String PROXIMITY_GROUP_ID = "proximity_connected_tags";
	private final static int NOTIFICATION_ID = 1000;
	private final static int OPEN_ACTIVITY_REQ = 0;
	private final static int DISCONNECT_REQ = 1;
	private final static int FIND_REQ = 2;
	private final static int SILENT_REQ = 3;

	private final ProximityBinder mBinder = new ProximityBinder();
	private ProximityServerManager mServerManager;
	private MediaPlayer mMediaPlayer;
	private int mOriginalVolume;
	/**
	 * When a device starts an alarm on the phone it is added to this list.
	 * Alarm is disabled when this list is empty.
	 */
	private List<BluetoothDevice> mDevicesWithAlarm;

	private int mAttempt;
	private final static int MAX_ATTEMPTS = 1;
	private RemoteClient remoteClient;

	/**
	 * This local binder is an interface for the bonded activity to operate with the proximity sensor
	 */
	public class ProximityBinder extends LocalBinder {
		/**
		 * Toggles the Immediate Alert on given remote device.
		 * @param device the connected device
		 * @return true if alarm has been enabled, false if disabled
		 */
		public boolean toggleImmediateAlert(final BluetoothDevice device) {
			final ProximityManager manager = (ProximityManager) getBleManager(device);
			return manager.toggleImmediateAlert();
		}

		public boolean toggleLock(BluetoothDevice device, boolean locked) {
			DebugLogger.d(TAG, "Toggle lock. Set locked: "+locked);
			final ProximityManager manager = (ProximityManager) getBleManager(device);
			return manager.writeImmediateAlert(locked);
		}

		public boolean isLocked(BluetoothDevice device) {
			final ProximityManager manager = (ProximityManager) getBleManager(device);
			return manager.isAlertEnabled();
		}

		public int getBattery(BluetoothDevice device) {
			final ProximityManager manager = (ProximityManager) getBleManager(device);
			return manager.getBatteryValue();
		}

		public ArrayList<BluetoothDevice> disconnectedDevices = new ArrayList<>();

		public boolean toggleConnection(BluetoothDevice device, boolean connect) {
			final ProximityManager manager = (ProximityManager) getBleManager(device);
			DebugLogger.d(TAG, "Toggle connection. Connect: "+connect);
			if (connect) {
				for (BluetoothDevice bl : BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
					if (device.getAddress().equals(bl.getAddress())) {
						getBinder().connect(bl, null);
					}
				}
				disconnectedDevices.remove(device);
			} else {
				disconnectedDevices.add(device);
				manager.disconnect();
			}
			return isConnected(device);
		}

		/**
		 * Returns the current alarm state on given device. This value is not read from the device, it's just the last value written to it
		 * (initially false).
		 * @param device the connected device
		 * @return true if alarm has been enabled, false if disabled
		 */
		public boolean isImmediateAlertOn(final BluetoothDevice device) {
			final ProximityManager manager = (ProximityManager) getBleManager(device);
			return manager.isAlertEnabled();
		}
	}

	@Override
	protected LocalBinder getBinder() {
		return mBinder;
	}

	@Override
	protected BleManager<ProximityManagerCallbacks> initializeManager() {
		return new ProximityManager(this, remoteClient);
	}

	/**
	 * This broadcast receiver listens for {@link #ACTION_DISCONNECT} that may be fired by pressing Disconnect action button on the notification.
	 */
	private final BroadcastReceiver mDisconnectActionBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
			mBinder.log(device, LogContract.Log.Level.INFO, "[Notification] DISCONNECT action pressed");
			mBinder.disconnect(device);
		}
	};

	/**
	 * This broadcast receiver listens for {@link #ACTION_FIND} or {@link #ACTION_SILENT} that may be fired by pressing Find me action button on the notification.
	 */
	private final BroadcastReceiver mToggleAlarmActionBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
			switch (intent.getAction()) {
				case ACTION_FIND:
					mBinder.log(device, LogContract.Log.Level.INFO, "[Notification] FIND action pressed");
					break;
				case ACTION_SILENT:
					mBinder.log(device, LogContract.Log.Level.INFO, "[Notification] SILENT action pressed");
					break;
			}
			mBinder.toggleImmediateAlert(device);
			createNotificationForConnectedDevice(device);
		}
	};

	@Override
	protected void onServiceCreated() {
		remoteClient = new RemoteClient(mBinder, getBaseContext());

		Intent notificationIntent = new Intent(this, ProximityActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		startForeground();

		mServerManager = new ProximityServerManager(this);
		mServerManager.setLogger(mBinder);

		initializeAlarm();

		registerReceiver(mDisconnectActionBroadcastReceiver, new IntentFilter(ACTION_DISCONNECT));
		final IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_FIND);
		filter.addAction(ACTION_SILENT);
		registerReceiver(mToggleAlarmActionBroadcastReceiver, filter);
	}

	private void startForeground(){
		String NOTIFICATION_CHANNEL_ID = "no.joymyr.remotelock";
		String channelName = "Remotelock listener service";
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
		}
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
		Notification notification = notificationBuilder.setOngoing(true)
				.setSmallIcon(R.drawable.ic_lock_open_black_24dp)
				.setContentTitle("Remotelock is listening for events")
				.setPriority(NotificationManager.IMPORTANCE_MIN)
				.setCategory(Notification.CATEGORY_SERVICE)
				.build();
		startForeground(2, notification);
	}

	@Override
	public void onServiceStopped() {
		cancelNotifications();

		// Close the GATT server. If it hasn't been opened this method does nothing
		mServerManager.closeGattServer();

		releaseAlarm();

		unregisterReceiver(mDisconnectActionBroadcastReceiver);
		unregisterReceiver(mToggleAlarmActionBroadcastReceiver);

		remoteClient.destroy();

		super.onServiceStopped();
	}

	@Override
	protected void onBluetoothEnabled() {
		mAttempt = 0;
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				final Runnable that = this;
				// Start the GATT Server only if Bluetooth is enabled
				mServerManager.openGattServer(ProximityService.this, new ProximityServerManager.OnServerOpenCallback() {
					@Override
					public void onGattServerOpen() {
						// We are now ready to reconnect devices
						ProximityService.super.onBluetoothEnabled();
					}

					@Override
					public void onGattServerFailed(final int error) {
						mServerManager.closeGattServer();

						if (mAttempt < MAX_ATTEMPTS) {
							mAttempt++;
							getHandler().postDelayed(that, 2000);
						} else {
							showToast(getString(R.string.proximity_server_error, error));
							// GATT server failed to start, but we may connect as a client
							ProximityService.super.onBluetoothEnabled();
						}
					}
				});
			}
		});
	}

	@Override
	protected void onBluetoothDisabled() {
		super.onBluetoothDisabled();
		// Close the GATT server
		mServerManager.closeGattServer();
	}

	@Override
	protected void onRebind() {
		// When the activity rebinds to the service, remove the notification
		cancelNotifications();
	}

	@Override
	public void onUnbind() {
		createBackgroundNotification();
	}

	@Override
	public void onDeviceConnected(final BluetoothDevice device) {
		super.onDeviceConnected(device);
		remoteClient.onInfo(device.getName().trim() + " is connected");

		if (!mBinded) {
			createBackgroundNotification();
		}
	}

	@Override
	public void onError(BluetoothDevice device, String message, int errorCode) {
		super.onError(device, message, errorCode);
		remoteClient.onError("Error code "+errorCode+" occured: "+message);
		Log.d(TAG, "Error code "+errorCode+" occured: "+message);
	}

	@Override
	public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
		super.onServicesDiscovered(device, optionalServicesFound);
		mServerManager.openConnection(device);
	}

	@Override
	public void onLinklossOccur(final BluetoothDevice device) {
		mServerManager.cancelConnection(device);
		stopAlarm(device);
		super.onLinklossOccur(device);
		remoteClient.onError(device.getName().trim() + " lost connection");

		if (!mBinded) {
			createBackgroundNotification();
			if (BluetoothAdapter.getDefaultAdapter().isEnabled())
				createLinklossNotification(device);
			else
				cancelNotification(device);
		}
	}

	@Override
	public void onDeviceDisconnected(final BluetoothDevice device) {
		mServerManager.cancelConnection(device);
		stopAlarm(device);
		super.onDeviceDisconnected(device);

		if (!mBinded) {
			cancelNotification(device);
			createBackgroundNotification();
		}
	}

	@Override
	public void onAlarmTriggered(final BluetoothDevice device) {
//		playAlarm(device);
		remoteClient.onError("Alarm triggered for " + device.getName().trim());
	}

	@Override
	public void onBatteryValueReceived(BluetoothDevice device, int value) {
		remoteClient.onInfo("Battery value for " + device.getName().trim() + " is " + value);
		super.onBatteryValueReceived(device, value);
	}

	@Override
	public void onAlarmStopped(final BluetoothDevice device) {
		stopAlarm(device);
	}

	private void createBackgroundNotification() {
		final List<BluetoothDevice> connectedDevices = getConnectedDevices();
		for (final BluetoothDevice device : connectedDevices) {
			createNotificationForConnectedDevice(device);
		}
		createSummaryNotification();
	}

	private void createSummaryNotification() {
		final NotificationCompat.Builder builder = getNotificationBuilder();
		builder.setColor(ContextCompat.getColor(this, R.color.actionBarColorDark));
		builder.setShowWhen(false).setDefaults(0).setOngoing(true); // an ongoing notification will not be shown on Android Wear
		builder.setGroup(PROXIMITY_GROUP_ID).setGroupSummary(true);
		builder.setContentTitle(getString(R.string.app_name));

		final List<BluetoothDevice> managedDevices = getManagedDevices();
		final List<BluetoothDevice> connectedDevices = getConnectedDevices();
		if (connectedDevices.isEmpty()) {
			// No connected devices
			final int numberOfManagedDevices = managedDevices.size();
			if (numberOfManagedDevices == 1) {
				final String name = getDeviceName(managedDevices.get(0));
				// We don't use plurals here, as we only have the default language and 'one' is not in every language (versions differ in %d or %s)
				// and throw an exception in e.g. in Chinese
				builder.setContentText(getString(R.string.proximity_notification_text_nothing_connected_one_disconnected, name));
			} else {
				builder.setContentText(getString(R.string.proximity_notification_text_nothing_connected_number_disconnected, numberOfManagedDevices));
			}
		} else {
			// There are some proximity tags connected
			final StringBuilder text = new StringBuilder();

			final int numberOfConnectedDevices = connectedDevices.size();
			if (numberOfConnectedDevices == 1) {
				final String name = getDeviceName(connectedDevices.get(0));
				text.append(getString(R.string.proximity_notification_summary_text_name, name));
			} else {
				text.append(getString(R.string.proximity_notification_summary_text_number, numberOfConnectedDevices));
			}

			// If there are some disconnected devices, also print them
			final int numberOfDisconnectedDevices = managedDevices.size() - numberOfConnectedDevices;
			if (numberOfDisconnectedDevices == 1) {
				text.append(", ");
				// Find the single disconnected device to get its name
				for (final BluetoothDevice device : managedDevices) {
					if (!isConnected(device)) {
						final String name = getDeviceName(device);
						text.append(getString(R.string.proximity_notification_text_nothing_connected_one_disconnected, name));
						break;
					}
				}
			} else if (numberOfDisconnectedDevices > 1) {
				text.append(", ");
				// If there are more, just write number of them
				text.append(getString(R.string.proximity_notification_text_nothing_connected_number_disconnected, numberOfDisconnectedDevices));
			}
			builder.setContentText(text);
		}

		final Notification notification = builder.build();
		final NotificationManagerCompat nm = NotificationManagerCompat.from(this);
		nm.notify(NOTIFICATION_ID, notification);
	}

	/**
	 * Creates the notification for given connected device.
	 * Adds 3 action buttons: DISCONNECT, FIND and SILENT which perform given action on the device.
	 */
	private void createNotificationForConnectedDevice(final BluetoothDevice device) {
		final NotificationCompat.Builder builder = getNotificationBuilder();
		builder.setColor(ContextCompat.getColor(this, R.color.actionBarColorDark));
		builder.setGroup(PROXIMITY_GROUP_ID).setDefaults(0).setOngoing(true); // an ongoing notification will not be shown on Android Wear
		builder.setContentTitle(getString(R.string.proximity_notification_text, getDeviceName(device)));

		// Add DISCONNECT action
		final Intent disconnect = new Intent(ACTION_DISCONNECT);
		disconnect.putExtra(EXTRA_DEVICE, device);
		final PendingIntent disconnectAction = PendingIntent.getBroadcast(this, DISCONNECT_REQ + device.hashCode(), disconnect, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_bluetooth, getString(R.string.proximity_action_disconnect), disconnectAction));
		builder.setSortKey(getDeviceName(device) + device.getAddress()); // This will keep the same order of notification even after an action was clicked on one of them

		// Add FIND or SILENT action
		final ProximityManager manager = (ProximityManager) getBleManager(device);
		if (manager.isAlertEnabled()) {
			final Intent silentAllIntent = new Intent(ACTION_SILENT);
			silentAllIntent.putExtra(EXTRA_DEVICE, device);
			final PendingIntent silentAction = PendingIntent.getBroadcast(this, SILENT_REQ + device.hashCode(), silentAllIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(new NotificationCompat.Action(R.drawable.ic_stat_notify_proximity_silent, getString(R.string.proximity_action_silent), silentAction));
		} else {
			final Intent findAllIntent = new Intent(ACTION_FIND);
			findAllIntent.putExtra(EXTRA_DEVICE, device);
			final PendingIntent findAction = PendingIntent.getBroadcast(this, FIND_REQ + device.hashCode(), findAllIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(new NotificationCompat.Action(R.drawable.ic_stat_notify_proximity_find, getString(R.string.proximity_action_find), findAction));
		}

		final Notification notification = builder.build();
		final NotificationManagerCompat nm = NotificationManagerCompat.from(this);
		nm.notify(device.getAddress(), NOTIFICATION_ID, notification);
	}

	/**
	 * Creates a notification showing information about a device that got disconnected.
	 */
	private void createLinklossNotification(final BluetoothDevice device) {
		final NotificationCompat.Builder builder = getNotificationBuilder();
		builder.setColor(ContextCompat.getColor(this, R.color.orange));

		final Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
		//builder.setSound(notificationUri, AudioManager.STREAM_ALARM); // make sure the sound is played even in DND mode
		builder.setPriority(NotificationCompat.PRIORITY_HIGH);
		builder.setCategory(NotificationCompat.CATEGORY_ERROR);
		builder.setShowWhen(true).setOngoing(false); // an ongoing notification would not be shown on Android Wear
		// This notification is to be shown not in a group

		final String name = getDeviceName(device);
		builder.setContentTitle(getString(R.string.proximity_notification_linkloss_alert, name));
		builder.setTicker(getString(R.string.proximity_notification_linkloss_alert, name));

		final Notification notification = builder.build();
		final NotificationManagerCompat nm = NotificationManagerCompat.from(this);
		nm.notify(device.getAddress(), NOTIFICATION_ID, notification);
	}

	private NotificationCompat.Builder getNotificationBuilder() {
		final Intent targetIntent = new Intent(this, ProximityActivity.class);

		// Both activities above have launchMode="singleTask" in the AndroidManifest.xml file, so if the task is already running, it will be resumed
		final PendingIntent pendingIntent = PendingIntent.getActivities(this, OPEN_ACTIVITY_REQ, new Intent[] { targetIntent }, PendingIntent.FLAG_UPDATE_CURRENT);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ToolboxApplication.PROXIMITY_WARNINGS_CHANNEL);
		builder.setContentIntent(pendingIntent).setAutoCancel(false);
		builder.setSmallIcon(R.drawable.ic_stat_notify_proximity);
		return builder;
	}

	/**
	 * Cancels the existing notification. If there is no active notification this method does nothing
	 */
	private void cancelNotifications() {
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(NOTIFICATION_ID);

		final List<BluetoothDevice> managedDevices = getManagedDevices();
		for (final BluetoothDevice device : managedDevices) {
			nm.cancel(device.getAddress(), NOTIFICATION_ID);
		}
	}

	/**
	 * Cancels the existing notification for given device. If there is no active notification this method does nothing
	 */
	private void cancelNotification(final BluetoothDevice device) {
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(device.getAddress(), NOTIFICATION_ID);
	}

	private void initializeAlarm() {
		mDevicesWithAlarm = new LinkedList<>();
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
		mMediaPlayer.setLooping(true);
		mMediaPlayer.setVolume(1.0f, 1.0f);
		try {
			mMediaPlayer.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
		} catch (final IOException e) {
			Log.e(TAG, "Initialize Alarm failed: ", e);
		}
	}

	private void releaseAlarm() {
		mMediaPlayer.release();
		mMediaPlayer = null;
	}

	private void playAlarm(final BluetoothDevice device) {
		final boolean alarmPlaying = !mDevicesWithAlarm.isEmpty();
		if (!mDevicesWithAlarm.contains(device))
			mDevicesWithAlarm.add(device);

		if (!alarmPlaying) {
			// Save the current alarm volume and set it to max
			final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			mOriginalVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
			am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
			try {
				mMediaPlayer.prepare();
				mMediaPlayer.start();
			} catch (final IOException e) {
				Log.e(TAG, "Prepare Alarm failed: ", e);
			}
		}
	}

	private void stopAlarm(final BluetoothDevice device) {
		mDevicesWithAlarm.remove(device);
		if (mDevicesWithAlarm.isEmpty() && mMediaPlayer.isPlaying()) {
			mMediaPlayer.stop();
			// Restore original volume
			final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			am.setStreamVolume(AudioManager.STREAM_ALARM, mOriginalVolume, 0);
		}
	}

	private String getDeviceName(final BluetoothDevice device) {
		String name = device.getName();
		if (TextUtils.isEmpty(name))
			name = getString(R.string.proximity_default_device_name);
		return name;
	}
}
