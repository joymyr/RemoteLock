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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.Request;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.nrftoolbox.parser.AlertLevelParser;
import no.nordicsemi.android.nrftoolbox.utility.DebugLogger;
import no.nordicsemi.android.nrftoolbox.utility.ParserUtils;

public class ProximityManager extends BleManager<ProximityManagerCallbacks> {
	private final String TAG = "ProximityManager";

	/** Immediate Alert service UUID */
	public final static UUID IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
	/** Link Loss service UUID */
	public final static UUID LINKLOSS_SERVICE_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");
	/** Alert Level characteristic UUID */
	private static final UUID ALERT_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A06-0000-1000-8000-00805f9b34fb");

	private static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private static final UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
	private static final UUID LOCK_CHARACTERISTIC = UUID.fromString("B1DE1524-85EF-37CC-00C8-A3CF3412A548");
	private static final UUID LOCK_CMD_CHARACTERISTIC = UUID.fromString("B1DE1525-85EF-37CC-00C8-A3CF3412A548");
	private static final UUID LOCK_NAME_CHARACTERISTIC = UUID.fromString("B1DE1526-85EF-37CC-00C8-A3CF3412A548");
	public static final UUID LOCK_SERVICE = UUID.fromString("B1DE1523-85EF-37CC-00C8-A3CF3412A548");

	private static final byte[] UNLOCK = BleLockCommandBuilder.buildCommand(BleLockCommandBuilder.BLE_LOCK_CMD.CMD_PARAM_DRIVE_LOCK_TO_UNLOCKED);
	private static final byte[] LOCK = BleLockCommandBuilder.buildCommand(BleLockCommandBuilder.BLE_LOCK_CMD.CMD_PARAM_DRIVE_LOCK_TO_LOCKED);

	private final static byte[] HIGH_ALERT = { 0x02 };
	private final static byte[] MILD_ALERT = { 0x01 };
	private final static byte[] NO_ALERT = { 0x00 };
	private final HttpTools httpTools;

	private BluetoothGattCharacteristic mAlertLevelCharacteristic, mLinklossCharacteristic;
	private boolean mAlertOn;

	public ProximityManager(final Context context, HttpTools httpTools) {
		super(context);
		this.httpTools = httpTools;
	}

	@Override
	protected boolean shouldAutoConnect() {
		return true;
	}

	@Override
	protected BleManagerGattCallback getGattCallback() {
		return mGattCallback;
	}

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving indication, etc
	 */
	private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

		@Override
		protected Deque<Request> initGatt(final BluetoothGatt gatt) {
			final LinkedList<Request> requests = new LinkedList<>();
			requests.add(Request.newWriteRequest(mLinklossCharacteristic, HIGH_ALERT));
			internalSetLockstatusNotifications(gatt, true);
			return requests;
		}

		private boolean internalSetLockstatusNotifications(BluetoothGatt gatt, boolean enable) {
			if (gatt == null) {
				return false;
			}
			BluetoothGattService LockService = gatt.getService(LOCK_SERVICE);
			if (LockService == null) {
				return false;
			}
			BluetoothGattCharacteristic LockCharacteristic = LockService.getCharacteristic(LOCK_CHARACTERISTIC);
			if (LockCharacteristic == null || (LockCharacteristic.getProperties() & 16) == 0) {
				return false;
			}
			gatt.setCharacteristicNotification(LockCharacteristic, enable);
			BluetoothGattDescriptor descriptor = LockCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
			if (descriptor == null) {
				return false;
			}
			if (enable) {
				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			} else {
				descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
			}
			return gatt.writeDescriptor(descriptor);
		}

		@Override
		protected boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
			final BluetoothGattService llService = gatt.getService(ProximityManager.LOCK_SERVICE);
			if (llService != null) {
				mLinklossCharacteristic = llService.getCharacteristic(ProximityManager.LOCK_CHARACTERISTIC);
				ProximityManager.this.mLockCharacteristic = llService.getCharacteristic(ProximityManager.LOCK_CHARACTERISTIC);
				ProximityManager.this.mLockCMDCharacteristic = llService.getCharacteristic(ProximityManager.LOCK_CMD_CHARACTERISTIC);
			}
			return mLinklossCharacteristic != null;
		}

		@Override
		protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
			final BluetoothGattService iaService = gatt.getService(IMMEDIATE_ALERT_SERVICE_UUID);
			if (iaService != null) {
				mAlertLevelCharacteristic = iaService.getCharacteristic(ALERT_LEVEL_CHARACTERISTIC_UUID);
			}
			return mAlertLevelCharacteristic != null;
		}

		@Override
		protected void onDeviceDisconnected() {
			mAlertLevelCharacteristic = null;
			mLinklossCharacteristic = null;
			// Reset the alert flag
			mAlertOn = false;
		}

		@Override
		protected void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			Logger.a(mLogSession, "\"" + AlertLevelParser.parse(characteristic) + "\" sent");
		}

		@Override
		protected void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicRead(gatt, characteristic);
			onCharacteristicIndicated(gatt, characteristic);
		}

		@Override
		protected void onCharacteristicNotified(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicNotified(gatt, characteristic);
			onCharacteristicIndicated(gatt, characteristic);
		}

		@Override
		protected void onCharacteristicIndicated(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicIndicated(gatt, characteristic);
			String data = ParserUtils.parse(characteristic);
			if (isLockstatusCharacteristic(characteristic)) {
				Logger.a(mLogSession, "Notification received from " + characteristic.getUuid() + ", value: " + data);
				byte[] lockstatusValue = characteristic.getValue();
				Logger.a(mLogSession, "Lockstatus received: " + lockstatusValue + "%");

				if (BleLockCodes.isNewLockState(lockstatusValue)) {
					boolean locked = BleLockCodes.isDoorLocked(lockstatusValue);
					httpTools.pushLockState(gatt.getDevice().getName().trim()+" is "+(locked ? "Locked" : "Unlocked"));
				} else if (BleLockCodes.isFailedLockState(lockstatusValue)) {
					httpTools.pushLockState(gatt.getDevice().getName().trim()+" failed to lock");
				}
			} else if (isBatteryLevelCharacteristic(characteristic)) {
				Logger.a(mLogSession, "Notification received from " + characteristic.getUuid() + ", value: " + data);
				int batteryValue = characteristic.getIntValue(17, 0);
				Logger.a(mLogSession, "Battery level received: " + batteryValue + "%");
				httpTools.pushLockState("Battery level of "+gatt.getDevice().getName()+" is "+batteryValue);
			}
		}
	};

	private boolean mLOCKED;
	private BluetoothGattCharacteristic mLockCMDCharacteristic;
	private BluetoothGattCharacteristic mLockCharacteristic;

	private boolean isBatteryLevelCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (characteristic == null) {
			return false;
		}
		return BATTERY_LEVEL_CHARACTERISTIC.equals(characteristic.getUuid());
	}

	private boolean isLockstatusCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (characteristic == null) {
			return false;
		}
		return LOCK_CHARACTERISTIC.equals(characteristic.getUuid());
	}

	/**
	 * Toggles the immediate alert on the target device.
	 * @return true if alarm has been enabled, false if disabled
	 */
	public boolean toggleImmediateAlert() {
		writeImmediateAlert(!mAlertOn);
		return mAlertOn;
	}

	/**
	 * Writes the HIGH ALERT or NO ALERT command to the target device
	 * @param on true to enable the alarm on proximity tag, false to disable it
	 */
	public void writeImmediateAlert(final boolean on) {
		if (!isConnected()) {
			httpTools.pushLockState(mBluetoothDevice.getName().trim()+" is not connected");
			return;
		}

		byte[] byteCmd = on ? LOCK : UNLOCK;

		if (this.mLockCMDCharacteristic != null) {
			this.mLockCMDCharacteristic.setValue(byteCmd);
			writeCharacteristic(this.mLockCMDCharacteristic, byteCmd);
			this.mLOCKED = on;
			DebugLogger.d("ProximityManager", "Lock command sent. Locked: "+on);
			return;
		}
		DebugLogger.w("ProximityManager", "Lock command Characteristic is not found");
	}

	/**
	 * Returns true if the alert has been enabled on the proximity tag, false otherwise.
	 */
	public boolean isAlertEnabled() {
		return mAlertOn;
	}
}
