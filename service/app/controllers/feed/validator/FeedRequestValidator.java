package controllers.feed.validator;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

/** This call will validate the Feed API request */
public class FeedRequestValidator extends BaseRequestValidator {
  public static boolean userIdValidation(
      String accessTokenUserId, String managedForUserId, String requestUserId) {
    if (!StringUtils.equalsIgnoreCase(accessTokenUserId, requestUserId)
        && !StringUtils.equalsIgnoreCase(managedForUserId, requestUserId)) {
      ProjectCommonException.throwUnauthorizedErrorException();
    }
    return true;
  }

  public static void validateFeedRequest(Request request) {
    Map<String, Object> feedReq = request.getRequest();
    if (StringUtils.isBlank((String) feedReq.get(JsonKey.USER_ID))) {
      createClientError(ResponseCode.mandatoryParamsMissing, JsonKey.USER_ID);
    }
    if (StringUtils.isBlank((String) feedReq.get(JsonKey.CATEGORY))) {
      createClientError(ResponseCode.mandatoryParamsMissing, JsonKey.CATEGORY);
    }
    if ((feedReq.get(JsonKey.PRIORITY)) == null || ((Integer) feedReq.get(JsonKey.PRIORITY)) <= 0) {
      createClientError(ResponseCode.mandatoryParamsMissing, JsonKey.PRIORITY);
    }
  }

  public static boolean validateFeedDeleteRequest(
      Request request, String callerId1, String callerId2) {
    return validateFeedUpdateRequest(request, callerId1, callerId2);
  }

  public static boolean validateFeedUpdateRequest(
      Request request, String callerId1, String callerId2) {
    Map<String, Object> feedReq = request.getRequest();
    if (StringUtils.isBlank((String) feedReq.get(JsonKey.USER_ID))) {
      createClientError(ResponseCode.mandatoryParamsMissing, JsonKey.USER_ID);
    } else {
      userIdValidation(callerId1, callerId2, (String) feedReq.get(JsonKey.USER_ID));
    }
    if (StringUtils.isBlank((String) feedReq.get(JsonKey.CATEGORY))) {
      createClientError(ResponseCode.mandatoryParamsMissing, JsonKey.CATEGORY);
    }
    if (StringUtils.isBlank((String) feedReq.get(JsonKey.FEED_ID))) {
      createClientError(ResponseCode.mandatoryParamsMissing, JsonKey.FEED_ID);
    }
    return true;
  }
}
