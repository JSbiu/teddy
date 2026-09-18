package com.dbay.teddy.entity;

/**
 * lib.home 下的一个 JAR 条目，供资源管理页展示。
 *
 * 大小与修改时间都在服务端格式化成可显示文本：大小用 1024 进制单位，
 * 时间用服务器本地时区。放在服务端做有两个原因——显示结果不随浏览器
 * 本地化设置变化，以及在没有前端测试的情况下这两项仍能被单元测试覆盖。
 */
public class JarResource {

    private final String name;
    private final String path;
    private final String sizeText;
    private final String modifiedTimeText;

    public JarResource(String name, String path, String sizeText, String modifiedTimeText) {
        this.name = name;
        this.path = path;
        this.sizeText = sizeText;
        this.modifiedTimeText = modifiedTimeText;
    }

    /** 文件名，用于展示；相对 {@link #getPath()} 更适合放进表格。 */
    public String getName() {
        return name;
    }

    /** 绝对路径，删除接口需要回传它，避免前端从表格文本里反解。 */
    public String getPath() {
        return path;
    }

    /** 已格式化的大小，例如 {@code 12.3 MB}。 */
    public String getSizeText() {
        return sizeText;
    }

    /** 已格式化的修改时间，服务器本地时区，例如 {@code 2026-09-11 16:15:56}。 */
    public String getModifiedTimeText() {
        return modifiedTimeText;
    }
}
