package no.nordicsemi.android.nrftoolbox.proximity;

public class BleLockState {
    private static final byte BATTERY_STATUS_BASE = (byte) 0;
    private static final byte BATTERY_STATUS_MASK = (byte) 3;
    private static final int LOCK_CMD_RESULT_CODE = 0;
    private static final int LOCK_MODE_BASE = 0;
    private static final int LOCK_MODE_MASK = 1;
    private static final int LOCK_POSITION_BIT = 1;
    private static final int LOCK_STATE_BIT = 0;
    private static final int LOCK_STATE_BYTE_0 = 1;
    private static final int LOCK_STATE_BYTE_1 = 2;
    private static final int LOCK_STATE_BYTE_2 = 3;
    private static final int LOCK_TYPE_BASE = 5;
    private static final int LOCK_TYPE_MASK = 16;
    private static final String LOG_TAG = BleLockState.class.getSimpleName();
    private static final byte SHOCK_DETECTION_BASE = (byte) 2;
    private static final byte SHOCK_DETECTION_MASK = (byte) 24;
    private static final int UNIT_STATE_BASE = 2;
    private static final int UNIT_STATE_MASK = 28;
    public float LOCK_FIRMWARE_VERSION;
    public boolean LOCK_MODE;
    public BATTERY_STATUS mBatteryStatus;
    private boolean mBleLockAlarmState;
    private boolean mBleLockBurglarAttempt;
    public LOCKING_MECHANISM_POSITION mLockPosition;
    private LOCK_STATE mLockState;
    private LOCK_TYPE mLockType;
    private LOCK_VERSION mLockVersion;
    private UNIT_STATE mUnitState;
    private int unitCharacter;

    public enum BATTERY_STATUS {
        BATTERY_GOOD(0),
        BATTERY_LOW(1),
        BATTERY_CRYTICAL(2),
        BATTERRY_EMPTY(3),
        BATTERY_UNKNOWN(4);
        
        private int value;

        private BATTERY_STATUS(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    public enum FIRE_DETECTION_SOURCE {
        SOURCE_NULL,
        SOURCE_INT,
        SOURCE_EXT
    }

    public enum FIRE_DETECTION_STATE {
        IDLE,
        IN_PROGRESS,
        TRIGGERED
    }

    public enum LOCKING_MECHANISM_POSITION {
        LOCKED,
        UNLOCKED,
        LOCK_UNLOCK_IN_PROGRESS,
        UNKNOWN_POSITION
    }

    public enum LOCK_ORIENTATION {
        BOTTOM,
        TOP,
        LEFT,
        RIGHT,
        UNKNOWN_ORIENTATION
    }

    public enum LOCK_STATE {
        UNARMED(0),
        ARMED(1),
        UNKNOWN_STATE(2);
        
        private final int value;

        private LOCK_STATE(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    public enum LOCK_TYPE {
        HANDLE_LOCK(0),
        FRAME_LOCK(1),
        UNKN_LOCK_TYPE(2);
        
        private int value;

        private LOCK_TYPE(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    public enum LOCK_VERSION {
        HARDWARE_15(0),
        HARDWARE_21(1),
        HARDWARE_22(2),
        UNKNOWN(3);
        
        private int value;

        private LOCK_VERSION(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    public enum SHOCK_DETECTION_STATE {
        IDLE,
        IN_PROGRESS,
        TRIGGERED
    }

    public enum UNIT_STATE {
        FOHO(0),
        FCHC(1),
        FCHO(2),
        FOHC(3),
        FOHU_KIP(4),
        FOHO_TBT(5),
        UNKNOWN_UNIT_STATE(6);
        
        private int value;

        private UNIT_STATE(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    public BleLockState() {
        this.mLockState = LOCK_STATE.UNKNOWN_STATE;
        this.mLockPosition = LOCKING_MECHANISM_POSITION.UNKNOWN_POSITION;
        this.mUnitState = UNIT_STATE.UNKNOWN_UNIT_STATE;
        this.mLockType = LOCK_TYPE.UNKN_LOCK_TYPE;
        this.mLockVersion = LOCK_VERSION.UNKNOWN;
        this.mLockState = LOCK_STATE.UNKNOWN_STATE;
        this.mLockPosition = LOCKING_MECHANISM_POSITION.UNKNOWN_POSITION;
        this.mBatteryStatus = BATTERY_STATUS.BATTERY_UNKNOWN;
        this.mUnitState = UNIT_STATE.UNKNOWN_UNIT_STATE;
        this.mLockType = LOCK_TYPE.UNKN_LOCK_TYPE;
        this.mLockVersion = LOCK_VERSION.UNKNOWN;
    }

    public BleLockState(byte[] lockStatus) {
        this.mLockState = LOCK_STATE.UNKNOWN_STATE;
        this.mLockPosition = LOCKING_MECHANISM_POSITION.UNKNOWN_POSITION;
        this.mUnitState = UNIT_STATE.UNKNOWN_UNIT_STATE;
        this.mLockType = LOCK_TYPE.UNKN_LOCK_TYPE;
        this.mLockVersion = LOCK_VERSION.UNKNOWN;
        setLockState(lockStatus);
    }

    public void setLockVersion(byte[] lockStatus) {
        byte Lockversion = lockStatus[0];
        if (lockStatus != null) {
            switch (Lockversion) {
                case (byte) 15:
                    this.mLockVersion = LOCK_VERSION.HARDWARE_15;
                    return;
                case (byte) 21:
                    this.mLockVersion = LOCK_VERSION.HARDWARE_21;
                    return;
                case (byte) 22:
                    this.mLockVersion = LOCK_VERSION.HARDWARE_22;
                    return;
                default:
                    this.mLockVersion = LOCK_VERSION.UNKNOWN;
                    return;
            }
        }
    }

    public void setLockFirmwareVersion(byte[] lockStatus) {
        this.LOCK_FIRMWARE_VERSION = (float) lockStatus[1];
        this.LOCK_FIRMWARE_VERSION /= 10.0f;
    }

    public void setLockState(byte[] lockStatus) {
        byte b = lockStatus[0];
        b = lockStatus[1];
        if (testBitVal(b, 0)) {
            this.mLockState = LOCK_STATE.ARMED;
        } else {
            this.mLockState = LOCK_STATE.UNARMED;
        }
        if (testBitVal(b, 1)) {
            this.mLockPosition = LOCKING_MECHANISM_POSITION.LOCKED;
        } else {
            this.mLockPosition = LOCKING_MECHANISM_POSITION.UNLOCKED;
        }
        b = (byte) (((byte) (lockStatus[2] & 28)) >>> 2);
        if (b == UNIT_STATE.FOHO.getValue()) {
            this.mUnitState = UNIT_STATE.FOHO;
        } else if (b == UNIT_STATE.FCHC.getValue()) {
            this.mUnitState = UNIT_STATE.FCHC;
        } else if (b == UNIT_STATE.FCHO.getValue()) {
            this.mUnitState = UNIT_STATE.FCHO;
        } else if (b == UNIT_STATE.FOHC.getValue()) {
            this.mUnitState = UNIT_STATE.FOHC;
        } else if (b == UNIT_STATE.FOHU_KIP.getValue()) {
            this.mUnitState = UNIT_STATE.FOHU_KIP;
        } else if (b == UNIT_STATE.FOHO_TBT.getValue()) {
            this.mUnitState = UNIT_STATE.FOHO_TBT;
        } else {
            this.mUnitState = UNIT_STATE.UNKNOWN_UNIT_STATE;
        }
        b = (byte) (((byte) (lockStatus[2] & 3)) >>> 0);
        if (b == BATTERY_STATUS.BATTERY_GOOD.getValue()) {
            this.mBatteryStatus = BATTERY_STATUS.BATTERY_GOOD;
        } else if (b == BATTERY_STATUS.BATTERY_LOW.getValue()) {
            this.mBatteryStatus = BATTERY_STATUS.BATTERY_LOW;
        } else if (b == BATTERY_STATUS.BATTERY_CRYTICAL.getValue()) {
            this.mBatteryStatus = BATTERY_STATUS.BATTERY_CRYTICAL;
        } else if (b == BATTERY_STATUS.BATTERRY_EMPTY.getValue()) {
            this.mBatteryStatus = BATTERY_STATUS.BATTERRY_EMPTY;
        } else {
            this.mBatteryStatus = BATTERY_STATUS.BATTERY_UNKNOWN;
        }
        if (((byte) (((byte) (lockStatus[3] & 1)) >>> 0)) == (byte) 1) {
            this.LOCK_MODE = true;
        } else {
            this.LOCK_MODE = false;
        }
    }

    public LOCK_STATE getLockState() {
        return this.mLockState;
    }

    public LOCK_TYPE getLockType() {
        return this.mLockType;
    }

    public LOCK_VERSION getLockVersion() {
        return this.mLockVersion;
    }

    public UNIT_STATE getUnitState() {
        return this.mUnitState;
    }

    public LOCKING_MECHANISM_POSITION getmLockingMechanismPosition() {
        return this.mLockPosition;
    }

    private boolean testBitVal(byte b, int pos) {
        return ((1 << pos) & b) != 0;
    }
}
