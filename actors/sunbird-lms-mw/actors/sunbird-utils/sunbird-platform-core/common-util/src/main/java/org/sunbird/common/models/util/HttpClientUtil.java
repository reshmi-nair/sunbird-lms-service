package org.sunbird.common.models.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.MapUtils;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

public class HttpClientUtil {
  private static LoggerUtil logger = new LoggerUtil(HttpClientUtil.class);

  private static CloseableHttpClient httpclient = null;
  private static HttpClientUtil httpClientUtil;

  private HttpClientUtil() {
    ConnectionKeepAliveStrategy keepAliveStrategy =
        (response, context) -> {
          HeaderElementIterator it =
              new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
          while (it.hasNext()) {
            HeaderElement he = it.nextElement();
            String param = he.getName();
            String value = he.getValue();
            if (value != null && param.equalsIgnoreCase("timeout")) {
              return Long.parseLong(value) * 1000;
            }
          }
          return 180 * 1000;
        };

    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(200);
    connectionManager.setDefaultMaxPerRoute(150);
    connectionManager.closeIdleConnections(180, TimeUnit.SECONDS);
    httpclient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .useSystemProperties()
            .setKeepAliveStrategy(keepAliveStrategy)
            .build();
  }

  public static HttpClientUtil getInstance() {
    if (httpClientUtil == null) {
      synchronized (HttpClientUtil.class) {
        if (httpClientUtil == null) {
          httpClientUtil = new HttpClientUtil();
        }
      }
    }
    return httpClientUtil;
  }

  public static String get(String requestURL, Map<String, String> headers) {
    CloseableHttpResponse response = null;
    try {
      HttpGet httpGet = new HttpGet(requestURL);
      if (MapUtils.isNotEmpty(headers)) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          httpGet.addHeader(entry.getKey(), entry.getValue());
        }
      }
      response = httpclient.execute(httpGet);
      int status = response.getStatusLine().getStatusCode();
      if (status >= 200 && status < 300) {
        HttpEntity httpEntity = response.getEntity();
        byte[] bytes = EntityUtils.toByteArray(httpEntity);
        StatusLine sl = response.getStatusLine();
        logger.debug(
            "Response from get call : " + sl.getStatusCode() + " - " + sl.getReasonPhrase());
        return new String(bytes);
      } else {
        getErrorResponse(response, "GET");
        return "";
      }
    } catch (Exception ex) {
      logger.error("Exception occurred while calling get method", ex);
      return "";
    } finally {
      if (null != response) {
        try {
          response.close();
        } catch (Exception ex) {
          logger.error("Exception occurred while closing get response object", ex);
        }
      }
    }
  }

  private static void getErrorResponse(CloseableHttpResponse response, String method) {
    try {
      HttpEntity httpEntity = response.getEntity();
      byte[] bytes = EntityUtils.toByteArray(httpEntity);
      StatusLine sl = response.getStatusLine();
      String resp = new String(bytes);
      logger.info(
          "Response from : "
              + method
              + " call "
              + resp
              + " status "
              + sl.getStatusCode()
              + " - "
              + sl.getReasonPhrase());
    } catch (Exception ex) {
      logger.error("Exception occurred while fetching response", ex);
    }
  }

  public static String post(String requestURL, String params, Map<String, String> headers) {
    CloseableHttpResponse response = null;
    try {
      HttpPost httpPost = new HttpPost(requestURL);
      if (MapUtils.isNotEmpty(headers)) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          httpPost.addHeader(entry.getKey(), entry.getValue());
        }
      }
      StringEntity entity = new StringEntity(params);
      httpPost.setEntity(entity);

      response = httpclient.execute(httpPost);
      int status = response.getStatusLine().getStatusCode();
      if (status >= 200 && status < 300) {
        HttpEntity httpEntity = response.getEntity();
        byte[] bytes = EntityUtils.toByteArray(httpEntity);
        StatusLine sl = response.getStatusLine();
        logger.debug(
            "Response from post call : " + sl.getStatusCode() + " - " + sl.getReasonPhrase());
        return new String(bytes);
      } else {
        getErrorResponse(response, "POST");
        return "";
      }
    } catch (Exception ex) {
      logger.error("Exception occurred while calling Post method", ex);
      return "";
    } finally {
      if (null != response) {
        try {
          response.close();
        } catch (Exception ex) {
          logger.error("Exception occurred while closing Post response object", ex);
        }
      }
    }
  }

  public static String postFormData(
      String requestURL, Map<String, String> params, Map<String, String> headers) {
    CloseableHttpResponse response = null;
    try {
      HttpPost httpPost = new HttpPost(requestURL);
      if (MapUtils.isNotEmpty(headers)) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          httpPost.addHeader(entry.getKey(), entry.getValue());
        }
      }

      List<NameValuePair> form = new ArrayList<>();
      for (Map.Entry<String, String> entry : params.entrySet()) {
        form.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
      }
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);

      httpPost.setEntity(entity);

      response = httpclient.execute(httpPost);
      int status = response.getStatusLine().getStatusCode();
      if (status >= 200 && status < 300) {
        HttpEntity httpEntity = response.getEntity();
        byte[] bytes = EntityUtils.toByteArray(httpEntity);
        StatusLine sl = response.getStatusLine();
        logger.debug(
            "Response from post call : " + sl.getStatusCode() + " - " + sl.getReasonPhrase());
        return new String(bytes);
      } else {
        getErrorResponse(response, "POST FORM DATA");
        return "";
      }
    } catch (Exception ex) {
      logger.error("Exception occurred while calling Post method", ex);
      return "";
    } finally {
      if (null != response) {
        try {
          response.close();
        } catch (Exception ex) {
          logger.error("Exception occurred while closing Post response object", ex);
        }
      }
    }
  }

  public static String patch(String requestURL, String params, Map<String, String> headers) {
    CloseableHttpResponse response = null;
    try {
      HttpPatch httpPatch = new HttpPatch(requestURL);
      if (MapUtils.isNotEmpty(headers)) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          httpPatch.addHeader(entry.getKey(), entry.getValue());
        }
      }
      StringEntity entity = new StringEntity(params);
      httpPatch.setEntity(entity);

      response = httpclient.execute(httpPatch);
      int status = response.getStatusLine().getStatusCode();
      if (status >= 200 && status < 300) {
        HttpEntity httpEntity = response.getEntity();
        byte[] bytes = EntityUtils.toByteArray(httpEntity);
        StatusLine sl = response.getStatusLine();
        logger.debug(
            "Response from patch call : " + sl.getStatusCode() + " - " + sl.getReasonPhrase());
        String resp = new String(bytes);
        logger.info("Got response from patch call : " + resp);
        return resp;
      } else {
        getErrorResponse(response, "PATCH");
        return "";
      }
    } catch (Exception ex) {
      logger.error("Exception occurred while calling patch method", ex);
      return "";
    } finally {
      if (null != response) {
        try {
          response.close();
        } catch (Exception ex) {
          logger.error("Exception occurred while closing patch response object", ex);
        }
      }
    }
  }
}
