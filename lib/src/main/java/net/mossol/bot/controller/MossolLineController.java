package net.mossol.bot.controller;

import java.util.Base64;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.mossol.bot.connection.RetrofitConnection;
import net.mossol.bot.model.LinePushRequest;
import net.mossol.bot.model.LineReplyRequest;
import net.mossol.bot.model.LineRequest;
import net.mossol.bot.model.LineResponse;
import net.mossol.bot.model.LocationInfo;
import net.mossol.bot.model.ReplyMessage;
import net.mossol.bot.model.TextType;
import net.mossol.bot.service.MessageHandler;
import net.mossol.bot.util.MessageBuildUtil;
import net.mossol.bot.util.MossolJsonUtil;

import com.fasterxml.jackson.databind.JsonNode;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Path;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.RequestObject;

/**
 * Created by Amos.Doan.Mac on 2017. 11. 18..
 */
@Service
public class MossolLineController {
    private static final Logger logger = LoggerFactory.getLogger(MossolLineController.class);
    private static final String template = "%dth, Hello, %s!";

    @Value("${line.secret}")
    private String SECRET_KEY;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private RetrofitConnection retrofitConnection;

    private boolean sendFoodReply(String token, LocationInfo menu) {
        return sendReply(MessageBuildUtil.sendFoodMessage(token, menu));
    }

    private boolean sendReply(LineReplyRequest request) {
        String payload = MossolJsonUtil.writeJsonToString(request);
        logger.debug("sendRequest Payload : {}", payload);
        retrofitConnection.sendReply(request);
        return true;
    }

    private boolean leaveChat(String token, LineRequest.Source source, ReplyMessage replyMessage)
            throws Exception {
        String type = source.getType();
        switch (type) {
            case "user":
                sendReply(MessageBuildUtil.sendTextMessage(token, replyMessage.getText()));
                break;
            case "room":
                leaveRoom(source.getRoomId());
                break;
            case "group":
                leaveGroup(source.getGroupId());
                break;
            default:
                logger.warn("Exception occured in source type", type);
                throw new Exception("Given source type is invalid.");
        }

        return true;
    }

    private boolean leaveRoom(String roomId) {
        retrofitConnection.leaveRoom(null, roomId);
        return true;
    }

    private boolean leaveGroup(String groupId) {
        retrofitConnection.leaveGroup(null, groupId);
        return true;
    }

    private boolean validateHeader(String requestBody, String signature) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            String hash = Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(requestBody.getBytes()));
            if (signature.equals(hash)) {
                logger.debug("PASS: Hash Result {}, Signature {} Secret {}", hash, signature, SECRET_KEY);
                return true;
            } else {
                logger.debug("FAIL: Hash Result {}, Signature {} Secret {}", hash, signature, SECRET_KEY);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void handleMessage(LineRequest.Event event) throws Exception {
        final String token = event.getReplyToken();
        final String message = event.getMessage().getText();

        ReplyMessage replyMessage = messageHandler.replyMessage(message);
        if (replyMessage == null) {
            logger.debug("INFO: there is no matching reply message");
            return;
        }

        TextType type = replyMessage.getType();

        switch (type) {
            case SELECT_MENU_K:
            case SELECT_MENU_J:
            case SELECT_MENU_D:
                sendFoodReply(token, replyMessage.getLocationInfo());
                return;
            case LEAVE_CHAT:
                leaveChat(token, event.getSource(), replyMessage);
                break;
            default:
                sendReply(MessageBuildUtil.sendTextMessage(token, replyMessage.getText()));
                return;
        }

        throw new Exception("Send message failed");
    }

    @Post
    @Path("/line")
    public HttpResponse getLine(@Header("X-Line-Signature") String signature,
                                @RequestObject JsonNode request) {
        logger.info("Request from LINE {}", request);
        LineRequest requestObj = MossolJsonUtil.readJsonAsLineRequest(request);

        if (requestObj == null) {
            return null;
        }

        if (!validateHeader(request.toString(), signature)) {
            logger.debug("ERROR : Abusing API Call!");
            return null;
        }

        try {
            logger.debug("Logging : replyMessage {}", request);
            LineRequest.Event event = requestObj.getEvents().get(0);

            if ("message".equals(event.getType())) {
                handleMessage(event);
            } else if ("join".equals(event.getType())) {
                String groupId = event.getSource().getGroupId();
                logger.debug("Join the group {}", groupId);
            }

        } catch (Exception ignore) {
            logger.debug("Exception occurred in replyMessage", ignore);
        }

        LineResponse response = new LineResponse();
        try {
            response.setResponse(requestObj.getEvents().toString());
        } catch (Exception e) {
            logger.debug("Exception occurred in setResponse", e);
        }

        HttpResponse httpResponse = HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8,
                                                    requestObj.getEvents().toString());
        logger.debug("httpResponse <{}>", httpResponse);
        return httpResponse;
    }

    @Post
    @Path("/sendPush")
    public HttpResponse sendPush(@RequestObject JsonNode request) {
        final String message = request.get("message").textValue();
        final String target = request.get("target").textValue();

        logger.info("request {}", request);

        LinePushRequest pushRequest = new LinePushRequest(target);
        pushRequest.setMessage(MessageBuildUtil.buildTextMessage(message));
        retrofitConnection.sendPush(pushRequest);

        return HttpResponse.of(HttpStatus.OK);
    }
}
