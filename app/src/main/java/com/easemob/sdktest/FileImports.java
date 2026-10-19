package com.easemob.sdktest;

import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

final class FileImports {
    static void add(SdkSession session, List<Uri> sources, boolean images) {
        if (session.importing) { session.log("文件正在导入，请稍候"); return; }
        session.importing = true;
        session.log("开始导入 " + sources.size() + " 个文件");
        session.files.execute(() -> {
            List<SdkSession.DraftBody> imported = new ArrayList<>();
            for (Uri uri : sources) {
                File destination = null;
                try {
                    String name = filename(session, uri);
                    File directory = new File(session.context.getFilesDir(), "imports/" + UUID.randomUUID());
                    if (!directory.mkdirs()) throw new IOException("无法创建导入目录");
                    destination = new File(directory, name);
                    try (InputStream input = session.context.getContentResolver().openInputStream(uri);
                         OutputStream output = new FileOutputStream(destination)) {
                        if (input == null) throw new IOException("无法读取选择的文件");
                        byte[] buffer = new byte[65536];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    imported.add(new SdkSession.DraftBody(images ? "IMAGE" : "FILE",
                            destination.getAbsolutePath(), name, destination.length()));
                } catch (IOException | RuntimeException error) {
                    if (destination != null && destination.exists()) destination.delete();
                    session.log("导入失败：" + uri + "，" + error.getClass().getSimpleName());
                }
            }
            session.main.post(() -> {
                session.drafts.addAll(imported);
                session.importing = false;
                session.log("已导入 " + imported.size() + "/" + sources.size() + " 个文件；请检查列表后发送");
            });
        });
    }

    private static String filename(SdkSession session, Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = session.context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
        }
        if (name == null || name.trim().isEmpty()) name = uri.getLastPathSegment();
        if (name == null) name = "attachment";
        name = name.replace('/', '_').replace('\\', '_').replace('\n', '_').replace('\r', '_');
        return name.equals(".") || name.equals("..") || name.isEmpty() ? "attachment" : name;
    }
}
