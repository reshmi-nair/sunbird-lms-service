package org.sunbird.user;

import static akka.testkit.JavaTestKit.duration;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import java.util.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.KeyCloakConnectionProvider;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.util.FormApiUtilHandler;
import org.sunbird.user.actors.UserTypeActor;

@RunWith(PowerMockRunner.class)
@PrepareForTest({KeyCloakConnectionProvider.class, FormApiUtilHandler.class})
@PowerMockIgnore("javax.management.*")
public class UserTypeActorTest {

  private static ActorSystem system = ActorSystem.create("system");
  private static final Props props = Props.create(UserTypeActor.class);

  @Test
  public void testGetUserTypesSuccess() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    PowerMockito.mockStatic(FormApiUtilHandler.class);
    PowerMockito.when(FormApiUtilHandler.getFormApiConfig(Mockito.any(), Mockito.any()))
        .thenReturn(getFormApiConfig());
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.GET_USER_TYPES.getValue());
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(duration("1000 second"), Response.class);
    Assert.assertTrue(res.getResponseCode() == ResponseCode.OK && getResponse(res));
  }

  private boolean getResponse(Response res) {

    List<Map<String, String>> lst =
        (List<Map<String, String>>) res.getResult().get(JsonKey.RESPONSE);
    Set<String> types = new HashSet<>();
    for (Map map : lst) {
      Iterator<Map.Entry<String, String>> itr = map.entrySet().iterator();
      while (itr.hasNext()) {
        Map.Entry<String, String> entry = itr.next();
        types.add(entry.getValue());
      }
    }
    if (types.size() == 1) return true;
    return false;
  }

  public Map<String, Object> getFormApiConfig() {
    Map<String, Object> formData = new HashMap<>();
    Map<String, Object> formMap = new HashMap<>();
    Map<String, Object> dataMap = new HashMap<>();
    List<Map<String, Object>> fieldsList = new ArrayList<>();
    Map<String, Object> field = new HashMap<>();

    Map<String, Object> children = new HashMap<>();
    List<Map<String, Object>> userTypeConfigList = new ArrayList<>();
    Map<String, Object> subPersonConfig = new HashMap<>();
    Map<String, Object> templateOptionsMap = new HashMap<>();
    List<Map<String, String>> options = new ArrayList<>();
    Map<String, String> option = new HashMap<>();
    option.put(JsonKey.VALUE, "crc");
    options.add(option);

    templateOptionsMap.put(JsonKey.OPTIONS, options);
    subPersonConfig.put(JsonKey.CODE, JsonKey.SUB_PERSONA);
    subPersonConfig.put(JsonKey.TEMPLATE_OPTIONS, templateOptionsMap);
    userTypeConfigList.add(subPersonConfig);
    children.put("teacher", userTypeConfigList);
    field.put(JsonKey.CODE, JsonKey.PERSONA);
    field.put(JsonKey.CHILDREN, children);
    fieldsList.add(field);
    dataMap.put(JsonKey.FIELDS, fieldsList);
    formMap.put(JsonKey.DATA, dataMap);
    formData.put(JsonKey.FORM, formMap);
    return formData;
  }
}
