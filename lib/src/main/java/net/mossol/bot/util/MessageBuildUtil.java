package net.mossol.bot.util;

import net.mossol.bot.model.LineReplyRequest;
import net.mossol.bot.model.LocationInfo;
import net.mossol.bot.model.Message.LocationMessage;
import net.mossol.bot.model.Message.TextMessage;

public final class MessageBuildUtil {

    private static final String NO_LOCATION = "멍멍! 등록된 위치 정보가 없어요 ㅜ";
    private static final String EXIST_LOCATION = "멍멍! 등록된 위치 정보가 있어요!";
    private static final String SELECT_MENU = "멍멍 %s 안먹으면 가서 깨뭅니다";

    private MessageBuildUtil() {
    }

    public static TextMessage buildTextMessage(String content) {
        TextMessage replyMessage = new TextMessage(content);
        replyMessage.setType("text");
        return replyMessage;
    }

    private static LocationMessage buildLocationMessage(String title, String address,
                                                        double latitude, double longitude) {
        LocationMessage locationMessage = new LocationMessage(title, address, latitude, longitude);
        locationMessage.setType("location");
        return locationMessage;
    }

    public static LineReplyRequest sendTextMessage(String token, String content) {
        LineReplyRequest replyRequest = new LineReplyRequest(token);
        replyRequest.setMessage(buildTextMessage(content));
        return replyRequest;
    }

    public static LineReplyRequest sendFoodMessage(String token, LocationInfo locationInfo) {
        final String content = String.format(SELECT_MENU, locationInfo.getTitle());

        LineReplyRequest replyRequest = new LineReplyRequest(token);
        replyRequest.setMessage(buildTextMessage(content));
        if (Double.compare(locationInfo.getLatitude(), -1) == 0 &&
            Double.compare(locationInfo.getLongitude(), -1) == 0) {
            replyRequest.setMessage(buildTextMessage(NO_LOCATION));
        } else {
            replyRequest.setMessage(buildTextMessage(EXIST_LOCATION));
            replyRequest.setMessage(buildLocationMessage(locationInfo.getTitle(), locationInfo.getTitle(),
                                                         locationInfo.getLatitude(), locationInfo.getLongitude()));
        }
        return replyRequest;
    }

    public static String sendFoodMessage(LocationInfo locationInfo) {
        return String.format(SELECT_MENU, locationInfo.getTitle());
    }
}
