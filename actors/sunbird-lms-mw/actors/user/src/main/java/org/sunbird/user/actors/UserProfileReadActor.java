package org.sunbird.user.actors;

import static org.sunbird.learner.util.Util.isNotNull;

import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.models.util.datasecurity.EncryptionService;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.UserUtility;
import org.sunbird.learner.util.Util;
import org.sunbird.user.service.UserProfileReadService;
import scala.Tuple2;
import scala.concurrent.Future;

@ActorConfig(
  tasks = {
    "getUserDetailsByLoginId",
    "getUserProfileV3",
    "getUserByKey",
    "checkUserExistence",
    "checkUserExistenceV2"
  },
  asyncTasks = {},
  dispatcher = "most-used-one-dispatcher"
)
public class UserProfileReadActor extends BaseActor {

  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private EncryptionService encryptionService =
      org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.getEncryptionServiceInstance(
          null);
  private ElasticSearchService esUtil = EsClientFactory.getInstance(JsonKey.REST);
  private UserProfileReadService profileReadService = new UserProfileReadService();

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER);
    String operation = request.getOperation();
    switch (operation) {
      case "getUserProfileV3":
        getUserProfileV3(request);
        break;
      case "getUserDetailsByLoginId":
        getUserDetailsByLoginId(request);
        break;
      case "getUserByKey":
        getKey(request);
        break;
      case "checkUserExistence":
        checkUserExistence(request);
        break;
      case "checkUserExistenceV2":
        checkUserExistenceV2(request);
        break;
      default:
        onReceiveUnsupportedOperation("UserProfileReadActor");
    }
  }

  private void getUserProfileV3(Request actorMessage) {
    Response response = profileReadService.getUserProfileData(actorMessage);
    sender().tell(response, self());
  }

  private void getUserDetailsByLoginId(Request actorMessage) {
    actorMessage.toLower();
    Map<String, Object> userMap = actorMessage.getRequest();
    if (null != userMap.get(JsonKey.LOGIN_ID)) {
      String loginId = (String) userMap.get(JsonKey.LOGIN_ID);
      try {
        loginId =
            encryptionService.encryptData(
                (String) userMap.get(JsonKey.LOGIN_ID), actorMessage.getRequestContext());
      } catch (Exception e) {
        logger.error(actorMessage.getRequestContext(), e.getMessage(), e);
        ProjectCommonException exception =
            new ProjectCommonException(
                ResponseCode.userDataEncryptionError.getErrorCode(),
                ResponseCode.userDataEncryptionError.getErrorMessage(),
                ResponseCode.SERVER_ERROR.getResponseCode());
        sender().tell(exception, self());
        return;
      }
      SearchDTO searchDto = new SearchDTO();
      Map<String, Object> filter = new HashMap<>();
      filter.put(JsonKey.LOGIN_ID, loginId);
      searchDto.getAdditionalProperties().put(JsonKey.FILTERS, filter);
      Future<Map<String, Object>> esResponseF =
          esUtil.search(
              searchDto, ProjectUtil.EsType.user.getTypeName(), actorMessage.getRequestContext());
      Map<String, Object> esResponse =
          (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(esResponseF);
      List<Map<String, Object>> userList =
          (List<Map<String, Object>>) esResponse.get(JsonKey.CONTENT);
      Map<String, Object> result = null;
      if (null != userList && !userList.isEmpty()) {
        result = userList.get(0);
      } else {
        throw new ProjectCommonException(
            ResponseCode.userNotFound.getErrorCode(),
            ResponseCode.userNotFound.getErrorMessage(),
            ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
      }
      // String username = ssoManager.getUsernameById((String) result.get(JsonKey.USER_ID));
      // result.put(JsonKey.USERNAME, username);
      sendResponse(actorMessage, result);

    } else {
      ProjectCommonException exception =
          new ProjectCommonException(
              ResponseCode.userNotFound.getErrorCode(),
              ResponseCode.userNotFound.getErrorMessage(),
              ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
      sender().tell(exception, self());
      return;
    }
  }

  private void sendResponse(Request actorMessage, Map<String, Object> result) {
    if (result == null || result.size() == 0) {
      throw new ProjectCommonException(
          ResponseCode.userNotFound.getErrorCode(),
          ResponseCode.userNotFound.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }

    // check whether is_deletd true or false
    if (ProjectUtil.isNotNull(result)
        && result.containsKey(JsonKey.IS_DELETED)
        && ProjectUtil.isNotNull(result.get(JsonKey.IS_DELETED))
        && (Boolean) result.get(JsonKey.IS_DELETED)) {
      throw new ProjectCommonException(
          ResponseCode.userAccountlocked.getErrorCode(),
          ResponseCode.userAccountlocked.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    Future<Map<String, Object>> future =
        fetchRootAndRegisterOrganisation(result, actorMessage.getRequestContext());
    Future<Response> response =
        future.map(
            new Mapper<Map<String, Object>, Response>() {
              @Override
              public Response apply(Map<String, Object> responseMap) {
                logger.info(
                    actorMessage.getRequestContext(),
                    "UserProfileReadActor:handle user profile read async call ");
                result.put(JsonKey.ROOT_ORG, responseMap);
                Response response = new Response();
                handleUserCallAsync(result, response, actorMessage);
                return response;
              }
            },
            getContext().dispatcher());
    Patterns.pipe(response, getContext().dispatcher()).to(sender());
  }

  private void handleUserCallAsync(
      Map<String, Object> result, Response response, Request actorMessage) {
    // having check for removing private filed from user , if call user and response
    // user data id is not same.
    String requestedById =
        (String) actorMessage.getContext().getOrDefault(JsonKey.REQUESTED_BY, "");
    logger.info(
        actorMessage.getRequestContext(),
        "requested By and requested user id == "
            + requestedById
            + "  "
            + (String) result.get(JsonKey.USER_ID));

    try {
      if (!(((String) result.get(JsonKey.USER_ID)).equalsIgnoreCase(requestedById))) {
        result = removeUserPrivateField(result);
      } else {
        // fetch user external identity
        List<Map<String, String>> dbResExternalIds =
            profileReadService.fetchUserExternalIdentity(
                requestedById, result, true, actorMessage.getRequestContext());
        result.put(JsonKey.EXTERNAL_IDS, dbResExternalIds);
      }
    } catch (Exception e) {
      logger.error(actorMessage.getRequestContext(), e.getMessage(), e);
      ProjectCommonException exception =
          new ProjectCommonException(
              ResponseCode.userDataEncryptionError.getErrorCode(),
              ResponseCode.userDataEncryptionError.getErrorMessage(),
              ResponseCode.SERVER_ERROR.getResponseCode());
      sender().tell(exception, self());
      return;
    }

    if (null != result) {
      // remove email and phone no from response
      result.remove(JsonKey.ENC_EMAIL);
      result.remove(JsonKey.ENC_PHONE);
      result.remove(JsonKey.MISSING_FIELDS);
      result.remove(JsonKey.COMPLETENESS);
      profileReadService.updateTnc(result);
      if (null != actorMessage.getRequest().get(JsonKey.FIELDS)) {
        List<String> requestFields = (List<String>) actorMessage.getRequest().get(JsonKey.FIELDS);
        if (CollectionUtils.isNotEmpty(requestFields)) {
          profileReadService.addExtraFieldsInUserProfileResponse(
              result, String.join(",", requestFields), actorMessage.getRequestContext());
        }
      }
      response.put(JsonKey.RESPONSE, result);
      UserUtility.decryptUserDataFrmES(result);
    } else {
      result = new HashMap<>();
      response.put(JsonKey.RESPONSE, result);
    }
  }

  private Map<String, Object> removeUserPrivateField(Map<String, Object> responseMap) {
    logger.info("Start removing User private field==");
    for (int i = 0; i < ProjectUtil.excludes.length; i++) {
      responseMap.remove(ProjectUtil.excludes[i]);
    }
    logger.info("All private filed removed=");
    return responseMap;
  }

  private Future<Map<String, Object>> fetchRootAndRegisterOrganisation(
      Map<String, Object> result, RequestContext context) {
    try {
      if (isNotNull(result.get(JsonKey.ROOT_ORG_ID))) {
        String rootOrgId = (String) result.get(JsonKey.ROOT_ORG_ID);
        return esUtil.getDataByIdentifier(
            ProjectUtil.EsType.organisation.getTypeName(), rootOrgId, context);
      }
    } catch (Exception ex) {
      logger.error(context, ex.getMessage(), ex);
    }
    return null;
  }

  private Future<Response> checkUserExists(Request request, boolean isV1) {
    Future<Map<String, Object>> userFuture;
    String key = (String) request.get(JsonKey.KEY);
    if (JsonKey.PHONE.equalsIgnoreCase(key)
        || JsonKey.EMAIL.equalsIgnoreCase(key)
        || JsonKey.USERNAME.equalsIgnoreCase(key)) {
      String value = (String) request.get(JsonKey.VALUE);
      String userId =
          getUserIdByUserLookUp(
              key.toLowerCase(), StringUtils.lowerCase(value), request.getRequestContext());
      if (StringUtils.isBlank(userId)) {
        return Futures.future(
            () -> {
              Response resp = new Response();
              resp.put(JsonKey.EXISTS, false);
              return resp;
            },
            getContext().dispatcher());
      }
      userFuture =
          esUtil.getDataByIdentifier(
              EsType.user.getTypeName(), userId, request.getRequestContext());
      return userFuture.map(
          new Mapper<Map<String, Object>, Response>() {
            @Override
            public Response apply(Map<String, Object> response) {
              Response resp = new Response();
              resp.put(JsonKey.EXISTS, true);
              if (!isV1) {
                resp.put(JsonKey.ID, response.get(JsonKey.USER_ID));
                String name = (String) response.get(JsonKey.FIRST_NAME);
                if (StringUtils.isNotEmpty((String) response.get(JsonKey.LAST_NAME))) {
                  name += " " + response.get(JsonKey.LAST_NAME);
                }
                resp.put(JsonKey.NAME, name);
              }
              String logMsg = String.format("userExists %s ", request.get(JsonKey.VALUE));
              logger.info(request.getRequestContext(), logMsg);
              return resp;
            }
          },
          getContext().dispatcher());

    } else {
      userFuture = userSearchDetails(request);
      return userFuture.map(
          new Mapper<Map<String, Object>, Response>() {
            @Override
            public Response apply(Map<String, Object> responseMap) {
              List<Map<String, Object>> respList = (List) responseMap.get(JsonKey.CONTENT);
              long size = respList.size();
              boolean isExists = (size > 0);

              Response resp = new Response();
              resp.put(JsonKey.EXISTS, isExists);

              if (isExists && !isV1) {
                Map<String, Object> response = respList.get(0);
                resp.put(JsonKey.EXISTS, true);
                resp.put(JsonKey.ID, response.get(JsonKey.USER_ID));
                String name = (String) response.get(JsonKey.FIRST_NAME);
                if (StringUtils.isNotEmpty((String) response.get(JsonKey.LAST_NAME))) {
                  name += " " + response.get(JsonKey.LAST_NAME);
                }
                resp.put(JsonKey.NAME, name);
              }

              String logMsg =
                  String.format(
                      "userExists %s results size = %d", request.get(JsonKey.VALUE), size);
              logger.info(request.getRequestContext(), logMsg);
              return resp;
            }
          },
          getContext().dispatcher());
    }
  }

  private void checkUserExistence(Request request) {
    Future<Response> userResponse = checkUserExists(request, true);
    Patterns.pipe(userResponse, getContext().dispatcher()).to(sender());
  }

  private void checkUserExistenceV2(Request request) {
    Future<Response> userResponse = checkUserExists(request, false);
    Patterns.pipe(userResponse, getContext().dispatcher()).to(sender());
  }

  private Future<Map<String, Object>> userSearchDetails(Request request) {
    Map<String, Object> searchMap = new WeakHashMap<>();
    String value = (String) request.get(JsonKey.VALUE);
    String encryptedValue = null;
    try {
      encryptedValue =
          encryptionService.encryptData(StringUtils.lowerCase(value), request.getRequestContext());
    } catch (Exception var11) {
      throw new ProjectCommonException(
          ResponseCode.userDataEncryptionError.getErrorCode(),
          ResponseCode.userDataEncryptionError.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    searchMap.put((String) request.get(JsonKey.KEY), encryptedValue);
    logger.info(
        request.getRequestContext(),
        "UserProfileReadActor:checkUserExistence: search map prepared " + searchMap);
    SearchDTO searchDTO = new SearchDTO();
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, searchMap);
    Future<Map<String, Object>> esFuture =
        esUtil.search(searchDTO, EsType.user.getTypeName(), request.getRequestContext());
    return esFuture;
  }

  private String getUserIdByUserLookUp(String type, String value, RequestContext context) {
    try {
      value = encryptionService.encryptData(value, context);
    } catch (Exception e) {
      logger.info(context, "Exception occurred while encrypting email/phone " + e);
    }
    Util.DbInfo userLookUp = Util.dbInfoMap.get(JsonKey.USER_LOOKUP);
    Map<String, Object> reqMap = new HashMap<>();
    reqMap.put(JsonKey.TYPE, type);
    reqMap.put(JsonKey.VALUE, value);
    Response response =
        cassandraOperation.getRecordsByCompositeKey(
            userLookUp.getKeySpace(), userLookUp.getTableName(), reqMap, context);
    List<Map<String, Object>> userMapList =
        (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isNotEmpty(userMapList)) {
      Map<String, Object> userMap = userMapList.get(0);
      return (String) userMap.get(JsonKey.USER_ID);
    }
    return "";
  }

  private void getKey(Request actorMessage) {
    String key = (String) actorMessage.getRequest().get(JsonKey.KEY);
    String value = (String) actorMessage.getRequest().get(JsonKey.VALUE);
    if (JsonKey.LOGIN_ID.equalsIgnoreCase(key)
        || JsonKey.EMAIL.equalsIgnoreCase(key)
        || JsonKey.USERNAME.equalsIgnoreCase(key)) {
      value = value.toLowerCase();
    }
    Future<Map<String, Object>> userResponse;
    Future<Map<String, Object>> futureResponse;
    if (JsonKey.PHONE.equalsIgnoreCase(key)
        || JsonKey.EMAIL.equalsIgnoreCase(key)
        || JsonKey.USERNAME.equalsIgnoreCase(key)) {
      String userId =
          getUserIdByUserLookUp(key.toLowerCase(), value, actorMessage.getRequestContext());
      if (StringUtils.isBlank(userId)) {
        isUserExists(new HashMap<>());
      }
      futureResponse =
          esUtil.getDataByIdentifier(
              EsType.user.getTypeName(), userId, actorMessage.getRequestContext());
      userResponse =
          futureResponse.map(
              new Mapper<Map<String, Object>, Map<String, Object>>() {
                @Override
                public Map<String, Object> apply(Map<String, Object> userMap) {
                  isUserExists(userMap);
                  userMap.put(JsonKey.EMAIL, userMap.get(JsonKey.MASKED_EMAIL));
                  userMap.put(JsonKey.PHONE, userMap.get(JsonKey.MASKED_PHONE));
                  isUserAccountDeleted(userMap);
                  return userMap;
                }
              },
              getContext().dispatcher());

    } else {
      String encryptedValue = null;
      try {
        encryptedValue = encryptionService.encryptData(value, actorMessage.getRequestContext());
      } catch (Exception e) {
        ProjectCommonException exception =
            new ProjectCommonException(
                ResponseCode.userDataEncryptionError.getErrorCode(),
                ResponseCode.userDataEncryptionError.getErrorMessage(),
                ResponseCode.SERVER_ERROR.getResponseCode());
        sender().tell(exception, self());
        return;
      }
      Map<String, Object> searchMap = new WeakHashMap<>();
      searchMap.put(key, encryptedValue);
      SearchDTO searchDTO = new SearchDTO();
      searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, searchMap);

      futureResponse =
          esUtil.search(searchDTO, EsType.user.getTypeName(), actorMessage.getRequestContext());

      userResponse =
          futureResponse.map(
              new Mapper<Map<String, Object>, Map<String, Object>>() {
                @Override
                public Map<String, Object> apply(Map<String, Object> responseMap) {
                  logger.info(
                      actorMessage.getRequestContext(),
                      "SearchHandlerActor:handleUserSearchAsyncRequest user search call ");
                  List<Map<String, Object>> respList = (List) responseMap.get(JsonKey.CONTENT);
                  isUserExists(respList);
                  Map<String, Object> userMap = respList.get(0);
                  userMap.put(JsonKey.EMAIL, userMap.get(JsonKey.MASKED_EMAIL));
                  userMap.put(JsonKey.PHONE, userMap.get(JsonKey.MASKED_PHONE));
                  isUserAccountDeleted(userMap);
                  return userMap;
                }
              },
              getContext().dispatcher());
    }

    handleUserSearchAsyncRequest(userResponse, actorMessage);
  }

  private void handleUserSearchAsyncRequest(
      Future<Map<String, Object>> userResponse, Request actorMessage) {
    Future<Object> orgResponse =
        userResponse.map(
            new Mapper<Map<String, Object>, Object>() {
              @Override
              public Object apply(Map<String, Object> parameter) {
                Map<String, Object> esOrgMap = new HashMap<>();
                esOrgMap =
                    (Map<String, Object>)
                        ElasticSearchHelper.getResponseFromFuture(
                            fetchRootAndRegisterOrganisation(
                                parameter, actorMessage.getRequestContext()));
                return esOrgMap;
              }
            },
            getContext().dispatcher());

    Future<Map<String, Object>> userOrgResponse =
        userResponse
            .zip(orgResponse)
            .map(
                new Mapper<Tuple2<Map<String, Object>, Object>, Map<String, Object>>() {
                  @Override
                  public Map<String, Object> apply(Tuple2<Map<String, Object>, Object> parameter) {
                    Map<String, Object> userMap = parameter._1;
                    userMap.put(JsonKey.ROOT_ORG, (Map<String, Object>) parameter._2);
                    userMap.remove(JsonKey.ENC_EMAIL);
                    userMap.remove(JsonKey.ENC_PHONE);
                    String requestedById =
                        (String) actorMessage.getContext().getOrDefault(JsonKey.REQUESTED_BY, "");
                    if (!(((String) userMap.get(JsonKey.USER_ID))
                        .equalsIgnoreCase(requestedById))) {
                      userMap = removeUserPrivateField(userMap);
                    }
                    return userMap;
                  }
                },
                getContext().dispatcher());

    Future<Map<String, Object>> externalIdFuture =
        userResponse.map(
            new Mapper<Map<String, Object>, Map<String, Object>>() {
              @Override
              public Map<String, Object> apply(Map<String, Object> result) {
                Map<String, Object> extIdMap = new HashMap<>();
                String requestedById =
                    (String) actorMessage.getContext().getOrDefault(JsonKey.REQUESTED_BY, "");
                if (((String) result.get(JsonKey.USER_ID)).equalsIgnoreCase(requestedById)) {
                  List<Map<String, String>> dbResExternalIds =
                      profileReadService.fetchUserExternalIdentity(
                          (String) result.get(JsonKey.USER_ID),
                          result,
                          true,
                          actorMessage.getRequestContext());
                  extIdMap.put(JsonKey.EXTERNAL_IDS, dbResExternalIds);
                  return extIdMap;
                }
                return extIdMap;
              }
            },
            getContext().dispatcher());

    Future<Map<String, Object>> tncFuture =
        userOrgResponse.map(
            new Mapper<Map<String, Object>, Map<String, Object>>() {
              @Override
              public Map<String, Object> apply(Map<String, Object> result) {
                profileReadService.updateTnc(result);
                result.remove(JsonKey.MISSING_FIELDS);
                result.remove(JsonKey.COMPLETENESS);
                if (null != actorMessage.getRequest().get(JsonKey.FIELDS)) {
                  List<String> requestFields =
                      (List<String>) actorMessage.getRequest().get(JsonKey.FIELDS);
                  if (CollectionUtils.isNotEmpty(requestFields)) {
                    profileReadService.addExtraFieldsInUserProfileResponse(
                        result, String.join(",", requestFields), actorMessage.getRequestContext());
                  }
                }
                UserUtility.decryptUserDataFrmES(result);
                return result;
              }
            },
            getContext().dispatcher());

    Future<Response> sumFuture =
        externalIdFuture
            .zip(tncFuture)
            .map(
                new Mapper<Tuple2<Map<String, Object>, Map<String, Object>>, Response>() {
                  @Override
                  public Response apply(
                      Tuple2<Map<String, Object>, Map<String, Object>> parameter) {
                    Map<String, Object> externalIdMap = parameter._1;
                    logger.info(
                        actorMessage.getRequestContext(), "the externalId map is " + externalIdMap);
                    Map<String, Object> tncMap = parameter._2;
                    tncMap.putAll(externalIdMap);
                    Response response = new Response();
                    response.put(JsonKey.RESPONSE, tncMap);
                    return response;
                  }
                },
                getContext().dispatcher());

    Patterns.pipe(sumFuture, getContext().dispatcher()).to(sender());
  }

  private void isUserAccountDeleted(Map<String, Object> responseMap) {
    if (BooleanUtils.isTrue((Boolean) responseMap.get(JsonKey.IS_DELETED))) {
      throw new ProjectCommonException(
          ResponseCode.userAccountlocked.getErrorCode(),
          ResponseCode.userAccountlocked.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private void isUserExists(List<Map<String, Object>> respList) {
    if (null == respList || respList.size() == 0) {
      throw new ProjectCommonException(
          ResponseCode.userNotFound.getErrorCode(),
          ResponseCode.userNotFound.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
  }

  private void isUserExists(Map<String, Object> respMap) {
    if (MapUtils.isEmpty(respMap)) {
      throw new ProjectCommonException(
          ResponseCode.userNotFound.getErrorCode(),
          ResponseCode.userNotFound.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
  }
}
