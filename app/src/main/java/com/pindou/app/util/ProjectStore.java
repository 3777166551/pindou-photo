package com.pindou.app.util;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 拼豆项目存档:把当前照片、生成参数和手动修格记录打包成
 * 一个 JSON 文件,存在应用私有目录 files/projects/ 里。
 * 首页「我的项目」可列出所有存档并重新打开/删除。
 *
 * 一个项目文件的结构(由 EditorActivity 组装):
 * { name, savedAt, width, height,
 *   photo  : Base64(JPEG 压缩后的原图),
 *   thumb  : Base64(JPEG 小缩略图,列表展示用),
 *   settings : {...全部生成参数...},
 *   edits  : [[格下标, 色板下标], ...] }
 */
public final class ProjectStore {

    /** 一条项目记录的元信息(列表展示用) */
    public static final class Entry {
        public File file;
        public String name;
        public long savedAt;
        public Bitmap thumb;   // 可能是 null(解码失败时)
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "projects");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 新建一个项目文件,返回文件名;photo 会另存缩略图供列表用 */
    public static File create(Context ctx, String name, long savedAt) {
        String fileName = "proj_" + savedAt + "_" + Integer.toHexString(
                (name + "" + System.nanoTime()).hashCode()) + ".json";
        return new File(dir(ctx), fileName);
    }

    /** 列出全部项目,按保存时间从新到旧排序 */
    public static List<Entry> list(Context ctx) {
        List<Entry> out = new ArrayList<>();
        File[] files = dir(ctx).listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().endsWith(".json")) continue;
                try {
                    org.json.JSONObject o = Jsons.read(f);
                    Entry e = new Entry();
                    e.file = f;
                    e.name = o.optString("name", f.getName());
                    e.savedAt = o.optLong("savedAt", f.lastModified());
                    e.thumb = Jsons.decodeBitmap(o.optString("thumb"));
                    out.add(e);
                } catch (Exception ignored) {
                }
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.savedAt, a.savedAt);
            }
        });
        return out;
    }

    public static void delete(File f) {
        if (f != null && f.exists()) f.delete();
    }

    private ProjectStore() {
    }
}
