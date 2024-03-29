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
package no.nordicsemi.android.nrftoolbox.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.widget.Toast;

public class BleProfileService extends Service implements BleManagerCallbacks {
	@SuppressWarnings("unused")
	private static final String TAG = "BleProfileService";

	public static final String BROADCAST_CONNECTION_STATE = "no.nordicsemi.android.nrftoolbox.BROADCAST_CONNECTION_STATE";
	public static final String BROADCAST_DEVICE_NOT_SUPPORTED = "no.nordicsemi.android.nrftoolbox.BROADCAST_DEVICE_NOT_SUPPORTED";
	public static final String BROADCAST_DEVICE_READY = "no.nordicsemi.android.nrftoolbox.DEVICE_READY";
	public static final String BROADCAST_BOND_STATE = "no.nordicsemi.android.nrftoolbox.BROADCAST_BOND_STATE";
	public static final String BROADCAST_BATTERY_LEVEL = "no.nordicsemi.android.nrftoolbox.BROADCAST_BATTERY_LEVEL";
	public static final String BROADCAST_ERROR = "no.nordicsemi.android.nrftoolbox.BROADCAST_ERROR";

	/** The parameter passed when creating the service. Must contain the address of the sensor that we want to connect to */
	public static final String EXTRA_DEVICE_ADDRESS = "no.nordicsemi.android.nrftoolbox.EXTRA_DEVICE_ADDRESS";
	/** The key for the device name that is returned in {@link #BROADCAST_CONNECTION_STATE} with state {@link #STATE_CONNECTED}. */
	public static final String EXTRA_DEVICE_NAME = "no.nordicsemi.android.nrftoolbox.EXTRA_DEVICE_NAME";
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.nrftoolbox.EXTRA_DEVICE";
	public static final String EXTRA_CONNECTION_STATE = "no.nordicsemi.android.nrftoolbox.EXTRA_CONNECTION_STATE";
	public static final String EXTRA_BOND_STATE = "no.nordicsemi.android.nrftoolbox.EXTRA_BOND_STATE";
	public static final String EXTRA_ERROR_MESSAGE = "no.nordicsemi.android.nrftoolbox.EXTRA_ERROR_MESSAGE";
	public static final String EXTRA_ERROR_CODE = "no.nordicsemi.android.nrftoolbox.EXTRA_ERROR_CODE";

	public static final int STATE_LINK_LOSS = -1;
	public static final int STATE_DISCONNECTED = 0;
	public static final int STATE_CONNECTED = 1;
	public static final int STATE_CONNECTING = 2;
	public static final int STATE_DISCONNECTING = 3;

	private BleManager mBleManager;
	private Handler mHandler;

	protected boolean mBinded;
	private boolean mConnected;
	private BluetoothDevice mBluetoothDevice;
	private String mDeviceName;

	private final BroadcastReceiver mBluetoothStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

			switch (state) {
				case BluetoothAdapter.STATE_ON:
					onBluetoothEnabled();
					break;
				case BluetoothAdapter.STATE_TURNING_OFF:
				case BluetoothAdapter.STATE_OFF:
					onBluetoothDisabled();
					break;
			}
		}
	};

	public class LocalBinder extends Binder {
		/**
		 * Disconnects from the sensor.
		 */
		public void disconnect() {
			if (!mConnected) {
				mBleManager.close();
				onDeviceDisconnected(mBluetoothDevice);
				return;
			}

			mBleManager.disconnect();
		}

		/**
		 * Returns the device address
		 *
		 * @return device address
		 */
		public final String getDeviceAddress() {
			return mBluetoothDevice.getAddress();
		}

		/**
		 * Returns the device name
		 *
		 * @return the device name
		 */
		public final String getDeviceName() {
			return mDeviceName;
		}

		/**
		 * Returns the Bluetooth device
		 *
		 * @return the Bluetooth device
		 */
		public final BluetoothDevice getBluetoothDevice() {
			return mBluetoothDevice;
		}

		/**
		 * Returns <code>true</code> if the device is connected to the sensor.
		 *
		 * @return <code>true</code> if device is connected to the sensor, <code>false</code> otherwise
		 */
		public final boolean isConnected() {
			return mConnected;
		}

		/**
		 * Returns the Profile API. Profile may be null if service discovery has not been performed or the device does not match any profile.
		 */
		public final BleProfile getProfile() {
			return mBleManager.getProfile();
		}
	}

	/**
	 * Returns the binder implementation. This must return class implementing the additional manager interface that may be used in the bound activity.
	 *
	 * @return the service binder
	 */
	protected LocalBinder getBinder() {
		// default implementation returns the basic binder. You can overwrite the LocalBinder with your own, wider implementation
		return new LocalBinder();
	}

	@Override
	public IBinder onBind(final Intent intent) {
		mBinded = true;
		return getBinder();
	}

	@Override
	public final void onRebind(final Intent intent) {
		mBinded = true;
	}

	@Override
	public final boolean onUnbind(final Intent intent) {
		mBinded = false;

		// We want the onRebind method be called if anything else binds to it again
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate() {
		super.onCreate();

		mHandler = new Handler();

		// initialize the manager
		mBleManager = new BleManager(this, this);

		// Register broadcast receivers
		registerReceiver(mBluetoothStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

		// Service has now been created
		onServiceCreated();

		// Call onBluetoothEnabled if Bluetooth enabled
		final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter.isEnabled()) {
			onBluetoothEnabled();
		}
	}

	/**
	 * Called when the service has been created, before the {@link #onBluetoothEnabled()} is called.
	 */
	protected void onServiceCreated() {
		// empty default implementation
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		if (intent == null || !intent.hasExtra(EXTRA_DEVICE_ADDRESS))
			throw new UnsupportedOperationException("No device address at EXTRA_DEVICE_ADDRESS key");

		mDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);

		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		final BluetoothAdapter adapter = bluetoothManager.getAdapter();
		final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
		mBluetoothDevice = adapter.getRemoteDevice(deviceAddress);
		onServiceStarted();

		mBleManager.connect(mBluetoothDevice);
		return START_REDELIVER_INTENT;
	}

	/**
	 * Called when the service has been started. The device name and address are set. It nRF Logger is installed than logger was also initialized.
	 */
	protected void onServiceStarted() {
		// empty default implementation
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Unregister broadcast receivers
		unregisterReceiver(mBluetoothStateBroadcastReceiver);

		// shutdown the manager
		mBleManager.close();
		mBleManager = null;
		mBluetoothDevice = null;
		mDeviceName = null;
		mConnected = false;
	}

	/**
	 * Method called when Bluetooth Adapter has been disabled.
	 */
	protected void onBluetoothDisabled() {
		// empty default implementation
	}

	/**
	 * This method is called when Bluetooth Adapter has been enabled and
	 * after the service was created if Bluetooth Adapter was enabled at that moment.
	 * This method could initialize all Bluetooth related features, for example open the GATT server.
	 */
	protected void onBluetoothEnabled() {
		// empty default implementation
	}

	@Override
	public boolean shouldEnableBatteryLevelNotifications(final BluetoothDevice device) {
		// By default the Battery Level notifications will be enabled only the activity is bound.
		return mBinded;
	}

	@Override
	public void onDeviceConnecting(final BluetoothDevice device) {
		final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTING);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	@Override
	public void onDeviceConnected(final BluetoothDevice device) {
		mConnected = true;

		final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
		broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTED);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		broadcast.putExtra(EXTRA_DEVICE_NAME, mDeviceName);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	@Override
	public void onDeviceDisconnecting(final BluetoothDevice device) {
		// Notify user about changing the state to DISCONNECTING
		final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTING);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	@Override
	public void onDeviceDisconnected(final BluetoothDevice device) {
		mConnected = false;

		final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
		broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTED);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

		stopSelf();
	}

	@Override
	public void onLinklossOccur(final BluetoothDevice device) {
		mConnected = false;

		final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_LINK_LOSS);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	@Override
	public void onDeviceReady(final BluetoothDevice device) {
		final Intent broadcast = new Intent(BROADCAST_DEVICE_READY);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	@Override
	public void onDeviceNotSupported(final BluetoothDevice device) {
		final Intent broadcast = new Intent(BROADCAST_DEVICE_NOT_SUPPORTED);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

		// no need for disconnecting, it will be disconnected by the manager automatically
	}

	@Override
	public void onBondingRequired(final BluetoothDevice device) {
		showToast(no.nordicsemi.android.nrftoolbox.common.R.string.bonding);

		final Intent broadcast = new Intent(BROADCAST_BOND_STATE);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		broadcast.putExtra(EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDING);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	@Override
	public void onBonded(final BluetoothDevice device) {
		showToast(no.nordicsemi.android.nrftoolbox.common.R.string.bonded);

		final Intent broadcast = new Intent(BROADCAST_BOND_STATE);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		broadcast.putExtra(EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	@Override
	public void onError(final BluetoothDevice device, final String message, final int errorCode) {
		final Intent broadcast = new Intent(BROADCAST_ERROR);
		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		broadcast.putExtra(EXTRA_ERROR_MESSAGE, message);
		broadcast.putExtra(EXTRA_ERROR_CODE, errorCode);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

		// After receiving an error the device will be automatically disconnected.
		// Replace it with other implementation if necessary.
		mBleManager.disconnect();
		stopSelf();
	}

	/**
	 * Shows a message as a Toast notification. This method is thread safe, you can call it from any thread
	 *
	 * @param messageResId
	 *            an resource id of the message to be shown
	 */
	private void showToast(final int messageResId) {
		mHandler.post(() -> Toast.makeText(BleProfileService.this, messageResId, Toast.LENGTH_SHORT).show());
	}

	/**
	 * Creates an intent filter that filters for all broadcast events sent by this service.
	 */
	public static IntentFilter makeIntentFilter() {
		final IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_CONNECTION_STATE);
		filter.addAction(BROADCAST_BOND_STATE);
		filter.addAction(BROADCAST_DEVICE_READY);
		filter.addAction(BROADCAST_DEVICE_NOT_SUPPORTED);
		filter.addAction(BROADCAST_ERROR);
		return filter;
	}
}
