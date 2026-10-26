package dev.niceone.smartsiteappserver.utils;

public interface AppConstants {
    String DEFAULT_PAGE_NUMBER = "0";
    String DEFAULT_PAGE_SIZE = "30";

    int MAX_PAGE_SIZE = 50;

    String LOCK_CABINET_URL = "/lock";
    String UNLOCK_CABINET_URL = "/unlock";
    String SITE_EMERGENCY_KEY = "emerg_key";
    String AUX_ON_URL = "/auxon";
    String AUX_OFF_URL = "/auxoff";
    String HEALTHCHECK_URL = "/healthcheck";
    String ON_WIFI_URL = "/wifion";
    String OFF_WIFI_URL = "/wifioff";
    String CHECK_WIFI_URL = "/wifistatus";
    String RTMP_PUBLISH_URL = "/video/start";
    String RTMP_STOP_URL = "/video/stop";
    String SITE_REPORT = "/report";
    String SITE_CONFIG = "/config";
    String MOTION_TIME = "/motionTime";
    String FIRMWARE_OPTION = "/firmwareOption";
}