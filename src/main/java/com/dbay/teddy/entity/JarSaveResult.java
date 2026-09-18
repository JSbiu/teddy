package com.dbay.teddy.entity;

import java.util.List;

/**
 * 一次 JAR 保存的结果。
 *
 * {@link #getOverwritten()} 存在的意义是让"上传替换掉了同名文件"这件事可见：
 * 替换本身是更新 JAR 的正常途径，不能阻断，但使用者必须知道替换掉了什么、
 * 是否牵连到正在引用该文件名的任务。
 */
public class JarSaveResult {

    private final List<JarResource> jars;
    private final JarResource overwritten;

    public JarSaveResult(List<JarResource> jars, JarResource overwritten) {
        this.jars = jars;
        this.overwritten = overwritten;
    }

    /** 保存后的完整列表，供接口直接回传给页面。 */
    public List<JarResource> getJars() {
        return jars;
    }

    /** 被本次上传替换掉的同名文件（替换前的属性）；没有同名文件时为 null。 */
    public JarResource getOverwritten() {
        return overwritten;
    }
}
