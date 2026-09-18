package com.pocketremote.helper.inject;

/** 系统签名插件提供的按键/文本注入。仅允许助手进程绑定。 */
interface IPocketInject {
    boolean key(int code);
    boolean text(String raw);
    boolean ping();
    boolean pointer(String action, int dx, int dy);
}
