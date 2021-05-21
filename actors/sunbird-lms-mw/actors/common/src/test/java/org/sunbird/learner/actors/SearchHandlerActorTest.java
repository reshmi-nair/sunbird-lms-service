package org.sunbird.learner.actors;

import static akka.testkit.JavaTestKit.duration;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.javadsl.TestKit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.actor.service.BaseMWService;
import org.sunbird.actor.service.SunbirdMWService;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.search.SearchHandlerActor;
import scala.concurrent.Promise;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  ElasticSearchRestHighImpl.class,
  EsClientFactory.class,
  SunbirdMWService.class,
  BaseMWService.class
})
@PowerMockIgnore({
  "javax.management.*",
  "javax.net.ssl.*",
  "javax.security.*",
  "jdk.internal.reflect.*",
  "javax.crypto.*"
})
public class SearchHandlerActorTest {

  private static ActorSystem system;
  private static final Props props = Props.create(SearchHandlerActor.class);

  @BeforeClass
  public static void setUp() {
    system = ActorSystem.create("system");
  }

  private static Map<String, Object> createResponseGet(boolean isResponseRequired) {
    HashMap<String, Object> response = new HashMap<>();
    List<Map<String, Object>> content = new ArrayList<>();
    HashMap<String, Object> innerMap = new HashMap<>();
    List<Map<String, Object>> orgList = new ArrayList<>();
    Map<String, Object> orgMap = new HashMap<>();
    orgMap.put(JsonKey.ORGANISATION_ID, "anyOrgId");

    innerMap.put(JsonKey.ROOT_ORG_ID, "anyRootOrgId");
    innerMap.put(JsonKey.ORGANISATIONS, orgList);
    innerMap.put(JsonKey.HASHTAGID, "HASHTAGID");
    innerMap.put(JsonKey.ORGANISATION_TYPE, 2);
    Map<String, Object> userType = new HashMap<>();
    userType.put(JsonKey.TYPE, "type");
    userType.put(JsonKey.SUB_TYPE, "subType");
    innerMap.put(JsonKey.PROFILE_USERTYPE, userType);
    List<Map<String, String>> locList = new ArrayList<>();
    Map<String, String> locn = new HashMap<>();
    locn.put(JsonKey.ID, "456465464");
    locn.put(JsonKey.TYPE, "type");
    locList.add(locn);
    innerMap.put(JsonKey.PROFILE_LOCATION, locList);
    content.add(innerMap);
    response.put(JsonKey.CONTENT, content);
    return response;
  }

  @Test
  public void searchOrg() {
    PowerMockito.mockStatic(EsClientFactory.class);
    ElasticSearchService esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    Promise<Map<String, Object>> promise = Futures.promise();
    promise.success(createResponseGet(true));
    when(esService.search(Mockito.any(), Mockito.anyString(), Mockito.any()))
        .thenReturn(promise.future());

    PowerMockito.mockStatic(SunbirdMWService.class);
    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    PowerMockito.mockStatic(BaseMWService.class);
    BaseMWService.getRemoteRouter(Mockito.anyString());
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.ORG_SEARCH.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.QUERY, "");
    Map<String, Object> filters = new HashMap<>();
    List<String> objectType = new ArrayList<String>();
    objectType.add("org");
    filters.put(JsonKey.ID, "ORG_001");
    filters.put(JsonKey.IS_SCHOOL, true);
    filters.put(JsonKey.IS_ROOT_ORG, true);
    innerMap.put(JsonKey.FILTERS, filters);
    innerMap.put(JsonKey.LIMIT, 1);
    List<String> fields = new ArrayList<>();
    fields.add(JsonKey.ORG_NAME);
    fields.add(JsonKey.HASHTAGID);
    innerMap.put(JsonKey.FIELDS, fields);
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put(JsonKey.FIELDS, JsonKey.ORG_NAME);
    reqObj.setContext(contextMap);
    reqObj.setRequest(innerMap);
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void searchUser() {
    PowerMockito.mockStatic(EsClientFactory.class);
    ElasticSearchService esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    Promise<Map<String, Object>> promise = Futures.promise();
    promise.success(createResponseGet(true));
    when(esService.search(Mockito.any(), Mockito.anyString(), Mockito.any()))
        .thenReturn(promise.future());

    PowerMockito.mockStatic(SunbirdMWService.class);
    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    PowerMockito.mockStatic(BaseMWService.class);
    BaseMWService.getRemoteRouter(Mockito.anyString());
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.USER_SEARCH.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.QUERY, "");
    Map<String, Object> filters = new HashMap<>();
    List<String> objectType = new ArrayList<String>();
    objectType.add("user");
    filters.put(JsonKey.OBJECT_TYPE, objectType);
    filters.put(JsonKey.PROFILE_USERTYPE, "teacher");
    filters.put(JsonKey.PROFILE_LOCATION, "location");
    filters.put(JsonKey.USER_TYPE, "userType");
    filters.put(JsonKey.USER_SUB_TYPE, "userSubType");
    filters.put(JsonKey.LOCATION_ID, "locationID");
    filters.put(JsonKey.LOCATION_TYPE, "type");
    filters.put(JsonKey.ROOT_ORG_ID, "ORG_001");
    innerMap.put(JsonKey.FILTERS, filters);
    innerMap.put(JsonKey.LIMIT, 1);
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put(JsonKey.FIELDS, JsonKey.ORG_NAME);
    reqObj.setContext(contextMap);
    reqObj.setRequest(innerMap);
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void searchUser2() {
    PowerMockito.mockStatic(EsClientFactory.class);
    ElasticSearchService esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    Promise<Map<String, Object>> promise = Futures.promise();
    Map<String, Object> resp = createResponseGet(true);
    List<Map<String, Object>> content = (List<Map<String, Object>>) resp.get(JsonKey.CONTENT);
    content.get(0).put(JsonKey.PROFILE_USERTYPE, null);
    content.get(0).put(JsonKey.PROFILE_LOCATION, null);
    promise.success(resp);
    when(esService.search(Mockito.any(), Mockito.anyString(), Mockito.any()))
        .thenReturn(promise.future());

    PowerMockito.mockStatic(SunbirdMWService.class);
    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    PowerMockito.mockStatic(BaseMWService.class);
    BaseMWService.getRemoteRouter(Mockito.anyString());
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.USER_SEARCH.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.QUERY, "");
    Map<String, Object> filters = new HashMap<>();
    List<String> objectType = new ArrayList<String>();
    objectType.add("user");
    filters.put(JsonKey.OBJECT_TYPE, objectType);
    filters.put(JsonKey.PROFILE_USERTYPE, "teacher");
    filters.put(JsonKey.PROFILE_LOCATION, "location");
    filters.put(JsonKey.USER_TYPE, "userType");
    filters.put(JsonKey.USER_SUB_TYPE, "userSubType");
    filters.put(JsonKey.LOCATION_ID, "locationID");
    filters.put(JsonKey.LOCATION_TYPE, "type");
    filters.put(JsonKey.ROOT_ORG_ID, "ORG_001");
    innerMap.put(JsonKey.FILTERS, filters);
    innerMap.put(JsonKey.LIMIT, 1);
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put(JsonKey.FIELDS, JsonKey.ORG_NAME);
    reqObj.setContext(contextMap);
    reqObj.setRequest(innerMap);
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void searchUserWithObjectTypeAsOrg() {
    PowerMockito.mockStatic(EsClientFactory.class);
    ElasticSearchService esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    Promise<Map<String, Object>> promise = Futures.promise();
    promise.success(createResponseGet(true));
    when(esService.search(Mockito.any(), Mockito.anyString(), Mockito.any()))
        .thenReturn(promise.future());

    PowerMockito.mockStatic(SunbirdMWService.class);
    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    PowerMockito.mockStatic(BaseMWService.class);
    BaseMWService.getRemoteRouter(Mockito.anyString());
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.USER_SEARCH.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.QUERY, "");
    Map<String, Object> filters = new HashMap<>();
    List<String> objectType = new ArrayList<String>();
    objectType.add("org");
    filters.put(JsonKey.OBJECT_TYPE, objectType);
    filters.put(JsonKey.ROOT_ORG_ID, "ORG_001");
    innerMap.put(JsonKey.FILTERS, filters);
    innerMap.put(JsonKey.LIMIT, 1);

    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put(JsonKey.FIELDS, JsonKey.ORG_NAME);
    reqObj.setContext(contextMap);
    reqObj.setRequest(innerMap);
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void testInvalidOperation() {
    PowerMockito.mockStatic(SunbirdMWService.class);
    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    PowerMockito.mockStatic(BaseMWService.class);
    BaseMWService.getRemoteRouter(Mockito.anyString());
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation("INVALID_OPERATION");

    subject.tell(reqObj, probe.getRef());
    ProjectCommonException exc =
        probe.expectMsgClass(duration("10 second"), ProjectCommonException.class);
    Assert.assertTrue(null != exc);
  }
}
