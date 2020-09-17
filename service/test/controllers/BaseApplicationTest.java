package controllers;

import static org.powermock.api.mockito.PowerMockito.mockStatic;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import modules.OnRequestHandler;
import modules.StartModule;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.actor.service.SunbirdMWService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.response.ResponseParams;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.telemetry.util.TelemetryWriter;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.RequestInterceptor;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@PrepareForTest({RequestInterceptor.class, TelemetryWriter.class,
        OnRequestHandler.class,
        ActorRef.class,
        SunbirdMWService.class})
public abstract class BaseApplicationTest {
  protected Application application;
  private ActorSystem system;
  private Props props;
  private SunbirdMWService app;
  private static ActorRef subject;

  public <T> void setup(Class<T> actorClass) {
    PowerMockito.mockStatic(SunbirdMWService.class);
//    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    try {
      application =
          new GuiceApplicationBuilder()
              .in(new File("path/to/app"))
              .in(Mode.TEST)
              .disable(StartModule.class)
              .build();
      Helpers.start(application);
      system = ActorSystem.create("system");
      props = Props.create(actorClass);
      subject = system.actorOf(props);
     // BaseController.setActorRef(subject);
      applicationSetUp();
/*      mockStatic(RequestInterceptor.class);*/
      mockStatic(TelemetryWriter.class);
   /*   PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.anyObject()))
          .thenReturn(userAuthentication);
      mockStatic(OnRequestHandler.class);*/

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  public void applicationSetUp() {
    app = PowerMockito.mock(SunbirdMWService.class);
    //PowerMockito.mockStatic(org.sunbird.Application.class);
   // PowerMockito.when(SunbirdMWService.init()).thenReturn(app);
   // app.init();
  }
  public Result performTest(String url, String method) {
    Http.RequestBuilder req = new Http.RequestBuilder().uri(url).method(method);
    Result result = Helpers.route(application, req);
    return result;
  }

  public String getResponseCode(Result result) {
    String responseStr = Helpers.contentAsString(result);
    ObjectMapper mapper = new ObjectMapper();

    try {
      Response response = mapper.readValue(responseStr, Response.class);

      if (response != null) {
        ResponseParams params = response.getParams();
        return params.getStatus();
      }
    } catch (Exception e) {
      ProjectLogger.log(
          "BaseControllerTest:getResponseCode: Exception occurred with error message = "
              + e.getMessage(),
          LoggerEnum.ERROR.name());
    }
    return "";
  }

  public int getResponseStatus(Result result) {
    return result.status();
  }
}
