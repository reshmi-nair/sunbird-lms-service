package org.sunbird.user.service.impl;

import java.util.*;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.common.request.RequestContext;
import org.sunbird.user.dao.UserExternalIdentityDao;
import org.sunbird.user.dao.impl.UserExternalIdentityDaoImpl;
import org.sunbird.user.service.UserExternalIdentityService;
import org.sunbird.user.util.UserExternalIdentityAdapter;
import org.sunbird.user.util.UserUtil;

public class UserExternalIdentityServiceImpl implements UserExternalIdentityService {
  private UserExternalIdentityDao userExternalIdentityDao =
      new UserExternalIdentityDaoImpl();

  @Override
  public List<Map<String, Object>> getSelfDeclaredDetails(
      String userId, String orgId, String role, RequestContext context) {
    // Todo:For new Update Api
    return null;
  }

  @Override
  public List<Map<String, String>> getSelfDeclaredDetails(String userId, RequestContext context) {
    List<Map<String, String>> externalIds = new ArrayList<>();
    List<Map<String, Object>> dbSelfDeclareExternalIds =
        userExternalIdentityDao.getUserSelfDeclaredDetails(userId, context);
    if (CollectionUtils.isNotEmpty(dbSelfDeclareExternalIds)) {
      externalIds =
          UserExternalIdentityAdapter.convertSelfDeclareFieldsToExternalIds(
              dbSelfDeclareExternalIds.get(0));
    }
    return externalIds;
  }

  @Override
  public List<Map<String, String>> getUserExternalIds(String userId, RequestContext context) {
    return userExternalIdentityDao.getUserExternalIds(userId, context);
  }

  /**
   * Fetch userid using channel info from usr_external_identity table
   *
   * @param extId
   * @param provider
   * @param idType
   * @param context
   * @return
   */
  @Override
  public String getUserV1(String extId, String provider, String idType, RequestContext context) {
    Map<String, String> providerOrgMap =
        UserUtil.fetchOrgIdByProvider(Arrays.asList(provider), context);
    String orgId = UserUtil.getCaseInsensitiveOrgFromProvider(provider, providerOrgMap);
    return userExternalIdentityDao.getUserIdByExternalId(extId, orgId, context);
  }

  /**
   * Fetch userid using orgId info to support usr_external_identity table
   *
   * @param extId
   * @param orgId
   * @param idType
   * @param context
   * @return
   */
  @Override
  public String getUserV2(String extId, String orgId, String idType, RequestContext context) {
    return userExternalIdentityDao.getUserIdByExternalId(extId, orgId, context);
  }
}
