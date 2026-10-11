package com.lijinmu.pairlink;

interface IPairLinkUserService {
    void destroy() = 16777114;
    String run(String command, int seconds) = 1;
    void cancel() = 2;
    String screenshot(in String[] blacklist) = 3;
}
