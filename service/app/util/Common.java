package util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.response.ResponseParams;
import org.sunbird.common.responsecode.ResponseCode;
import play.libs.typedmap.TypedKey;
import play.mvc.Http;

public class Common {

  public static Map<String, String[]> getRequestHeadersInArray(
      Map<String, List<String>> requestHeaders) {
    Map<String, String[]> requestHeadersArray = new HashMap();
    requestHeaders
        .entrySet()
        .forEach(
            entry -> {
              requestHeadersArray.put(entry.getKey(), (String[]) entry.getValue().toArray());
            });
    return requestHeadersArray;
  }

  public static String getFromRequest(Http.Request httpReq, TypedKey<?> attribute) {
    String attributeValue = null;
    if (httpReq.attrs() != null && httpReq.attrs().containsKey(attribute)) {
      attributeValue = (String) httpReq.attrs().get(attribute);
    }
    return attributeValue;
  }

  public static ResponseParams createResponseParamObj(
      ResponseCode code, String customMessage, String requestId) {
    ResponseParams params = new ResponseParams();
    if (code.getResponseCode() != 200) {
      params.setErr(code.getErrorCode());
      params.setErrmsg(
          StringUtils.isNotBlank(customMessage) ? customMessage : code.getErrorMessage());
    }
    params.setStatus(ResponseCode.getHeaderResponseCode(code.getResponseCode()).name());
    params.setMsgid(requestId);
    return params;
  }
}
