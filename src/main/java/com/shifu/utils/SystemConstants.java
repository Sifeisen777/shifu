package com.shifu.utils;

public class SystemConstants {
    /**
     * 图片上传目录已移到配置项 shifu.upload.dir（见 application.yaml）。
     * 绝对路径写死在代码里，换个部署目录就要改源码并重新编译，不适合。
     */
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
