package no.nordicsemi.android.nrftoolbox.proximity;

import java.util.HashMap;

public class BleLockCodes {
    static HashMap<Integer, String> cmd_result_codes = new HashMap();

    static {
        cmd_result_codes.put(Integer.valueOf(0), "CMD_SUCCESS");
        cmd_result_codes.put(Integer.valueOf(1), "CMD_REQ_ACCEPTED");
        cmd_result_codes.put(Integer.valueOf(2), "CMD_INVALID_STATE");
        cmd_result_codes.put(Integer.valueOf(3), "CMD_ERROR_INVALID_CALLBACK");
        cmd_result_codes.put(Integer.valueOf(4), "CMD_ERROR_INVALID_PARAMS");
    }

    public static String getCmdResult(byte[] data) {
        return (String) cmd_result_codes.get(Integer.valueOf((byte) (data[0] & -129)));
    }

    public static boolean isFailedLockState(byte[] data) { return (data[0] == -123); }

    public static boolean isNewLockState(byte[] data) { return (data[0] == -128); }

    public static boolean isDoorLocked(byte[] data) { return (data[1] == 6); }

    public static boolean isAdmin(byte[] data) {
        return testBitVal(data[0], 7);
    }

    private static boolean testBitVal(byte b, int pos) {
        return ((1 << pos) & b) != 0;
    }
}
