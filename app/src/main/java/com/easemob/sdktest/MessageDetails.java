package com.easemob.sdktest;

import com.hyphenate.chat.*;
import java.util.List;

final class MessageDetails {
    static String describe(EMMessage message) {
        List<EMMessageBody> bodies = message.getBodies();
        StringBuilder text = new StringBuilder("messageId=").append(message.getMsgId())
                .append(" from=").append(message.getFrom()).append(" to=").append(message.getTo())
                .append(" chatType=").append(message.getChatType()).append(" status=").append(message.status())
                .append("\n").append(bodies.size() > 1 ? "复合消息" : "单 body 消息")
                .append(" bodyCount=").append(bodies.size());
        for (int index = 0; index < bodies.size(); index++) {
            EMMessageBody body = bodies.get(index);
            text.append("\nbody[").append(index).append("] ").append(body.getClass().getSimpleName());
            if (body instanceof EMTextMessageBody) text.append(" text=").append(((EMTextMessageBody) body).getMessage());
            if (body instanceof EMFileMessageBody) {
                EMFileMessageBody file = (EMFileMessageBody) body;
                text.append(" name=").append(file.displayName()).append(" status=").append(file.downloadStatus())
                        .append("\nlocalPath=").append(file.getLocalUrl());
                if (body instanceof EMNormalFileMessageBody) text.append(" bytes=").append(((EMNormalFileMessageBody) body).getFileSize());
                if (body instanceof EMImageMessageBody) {
                    EMImageMessageBody image = (EMImageMessageBody) body;
                    text.append(" bytes=").append(image.getFileSize()).append("\nthumbnail=")
                            .append(image.thumbnailLocalPath()).append(" status=").append(image.thumbnailDownloadStatus())
                            .append("\nbigImage=").append(image.getBigImageLocalUrl()).append(" status=").append(image.getBigImageDownloadStatus());
                }
            }
        }
        return text.toString();
    }

    static String paths(EMMessage message) {
        StringBuilder text = new StringBuilder();
        for (EMMessageBody body : message.getBodies()) {
            if (body instanceof EMFileMessageBody) {
                String path = ((EMFileMessageBody) body).getLocalUrl();
                if (path != null && !path.isEmpty()) text.append(path).append('\n');
            }
        }
        return text.toString().trim();
    }
}
