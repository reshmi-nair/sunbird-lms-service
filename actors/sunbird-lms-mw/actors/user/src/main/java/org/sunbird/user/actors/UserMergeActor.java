package org.sunbird.user.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.actorutil.systemsettings.SystemSettingClient;
import org.sunbird.actorutil.systemsettings.impl.SystemSettingClientImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.models.util.datasecurity.OneWayHashing;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.responsecode.ResponseMessage;
import org.sunbird.common.util.ConfigUtil;
import org.sunbird.kafka.client.KafkaClient;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.learner.util.Util;
import org.sunbird.models.systemsetting.SystemSetting;
import org.sunbird.models.user.User;
import org.sunbird.services.sso.SSOManager;
import org.sunbird.services.sso.SSOServiceFactory;
import org.sunbird.telemetry.dto.Actor;
import org.sunbird.telemetry.dto.Context;
import org.sunbird.telemetry.dto.Target;
import org.sunbird.telemetry.dto.Telemetry;
import org.sunbird.user.dao.UserDao;
import org.sunbird.user.dao.impl.UserDaoImpl;
import org.sunbird.user.service.UserService;
import org.sunbird.user.service.impl.UserMergeServiceImpl;
import org.sunbird.user.service.impl.UserServiceImpl;
import org.sunbird.user.util.KafkaConfigConstants;
import org.sunbird.user.util.UserUtil;

@ActorConfig(
  tasks = {"mergeUser"},
  asyncTasks = {}
)
public class UserMergeActor extends UserBaseActor {
  String topic = null;
  Producer<String, String> producer = null;
  ObjectMapper objectMapper = new ObjectMapper();
  private UserService userService = UserServiceImpl.getInstance();
  private SSOManager keyCloakService = SSOServiceFactory.getInstance();
  private SystemSettingClient systemSettingClient = SystemSettingClientImpl.getInstance();

  @Override
  public void onReceive(Request userRequest) throws Throwable {
    Util.initializeContext(userRequest, TelemetryEnvKey.USER);
    if (producer == null) {
      initKafkaClient();
    }
    updateUserMergeDetails(userRequest);
  }

  /**
   * Main method for calling user-course service, merge user details and then call user-cert service
   *
   * @param userRequest
   * @throws IOException
   */
  private void updateUserMergeDetails(Request userRequest) throws IOException {
    logger.info(
        userRequest.getRequestContext(), "UserMergeActor:updateUserMergeDetails: starts : ");
    Response response = new Response();
    Map mergeeDBMap = new HashMap<String, Object>();
    HashMap requestMap = (HashMap) userRequest.getRequest();
    RequestContext context = userRequest.getRequestContext();
    Map userCertMap = (Map) requestMap.clone();
    Map headers = (Map) userRequest.getContext().get(JsonKey.HEADER);
    String mergeeId = (String) requestMap.get(JsonKey.FROM_ACCOUNT_ID);
    String mergerId = (String) requestMap.get(JsonKey.TO_ACCOUNT_ID);
    // validating tokens
    checkTokenDetails(headers, mergeeId, mergerId, userRequest.getRequestContext());
    Map telemetryMap = (HashMap) requestMap.clone();
    User mergee = userService.getUserById(mergeeId, userRequest.getRequestContext());
    User merger = userService.getUserById(mergerId, userRequest.getRequestContext());
    String custodianId = getCustodianValue(userRequest.getRequestContext());
    if ((!custodianId.equals(mergee.getRootOrgId())) || custodianId.equals(merger.getRootOrgId())) {
      logger.info(
          userRequest.getRequestContext(),
          "UserMergeActor:updateUserMergeDetails: Either custodian id is not matching with mergeeid root-org"
              + mergeeId
              + "or matching with mergerid root-org"
              + mergerId);
      throw new ProjectCommonException(
          ResponseCode.accountNotFound.getErrorCode(),
          ResponseCode.accountNotFound.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (!mergee.getIsDeleted()) {
      prepareMergeeAccountData(mergee, mergeeDBMap);
      userRequest.put(JsonKey.USER_MERGEE_ACCOUNT, mergeeDBMap);
      UserDao userDao = UserDaoImpl.getInstance();
      Response mergeeResponse = userDao.updateUser(mergeeDBMap, userRequest.getRequestContext());
      List<String> userLookUpIdentifiers =
          Stream.of(JsonKey.EMAIL, JsonKey.PHONE, JsonKey.USERNAME).collect(Collectors.toList());
      UserUtil.removeEntryFromUserLookUp(
          objectMapper.convertValue(mergee, Map.class), userLookUpIdentifiers, context);
      String mergeeResponseStr = (String) mergeeResponse.get(JsonKey.RESPONSE);
      logger.info(
          userRequest.getRequestContext(),
          "UserMergeActor: updateUserMergeDetails: mergeeResponseStr = " + mergeeResponseStr);
      Map result = new HashMap<String, Object>();
      result.put(JsonKey.STATUS, JsonKey.SUCCESS);
      response.put(JsonKey.RESULT, result);
      sender().tell(response, self());

      // update user-course-cert details
      mergeCertCourseDetails(mergee, merger, userRequest.getRequestContext());

      // update mergee details in ES
      mergeUserDetailsToEs(userRequest);

      // deleting User From KeyCloak
      CompletableFuture.supplyAsync(
              () -> {
                return deactivateMergeeFromKC(
                    (String) mergeeDBMap.get(JsonKey.ID), userRequest.getRequestContext());
              })
          .thenApply(
              status -> {
                logger.info(
                    userRequest.getRequestContext(),
                    "UserMergeActor: updateUserMergeDetails: user deleted from KeyCloak: "
                        + status);
                return null;
              });

      // create telemetry event for merge
      logger.info("UserMergeActor:triggerUserMergeTelemetry: generating telemetry event for merge");
      new UserMergeServiceImpl()
          .triggerUserMergeTelemetry(telemetryMap, merger, userRequest.getContext());

    } else {
      logger.info("UserMergeActor:updateUserMergeDetails: User mergee is not exist : " + mergeeId);
      throw new ProjectCommonException(
          ResponseCode.invalidIdentifier.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseMessage.Message.INVALID_PARAMETER_VALUE, mergeeId, JsonKey.FROM_ACCOUNT_ID),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
  }

  /**
   * This method returns system custodian value
   *
   * @return rootCustodianValue
   */
  private String getCustodianValue(RequestContext context) {
    String custodianId = null;
    try {
      Map<String, String> configSettingMap = DataCacheHandler.getConfigSettings();
      custodianId = configSettingMap.get(JsonKey.CUSTODIAN_ORG_ID);
      if (custodianId == null || custodianId.isEmpty()) {
        SystemSetting custodianIdSetting =
            systemSettingClient.getSystemSettingByField(
                getActorRef(ActorOperations.GET_SYSTEM_SETTING.getValue()),
                JsonKey.CUSTODIAN_ORG_ID,
                context);
        if (custodianIdSetting != null) {
          configSettingMap.put(custodianIdSetting.getId(), custodianIdSetting.getValue());
          custodianId = custodianIdSetting.getValue();
        }
      }
    } catch (Exception e) {
      logger.error(
          context,
          "UserMergeActor:updateTncInfo: Exception occurred while getting system setting for"
              + JsonKey.CUSTODIAN_ORG_ID
              + e.getMessage(),
          e);
    }
    return custodianId;
  }

  /**
   * This method creates Kafka topic for user-cert
   *
   * @param mergee
   * @param merger
   * @throws IOException
   */
  private void mergeCertCourseDetails(User mergee, User merger, RequestContext context)
      throws IOException {
    String content = null;
    Telemetry userCertMergeRequest = createAccountMergeTopicData(mergee, merger);

    content = objectMapper.writeValueAsString(userCertMergeRequest);
    logger.info(context, "UserMergeActor:mergeCertCourseDetails: Kafka producer topic::" + content);
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, content);
    if (producer != null) {
      producer.send(record);
    } else {
      logger.info(
          context, "UserMergeActor:mergeCertCourseDetails: Kafka producer is not initialised.");
    }
  }

  private Telemetry createAccountMergeTopicData(User mergee, User merger) {
    Map<String, Object> edata = new HashMap<>();
    Telemetry mergeUserEvent = new Telemetry();
    Actor actor = new Actor();
    actor.setId(JsonKey.TELEMETRY_ACTOR_USER_MERGE_ID);
    actor.setType(JsonKey.SYSTEM);
    mergeUserEvent.setActor(actor);
    mergeUserEvent.setEid(JsonKey.BE_JOB_REQUEST);
    edata.put(JsonKey.ACTION, JsonKey.TELEMETRY_EDATA_USER_MERGE_ACTION);
    edata.put(JsonKey.FROM_ACCOUNT_ID, mergee.getId());
    edata.put(JsonKey.TO_ACCOUNT_ID, merger.getId());
    edata.put(JsonKey.ROOT_ORG_ID, merger.getRootOrgId());
    edata.put(JsonKey.ITERATION, 1);
    mergeUserEvent.setEdata(edata);
    Context context = new Context();
    org.sunbird.telemetry.dto.Producer dataProducer = new org.sunbird.telemetry.dto.Producer();
    dataProducer.setVer("1.0");
    dataProducer.setId(JsonKey.TELEMETRY_PRODUCER_USER_MERGE_ID);
    context.setPdata(dataProducer);
    mergeUserEvent.setContext(context);
    Target target = new Target();
    target.setId(OneWayHashing.encryptVal(mergee.getId() + "_" + merger.getId()));
    target.setType(JsonKey.TELEMETRY_TARGET_USER_MERGE_TYPE);
    mergeUserEvent.setObject(target);
    return mergeUserEvent;
  }

  private void mergeUserDetailsToEs(Request userRequest) {
    userRequest.setOperation(ActorOperations.MERGE_USER_TO_ELASTIC.getValue());
    logger.info(
        userRequest.getRequestContext(),
        "UserMergeActor: mergeUserDetailsToEs: Trigger sync of user details to ES for user id"
            + userRequest.getRequest().get(JsonKey.FROM_ACCOUNT_ID));
    tellToAnother(userRequest);
  }

  private void prepareMergeeAccountData(User mergee, Map mergeeDBMap) {
    mergeeDBMap.put(JsonKey.STATUS, 0);
    mergeeDBMap.put(JsonKey.IS_DELETED, true);
    mergeeDBMap.put(JsonKey.EMAIL, null);
    mergeeDBMap.put(JsonKey.PHONE, null);
    mergeeDBMap.put(JsonKey.USERNAME, null);
    mergeeDBMap.put(JsonKey.PREV_USED_EMAIL, mergee.getEmail());
    mergeeDBMap.put(JsonKey.PREV_USED_PHONE, mergee.getPhone());
    mergeeDBMap.put(JsonKey.UPDATED_DATE, ProjectUtil.getFormattedDate());
    mergeeDBMap.put(JsonKey.ID, mergee.getId());
  }

  private void checkTokenDetails(
      Map headers, String mergeeId, String mergerId, RequestContext context) {
    String userAuthToken = (String) headers.get(JsonKey.X_AUTHENTICATED_USER_TOKEN);
    String sourceUserAuthToken = (String) headers.get(JsonKey.X_SOURCE_USER_TOKEN);
    String subDomainUrl = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_SUBDOMAIN_KEYCLOAK_BASE_URL);
    logger.info(context, "UserMergeActor:checkTokenDetails sub domain url value " + subDomainUrl);
    String userId = keyCloakService.verifyToken(userAuthToken, context);
    // Since source token is generated from subdomain , so verification also need with
    // same subdomain.
    String sourceUserId = keyCloakService.verifyToken(sourceUserAuthToken, subDomainUrl, context);
    if (!(mergeeId.equals(sourceUserId) && mergerId.equals(userId))) {
      throw new ProjectCommonException(
          ResponseCode.unAuthorized.getErrorCode(),
          ProjectUtil.formatMessage(ResponseMessage.Message.UNAUTHORIZED_USER, mergeeId),
          ResponseCode.UNAUTHORIZED.getResponseCode());
    }
  }

  /** Initialises Kafka producer required for dispatching messages on Kafka. */
  private void initKafkaClient() {
    logger.info("UserMergeActor:initKafkaClient: starts = ");
    Config config = ConfigUtil.getConfig();
    topic = config.getString(KafkaConfigConstants.SUNBIRD_USER_CERT_KAFKA_TOPIC);
    logger.info("UserMergeActor:initKafkaClient: topic = " + topic);
    try {
      producer = KafkaClient.getProducer();
    } catch (Exception e) {
      logger.error("UserMergeActor:initKafkaClient: An exception occurred." + e.getMessage(), e);
    }
  }

  private String deactivateMergeeFromKC(String userId, RequestContext context) {
    Map<String, Object> userMap = new HashMap<>();
    userMap.put(JsonKey.USER_ID, userId);
    logger.info(
        context,
        "UserMergeActor:deactivateMergeeFromKC: request Got to deactivate mergee account from KC:"
            + userMap);
    return keyCloakService.removeUser(userMap, context);
  }
}
