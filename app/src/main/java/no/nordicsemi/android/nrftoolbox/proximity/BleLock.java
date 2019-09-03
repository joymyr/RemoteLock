package no.nordicsemi.android.nrftoolbox.proximity;

import android.bluetooth.BluetoothDevice;
import android.util.Pair;

import no.nordicsemi.android.nrftoolbox.proximity.BleLockState.LOCK_TYPE;
import no.nordicsemi.android.nrftoolbox.proximity.BleLockState.LOCK_VERSION;
import no.nordicsemi.android.nrftoolbox.proximity.BleLockState.UNIT_STATE;

public class BleLock {
    private boolean isAdmin = false;
    private BleLockState mBleLockState;
    private Pair<String, String> mBleLockVersion;
    private BluetoothDevice mDevice;
    private BLE_DEVICE_CONNECTION_STATE mDeviceConnectionState = BLE_DEVICE_CONNECTION_STATE.DISCONNECTED;
    private String name;

    public enum BLE_DEVICE_CONNECTION_STATE {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        SERVICES_DISCOVERED,
        UNKNOWN
    }

    public BleLock(BluetoothDevice device) {
        this.mDevice = device;
        this.mBleLockState = new BleLockState();
    }

    public BleLock(BluetoothDevice device, byte[] state) {
        this.mDevice = device;
        this.mBleLockState = new BleLockState(state);
        this.mBleLockVersion = new Pair(null, null);
    }

    public BluetoothDevice getmDevice() {
        return this.mDevice;
    }

    public void setmDevice(BluetoothDevice mDevice) {
        this.mDevice = mDevice;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public BleLockState getBleLockState() {
        return this.mBleLockState;
    }

    private LOCK_TYPE getBleLockType() {
        return this.mBleLockState.getLockType();
    }

    private LOCK_VERSION getBleLockVersion() {
        return this.mBleLockState.getLockVersion();
    }

    private UNIT_STATE getBleUnitState() {
        return this.mBleLockState.getUnitState();
    }

    public void setConnectionState(BLE_DEVICE_CONNECTION_STATE state) {
        this.mDeviceConnectionState = state;
    }

    public BLE_DEVICE_CONNECTION_STATE getConenctionState() {
        return this.mDeviceConnectionState;
    }

    public boolean isConnected() {
        if (this.mDeviceConnectionState != BLE_DEVICE_CONNECTION_STATE.DISCONNECTED) {
            return true;
        }
        return false;
    }

    public boolean realConnection() {
        if (this.mDeviceConnectionState == BLE_DEVICE_CONNECTION_STATE.SERVICES_DISCOVERED) {
            return true;
        }
        return false;
    }

    public boolean getIsAdmin() {
        return this.isAdmin;
    }

    public void setIsAdmin(boolean adminOptions) {
        this.isAdmin = adminOptions;
    }

    public String toString() {
        return this.mDevice.getName() + " | " + this.mDevice.getAddress() + " | " + this.mBleLockState.toString();
    }
}
