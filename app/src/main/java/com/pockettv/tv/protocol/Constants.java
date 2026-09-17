package com.pockettv.tv.protocol;

/** 协议冻结常量，必须与 docs/PROTOCOL.md 逐字一致。 */
public final class Constants {
    public static final int CONTROL_PORT = 17880;
    public static final int UDP_PORT = 17882;
    public static final String NSD_TYPE = "_pockettv._tcp.";
    public static final String NSD_NAME = "PocketTV";
    public static final String WS_PATH = "/ws";
    public static final String UDP_MAGIC = "PTVDISC1";
    public static final int PROTOCOL_V = 1;
    public static final String ROOT_DIR_NAME = "PocketTV";
    public static final String DIR_INBOX = "inbox";
    public static final String DIR_APK = "apk";
    public static final String FILE_AUTHORITY = "com.pockettv.tv.files";

    public static final String TYPE_HELLO = "hello";
    public static final String TYPE_HELLO_OK = "hello_ok";
    public static final String TYPE_NEED_PIN = "need_pin";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_KEY = "key";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_APPS = "apps";
    public static final String TYPE_APPS_OK = "apps_ok";
    public static final String TYPE_APP_OPEN = "app_open";
    public static final String TYPE_APP_UNINSTALL = "app_uninstall";

    public static final String ERR_AUTH = "AUTH";
    public static final String ERR_INJECT = "INJECT";
    public static final String ERR_TEXT = "TEXT";
    public static final String ERR_FS = "FS";
    public static final String ERR_PROTOCOL = "PROTOCOL";

    public static final int PIN_TTL_MS = 60 * 1000;
    public static final int PIN_MAX_FAIL = 3;

    private Constants() {}
}
