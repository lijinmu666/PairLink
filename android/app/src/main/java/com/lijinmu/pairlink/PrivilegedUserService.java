package com.lijinmu.pairlink;

import android.os.RemoteException;
import org.json.JSONObject;

public final class PrivilegedUserService extends IPairLinkUserService.Stub {
    private final ShellRunner runner = new ShellRunner();
    private final ScreenshotEngine screenshots = new ScreenshotEngine();
    private volatile boolean screenshotCancelled;
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
    @Override public String screenshot(String[] blacklist) throws RemoteException {
        screenshotCancelled = false;
        try {
            if (blacklist == null || blacklist.length > 2000) throw new Exception("截图黑名单无效或过大");
            java.util.Set<String> blocked = new java.util.HashSet<>();
            for (String pkg : blacklist) {
                if (pkg == null || pkg.length() > 255 || !AppControl.PACKAGE.matcher(pkg).matches())
                    throw new Exception("截图黑名单包名无效");
                blocked.add(pkg);
            }
            return screenshots.capture(false, blocked, () -> !screenshotCancelled).toString();
        } catch (Exception e) {
            try { return new JSONObject().put("error", e.getMessage() == null ? "截图失败" : e.getMessage()).toString(); }
            catch (Exception ignored) { throw new RemoteException("截图失败"); }
        }
    }
    @Override public void cancel() { screenshotCancelled = true; runner.cancel(); screenshots.cancel(); }
    @Override public void destroy() { cancel(); System.exit(0); }
}
