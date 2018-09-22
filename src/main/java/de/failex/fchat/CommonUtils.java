package de.failex.fchat;

import java.lang.management.ManagementFactory;

public class CommonUtils {


    /**
     * Gets the PID of the curreent process. mainly used to fill the lockfile
     * @return PID of the current process
     */
    public static int getPID() {
        String jvmid = ManagementFactory.getRuntimeMXBean().getName();

        if (jvmid.contains("@")) {
            try {
                return Integer.parseInt(jvmid.substring(0, jvmid.indexOf('@')));
            }
            catch (NumberFormatException e) {
                return -1337;
            }
        } else {
            return -1337;
        }
    }

    /**
     * Check if PID is still alive. mainly used to check if PID in lockfile is currently in use.
     * This will be used in the future when I actually figure out how to do it.
     * @param pid The PID to check
     * @return is PID still alive
     */
    public static int isPIDValid(int pid) {
        String os = System.getProperty("os.name").toLowerCase();
        String command;

        if (os.contains("win")) {
            command = "cmd /c \"tasklist /FI \"PID eq " + pid + "\" | findstr " + pid + "\"";
        } else if (os.contains("nix") || os.contains("nux")) {
            command = "ps -p " + pid;
        }

        return -1337;
    }
}
