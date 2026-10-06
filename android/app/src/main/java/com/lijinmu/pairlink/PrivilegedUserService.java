package com.lijinmu.pairlink;

import android.os.RemoteException;
import org.json.JSONObject;

public final class PrivilegedUserService extends IPairLinkUserService.Stub {
    private final ShellRunner runner = new ShellRunner();
    public PrivilegedUserService() {}

    @Override public String run(String command, int seconds) throws RemoteException {
        try {
            if (command == null || command.trim().isEmpty() || command.length() > 8192 || seconds < 1 || seconds > 30)
                throw new Exception("Shell 参数无效");
            ShellRunner.Result r = runner.run(command, false, seconds, () -> true);
            return new JSONObject().put("stdout", r.stdout).put("stderr", r.stderr).put("exit_code", r.exitCode)
                .put("timed_out", r.timedOut).put("cancelled", r.cancelled).put("truncated", r.truncated).toString();
        } catch (Exception e) {
            try { return new JSONObject().put("error", e.getMessage() == null ? "特权执行失败" : e.getMessage()).toString(); }
            catch (Exception ignored) { throw new RemoteException("特权执行失败"); }
        }
    }
    @Override public void cancel() { runner.cancel(); }
    @Override public void destroy() { runner.cancel(); System.exit(0); }
}
