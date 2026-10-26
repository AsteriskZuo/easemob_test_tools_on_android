package com.easemob.sdktest;

import com.hyphenate.EMCallBack;
import com.hyphenate.EMValueCallBack;
import com.hyphenate.chat.*;
import com.hyphenate.exceptions.HyphenateException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * API 调用页的注册表：Java 没有运行时按名分发，API 名称到调用的映射需要手工注册。
 * 初始化与登录由「初始化与登录」页面负责，不在此注册。
 */
final class ApiRegistry {
    /** 结果回调；允许在任意线程调用，保证只生效一次由调用方包装。 */
    interface Result { void accept(JSONObject result); }
    interface Invoker { void invoke(JSONObject params, Result deliver) throws Exception; }

    static final class ApiEntry {
        final String name;           // 例如 "ChatManager.sendMessage"
        final String group;          // 例如 "ChatManager"
        final String detail;         // 参数约定与注意事项
        final String paramsTemplate; // 必填字段 JSON 模板
        final Invoker invoker;
        ApiEntry(String name, String group, String detail, String paramsTemplate, Invoker invoker) {
            this.name = name; this.group = group; this.detail = detail;
            this.paramsTemplate = paramsTemplate; this.invoker = invoker;
        }
    }

    /** 参数错误：统一映射为 code = -1 的失败结果。 */
    static final class ParamException extends Exception {
        ParamException(String message) { super(message); }
    }

    static final ExecutorService executor = Executors.newCachedThreadPool();

    // ---- 统一结果：成功 {"success": true, "data": ...}（无返回值则无 data），失败 {"success": false, "error": {"code", "message"}} ----
    static JSONObject success(Object data) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("success", true);
        if (data != null) result.put("data", toJsonSafe(data));
        return result;
    }

    static JSONObject failure(int code, String message) {
        JSONObject result = new JSONObject();
        try {
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message == null ? "" : message);
            result.put("success", false);
            result.put("error", error);
        } catch (JSONException ignored) {}
        return result;
    }

    static JSONObject failure(Throwable error) {
        if (error instanceof ParamException) return failure(-1, error.getMessage());
        if (error instanceof HyphenateException) {
            HyphenateException e = (HyphenateException) error;
            return failure(e.getErrorCode(), e.getDescription());
        }
        return failure(-1, String.valueOf(error.getMessage() != null ? error.getMessage() : error));
    }

    // ---- 参数读取 ----
    static String requiredString(JSONObject params, String key) throws ParamException {
        String value = params.optString(key, "");
        if (value.isEmpty()) throw new ParamException("缺少参数 " + key + "（非空字符串）");
        return value;
    }

    static void requireInit() throws ParamException {
        if (!EMClient.getInstance().isSdkInited()) {
            throw new ParamException("SDK 未初始化，请先在「初始化与登录」页面初始化");
        }
    }

    static List<String> stringList(JSONArray array) throws ParamException {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) values.add(array.optString(i, ""));
        return values;
    }

    // ---- SDK 模型 → 可 JSON 序列化结构（SDK 模型没有统一 toJson，按类型手写） ----
    static Object toJsonSafe(Object value) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof JSONObject || value instanceof JSONArray) return value;
        if (value instanceof EMMessage) return messageToJson((EMMessage) value);
        if (value instanceof EMConversation) return conversationToJson((EMConversation) value);
        if (value instanceof EMGroup) return groupToJson((EMGroup) value);
        if (value instanceof List) {
            JSONArray array = new JSONArray();
            for (Object item : (List<?>) value) array.put(toJsonSafe(item));
            return array;
        }
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.put(String.valueOf(entry.getKey()), toJsonSafe(entry.getValue()));
            }
            return object;
        }
        return String.valueOf(value);
    }

    static JSONObject messageToJson(EMMessage message) throws JSONException {
        JSONArray bodies = new JSONArray();
        List<EMMessageBody> list = message.getBodies();
        for (int index = 0; index < list.size(); index++) {
            bodies.put(bodyToJson(index, list.get(index)));
        }
        JSONObject json = new JSONObject();
        json.put("messageId", message.getMsgId());
        json.put("conversationId", message.conversationId());
        json.put("from", message.getFrom());
        json.put("to", message.getTo());
        json.put("chatType", message.getChatType().ordinal());
        json.put("status", message.status().ordinal());
        json.put("timestamp", message.getMsgTime());
        json.put("bodies", bodies);
        return json;
    }

    /** 注意 Android 按 EMMessage.Type 从 0 开始（0 文本/1 图片/.../7 自定义），与 iOS body type 从 1 开始不同。 */
    static JSONObject bodyToJson(int index, EMMessageBody body) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("index", index);
        int type = bodyType(body);
        item.put("type", type);
        if (type >= 0) item.put("typeName", EMMessage.Type.values()[type].name());
        if (body instanceof EMTextMessageBody) {
            putNullable(item, "text", ((EMTextMessageBody) body).getMessage());
        } else if (body instanceof EMLocationMessageBody) {
            EMLocationMessageBody location = (EMLocationMessageBody) body;
            item.put("latitude", location.getLatitude());
            item.put("longitude", location.getLongitude());
            putNullable(item, "address", location.getAddress());
            putNullable(item, "buildingName", location.getBuildingName());
        } else if (body instanceof EMCmdMessageBody) {
            EMCmdMessageBody cmd = (EMCmdMessageBody) body;
            putNullable(item, "action", cmd.action());
            item.put("deliverOnlineOnly", cmd.isDeliverOnlineOnly());
        } else if (body instanceof EMCustomMessageBody) {
            EMCustomMessageBody custom = (EMCustomMessageBody) body;
            putNullable(item, "event", custom.event());
            item.put("params", toJsonSafe(custom.getParams()));
        } else if (body instanceof EMFileMessageBody) {
            EMFileMessageBody file = (EMFileMessageBody) body;
            putNullable(item, "fileName", file.displayName());
            putNullable(item, "localPath", file.getLocalUrl());
            putNullable(item, "remotePath", file.getRemoteUrl());
            item.put("downloadStatus", String.valueOf(file.downloadStatus()));
            if (body instanceof EMImageMessageBody) {
                EMImageMessageBody image = (EMImageMessageBody) body;
                item.put("fileSize", image.getFileSize());
                item.put("width", image.getWidth());
                item.put("height", image.getHeight());
                item.put("sendOriginalImage", image.isSendOriginalImage());
                putNullable(item, "thumbnailLocalPath", image.thumbnailLocalPath());
                putNullable(item, "thumbnailRemotePath", image.getThumbnailUrl());
                item.put("thumbnailDownloadStatus", String.valueOf(image.thumbnailDownloadStatus()));
                putNullable(item, "bigImageLocalPath", image.getBigImageLocalUrl());
                putNullable(item, "bigImageRemotePath", image.getBigImageRemoteUrl());
                item.put("bigImageDownloadStatus", String.valueOf(image.getBigImageDownloadStatus()));
            } else if (body instanceof EMVideoMessageBody) {
                EMVideoMessageBody video = (EMVideoMessageBody) body;
                item.put("fileSize", video.getVideoFileLength());
                item.put("duration", video.getDuration());
                item.put("thumbnailWidth", video.getThumbnailWidth());
                item.put("thumbnailHeight", video.getThumbnailHeight());
                putNullable(item, "thumbnailLocalPath", video.getLocalThumb());
                putNullable(item, "thumbnailRemotePath", video.getThumbnailUrl());
                item.put("thumbnailDownloadStatus", String.valueOf(video.thumbnailDownloadStatus()));
            } else if (body instanceof EMVoiceMessageBody) {
                EMVoiceMessageBody voice = (EMVoiceMessageBody) body;
                item.put("fileSize", voice.getFileSize());
                item.put("duration", voice.getLength());
            } else if (body instanceof EMNormalFileMessageBody) {
                item.put("fileSize", ((EMNormalFileMessageBody) body).getFileSize());
            }
        }
        return item;
    }

    /** org.json 的 put(key, null) 会删除该键；测试输出需要显式 null 占位。 */
    private static void putNullable(JSONObject json, String key, Object value) throws JSONException {
        json.put(key, value == null ? JSONObject.NULL : value);
    }

    private static int bodyType(EMMessageBody body) {
        if (body instanceof EMTextMessageBody) return EMMessage.Type.TXT.ordinal();
        if (body instanceof EMImageMessageBody) return EMMessage.Type.IMAGE.ordinal();
        if (body instanceof EMVideoMessageBody) return EMMessage.Type.VIDEO.ordinal();
        if (body instanceof EMLocationMessageBody) return EMMessage.Type.LOCATION.ordinal();
        if (body instanceof EMVoiceMessageBody) return EMMessage.Type.VOICE.ordinal();
        if (body instanceof EMFileMessageBody) return EMMessage.Type.FILE.ordinal();
        if (body instanceof EMCmdMessageBody) return EMMessage.Type.CMD.ordinal();
        if (body instanceof EMCustomMessageBody) return EMMessage.Type.CUSTOM.ordinal();
        return -1;
    }

    static JSONObject conversationToJson(EMConversation conversation) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("conversationId", conversation.conversationId());
        json.put("type", conversation.getType().ordinal());
        json.put("unreadMessagesCount", conversation.getUnreadMsgCount());
        json.put("messagesCount", conversation.getAllMsgCount());
        EMMessage latest = conversation.getLastMessage();
        if (latest != null) json.put("latestMessageId", latest.getMsgId());
        return json;
    }

    static JSONObject groupToJson(EMGroup group) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("groupId", group.getGroupId());
        json.put("groupName", group.getGroupName());
        json.put("owner", group.getOwner());
        json.put("description", group.getDescription());
        return json;
    }

    static ApiEntry find(String name) {
        for (ApiEntry entry : ENTRIES) if (entry.name.equals(name)) return entry;
        return null;
    }

    static final List<ApiEntry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
        new ApiEntry(
            "ChatManager.sendMessage",
            "ChatManager",
            "发送文本消息（对应 EMChatManager 的 sendMessage + EMMessage.setMessageStatusCallback 异步接口）。\n"
                    + "chatType：0 单聊 / 1 群聊 / 2 聊天室；群聊与聊天室的 to 填已加入的群 ID / 聊天室 ID。\n"
                    + "成功时 data 为发送完成后的消息 JSON，messageId 已是服务器下发的 ID。",
            "{\n  \"to\": \"targetId\",\n  \"chatType\": 0,\n  \"content\": \"hello\"\n}",
            (params, deliver) -> {
                requireInit();
                String to = requiredString(params, "to");
                String content = requiredString(params, "content");
                int chatType = params.optInt("chatType", 0);
                if (chatType < 0 || chatType > 2) throw new ParamException("chatType 只支持 0/1/2");
                EMMessage message = EMMessage.createTextSendMessage(content, to);
                message.setChatType(EMMessage.ChatType.values()[chatType]);
                message.setMessageStatusCallback(new EMCallBack() {
                    @Override public void onSuccess() { deliver.accept(wrap(message)); }
                    @Override public void onError(int code, String error) { deliver.accept(failure(code, error)); }
                });
                EMClient.getInstance().chatManager().sendMessage(message);
            }
        ),
        new ApiEntry(
            "ChatManager.getAllConversations",
            "ChatManager",
            "从本地数据库加载全部会话（同步方法，对应 getAllConversations，按 conversationId 排序输出）。\n"
                    + "data 为会话数组，含 conversationId、type（0 单聊/1 群聊/2 聊天室）、unreadMessagesCount、messagesCount、latestMessageId。",
            "{}",
            (params, deliver) -> {
                requireInit();
                List<EMConversation> conversations = new ArrayList<>(
                        EMClient.getInstance().chatManager().getAllConversations().values());
                conversations.sort(Comparator.comparing(EMConversation::conversationId));
                deliver.accept(success(conversations));
            }
        ),
        new ApiEntry(
            "ContactManager.getContactsFromLocal",
            "ContactManager",
            "获取本地数据库中的全部联系人（同步方法，对应 getContactsFromLocal，需要登录后读取本地库）。\n"
                    + "data 为联系人 userId 字符串数组。注意 Android 本地接口只返回 userId 列表；iOS 的 getAllContacts 返回含备注的对象数组。",
            "{}",
            (params, deliver) -> {
                requireInit();
                deliver.accept(success(EMClient.getInstance().contactManager().getContactsFromLocal()));
            }
        ),
        new ApiEntry(
            "GroupManager.getAllGroups",
            "GroupManager",
            "获取本地缓存的当前用户已加入群组列表（同步方法，对应 getAllGroups；iOS 对应接口叫 getJoinedGroups）。\n"
                    + "data 为群组数组，含 groupId、groupName、owner、description。",
            "{}",
            (params, deliver) -> {
                requireInit();
                deliver.accept(success(EMClient.getInstance().groupManager().getAllGroups()));
            }
        ),
        new ApiEntry(
            "ChatManager.asyncFetchHistoryMessages",
            "ChatManager",
            "从服务器分页拉取指定会话的历史消息（对应 EMChatManager 的 asyncFetchHistoryMessages，iOS 叫 fetchMessagesFromServer）。\n"
                    + "conversationType：0 单聊 / 1 群聊 / 2 聊天室；conversationId 单聊填对端用户 ID，群聊/聊天室填群 ID/聊天室 ID。\n"
                    + "cursor 传空字符串从最新开始；pageSize 取值 [1,50]。\n"
                    + "option 可整体省略；fromIds 按发送方过滤（仅群聊）；msgTypes 为消息类型数组（0 文本/1 图片/2 视频/3 位置/4 语音/5 文件/6 指令/7 自定义）；"
                    + "startTime/endTime 为毫秒时间戳（-1 表示忽略）；direction：0 倒序（UP）/ 1 正序（DOWN）；isSave 控制拉取结果是否写入本地数据库（默认 false）。\n"
                    + "返回 data 为 {\"cursor\": \"...\", \"list\": [消息 JSON...]}；返回条数小于 pageSize 说明服务器没有更多历史消息。",
            "{\n"
                    + "  \"conversationId\": \"targetId\",\n"
                    + "  \"conversationType\": 0,\n"
                    + "  \"cursor\": \"\",\n"
                    + "  \"pageSize\": 20,\n"
                    + "  \"option\": {\n"
                    + "    \"direction\": 0,\n"
                    + "    \"isSave\": false\n"
                    + "  }\n"
                    + "}",
            (params, deliver) -> {
                requireInit();
                String conversationId = requiredString(params, "conversationId");
                int type = params.optInt("conversationType", 0);
                if (type < 0 || type > 2) throw new ParamException("conversationType 只支持 0/1/2");
                String cursor = params.optString("cursor", "");
                int pageSize = params.optInt("pageSize", 20);
                EMFetchMessageOption option = null;
                JSONObject optionParams = params.optJSONObject("option");
                if (optionParams != null) {
                    option = new EMFetchMessageOption();
                    JSONArray fromIds = optionParams.optJSONArray("fromIds");
                    if (fromIds != null) option.setFromIds(stringList(fromIds));
                    JSONArray msgTypes = optionParams.optJSONArray("msgTypes");
                    if (msgTypes != null) {
                        List<EMMessage.Type> types = new ArrayList<>();
                        for (int i = 0; i < msgTypes.length(); i++) {
                            int raw = msgTypes.optInt(i, -1);
                            if (raw < 0 || raw >= EMMessage.Type.values().length) {
                                throw new ParamException("msgTypes 取值范围 0-" + (EMMessage.Type.values().length - 1));
                            }
                            types.add(EMMessage.Type.values()[raw]);
                        }
                        option.setMsgTypes(types);
                    }
                    if (optionParams.has("startTime")) option.setStartTime(optionParams.optLong("startTime", -1));
                    if (optionParams.has("endTime")) option.setEndTime(optionParams.optLong("endTime", -1));
                    if (optionParams.optInt("direction", 0) == 1) {
                        option.setDirection(EMConversation.EMSearchDirection.DOWN);
                    } else {
                        option.setDirection(EMConversation.EMSearchDirection.UP);
                    }
                    if (optionParams.has("isSave")) option.setIsSave(optionParams.optBoolean("isSave", false));
                }
                EMClient.getInstance().chatManager().asyncFetchHistoryMessages(
                        conversationId,
                        EMConversation.EMConversationType.values()[type],
                        pageSize,
                        cursor,
                        option,
                        new EMValueCallBack<EMCursorResult<EMMessage>>() {
                            @Override public void onSuccess(EMCursorResult<EMMessage> value) {
                                try {
                                    JSONObject data = new JSONObject();
                                    data.put("cursor", value.getCursor() == null ? "" : value.getCursor());
                                    data.put("list", toJsonSafe(value.getData() == null ? new ArrayList<>() : value.getData()));
                                    deliver.accept(success(data));
                                } catch (JSONException error) {
                                    deliver.accept(failure(error));
                                }
                            }
                            @Override public void onError(int error, String errorMsg) {
                                deliver.accept(failure(error, errorMsg));
                            }
                        });
            }
        )
    ));

    private static JSONObject wrap(Object value) {
        try {
            return success(value);
        } catch (JSONException error) {
            return failure(error);
        }
    }
}
