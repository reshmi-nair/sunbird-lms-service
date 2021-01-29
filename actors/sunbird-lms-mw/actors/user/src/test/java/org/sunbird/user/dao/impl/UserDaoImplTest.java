package org.sunbird.user.dao.impl;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.models.user.User;
import org.sunbird.user.dao.UserDao;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  CassandraOperationImpl.class,
})
@PowerMockIgnore({"javax.management.*"})
public class UserDaoImplTest {
  private static CassandraOperation cassandraOperationImpl = null;

  @Before
  public void setUp() {
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperationImpl = mock(CassandraOperationImpl.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperationImpl);
    Response response = new Response();
    Map<String, Object> user = new HashMap<>();
    user.put(JsonKey.ID, "46545665465465");
    user.put(JsonKey.IS_DELETED, false);
    user.put(JsonKey.FIRST_NAME, "firstName");
    List<Map<String, Object>> userList = new ArrayList<>();
    userList.add(user);
    response.getResult().put(JsonKey.RESPONSE, userList);
    when(cassandraOperationImpl.getRecordById(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(RequestContext.class)))
        .thenReturn(response);
  }

  @Test
  public void testGetUserDetailsById() {
    UserDao userDao = UserDaoImpl.getInstance();
    Map<String, Object> user = userDao.getUserDetailsById("123-456-789", null);
    Assert.assertNotNull(user);
  }

  @Test
  public void testGetUserById() {
    UserDao userDao = UserDaoImpl.getInstance();
    User user = userDao.getUserById("123-456-789", null);
    Assert.assertNotNull(user);
  }
}
