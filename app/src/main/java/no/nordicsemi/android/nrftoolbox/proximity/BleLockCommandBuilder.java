package no.nordicsemi.android.nrftoolbox.proximity;

public class BleLockCommandBuilder {
    private static final int ADMIN_OPTIONS_CMD = 2;
    private static final int CHANGE_LOCK_STATE_CMD = 0;
    private static final int DRIVE_LOCK_CMD = 1;

    public enum BLE_LOCK_CMD {
        CMD_PARAM_CHANGE_STATE_TO_UNARMED(0),
        CMD_PARAM_CHANGE_STATE_TO_ARMED(1),
        CMD_PARAM_DRIVE_LOCK_TO_UNLOCKED(0),
        CMD_PARAM_DRIVE_LOCK_TO_LOCKED(1),
        CMD_PARAM_PROXIMITY_MODE_OFF(0),
        CMD_PARAM_PROXIMITY_MODE_ON(1),
        CMD_PARAM_DELETE_BONDS(2),
        CMD_PARAM_ENABLE_PAIRING(3);
        
        private final int value;

        private BLE_LOCK_CMD(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    public static byte[] buildCommand(BLE_LOCK_CMD cmd) {
        byte[] byteCmd = new byte[2];
        switch (cmd) {
            case CMD_PARAM_CHANGE_STATE_TO_ARMED:
                byteCmd[1] = (byte) 0;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_CHANGE_STATE_TO_ARMED.getValue();
                break;
            case CMD_PARAM_CHANGE_STATE_TO_UNARMED:
                byteCmd[1] = (byte) 0;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_CHANGE_STATE_TO_UNARMED.getValue();
                break;
            case CMD_PARAM_DRIVE_LOCK_TO_LOCKED:
                byteCmd[1] = (byte) 1;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_DRIVE_LOCK_TO_LOCKED.getValue();
                break;
            case CMD_PARAM_DRIVE_LOCK_TO_UNLOCKED:
                byteCmd[1] = (byte) 1;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_DRIVE_LOCK_TO_UNLOCKED.getValue();
                break;
            case CMD_PARAM_PROXIMITY_MODE_OFF:
                byteCmd[1] = (byte) 2;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_PROXIMITY_MODE_OFF.getValue();
                break;
            case CMD_PARAM_PROXIMITY_MODE_ON:
                byteCmd[1] = (byte) 2;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_PROXIMITY_MODE_ON.getValue();
                break;
            case CMD_PARAM_DELETE_BONDS:
                byteCmd[1] = (byte) 2;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_DELETE_BONDS.getValue();
                break;
            case CMD_PARAM_ENABLE_PAIRING:
                byteCmd[1] = (byte) 2;
                byteCmd[0] = (byte) BLE_LOCK_CMD.CMD_PARAM_ENABLE_PAIRING.getValue();
                break;
        }
        return byteCmd;
    }
}
