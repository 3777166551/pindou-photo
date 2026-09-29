package org.json;

/**
 * qa 桌面测试专用迷你 org.json(第 1/3 个文件:JSONException)。
 *
 * 背景:android.jar 里的 org.json 是运行时抛 "Stub!" 的空壳,挡住了全部
 * JSON 层模块(PatternShare/PaletteShare/项目存档等)的桌面 JVM 单测。
 * 本实现编译进 qa/out 后按 classpath 顺序遮蔽 stub;API 与真 org.json
 * 对齐(应用代码零改动即可在测试里跑真解析),仅限 qa 使用,不进 APK。
 * 只实现应用实际用到的面:put/opt/get/has/keys/length/toString。
 */
public class JSONException extends Exception {

    public JSONException(String message) {
        super(message);
    }
}
