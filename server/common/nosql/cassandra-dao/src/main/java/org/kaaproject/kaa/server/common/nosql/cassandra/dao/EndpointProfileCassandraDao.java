/*
 * Copyright 2014 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaaproject.kaa.server.common.nosql.cassandra.dao;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import org.apache.commons.codec.binary.Base64;
import org.kaaproject.kaa.common.dto.EndpointProfileBodyDto;
import org.kaaproject.kaa.common.dto.EndpointProfileDto;
import org.kaaproject.kaa.common.dto.EndpointProfilesBodyDto;
import org.kaaproject.kaa.common.dto.EndpointProfilesPageDto;
import org.kaaproject.kaa.common.dto.PageLinkDto;
import org.kaaproject.kaa.server.common.dao.DaoConstants;
import org.kaaproject.kaa.server.common.dao.impl.EndpointProfileDao;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.filter.CassandraEPByAccessTokenDao;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.filter.CassandraEPByAppIdDao;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.filter.CassandraEPByEndpointGroupIdDao;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.filter.CassandraEPBySdkTokenDao;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraEPByAccessToken;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraEPByAppId;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraEPByEndpointGroupId;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraEPBySdkToken;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraEndpointProfile;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraEndpointUser;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.type.CassandraEndpointGroupState;
import org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.in;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.kaaproject.kaa.server.common.dao.impl.DaoUtil.getDto;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.CassandraDaoUtil.convertKeyHashToString;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.CassandraDaoUtil.convertStringToKeyHash;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.CassandraDaoUtil.getByteBuffer;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.CassandraDaoUtil.getBytes;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_APP_ID_PROPERTY;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_BY_APP_ID_APPLICATION_ID_PROPERTY;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_BY_APP_ID_COLUMN_FAMILY_NAME;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_BY_APP_ID_ENDPOINT_KEY_HASH_PROPERTY;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_BY_ENDPOINT_GROUP_ID_COLUMN_FAMILY_NAME;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_BY_ENDPOINT_GROUP_ID_ENDPOINT_GROUP_ID_PROPERTY;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_BY_ENDPOINT_GROUP_ID_ENDPOINT_KEY_HASH_PROPERTY;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_COLUMN_FAMILY_NAME;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_EP_KEY_HASH_PROPERTY;
import static org.kaaproject.kaa.server.common.nosql.cassandra.dao.model.CassandraModelConstants.EP_PROFILE_PROPERTY;

@Repository(value = "endpointProfileDao")
public class EndpointProfileCassandraDao extends AbstractCassandraDao<CassandraEndpointProfile, ByteBuffer> implements EndpointProfileDao<CassandraEndpointProfile> {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointProfileCassandraDao.class);

    @Autowired
    private CassandraEPByAppIdDao cassandraEPByAppIdDao;
    @Autowired
    private CassandraEPByAccessTokenDao cassandraEPByAccessTokenDao;
    @Autowired
    private CassandraEPBySdkTokenDao cassandraEPBySdkTokenDao;
    @Autowired
    private CassandraEPByEndpointGroupIdDao cassandraEPByEndpointGroupIdDao;

    private EndpointUserCassandraDao endpointUserDao;

    @Override
    protected Class<CassandraEndpointProfile> getColumnFamilyClass() {
        return CassandraEndpointProfile.class;
    }

    @Override
    protected String getColumnFamilyName() {
        return EP_COLUMN_FAMILY_NAME;
    }

    @Override
    public CassandraEndpointProfile save(EndpointProfileDto dto) {
        if (isBlank(dto.getId())) {
            return save(new CassandraEndpointProfile(dto));
        } else {
            return update(new CassandraEndpointProfile(dto));
        }
    }

    @Override
    public CassandraEndpointProfile save(CassandraEndpointProfile profile) {
        profile.setId(convertKeyHashToString(profile.getEndpointKeyHash()));
        LOG.debug("Saving endpoint profile with id {}", profile.getId());
        ByteBuffer epKeyHash = profile.getEndpointKeyHash();
        List<Statement> statementList = new ArrayList<>();
        statementList.add(cassandraEPByAppIdDao.getSaveQuery(new CassandraEPByAppId(profile.getApplicationId(), epKeyHash)));
        String accessToken = profile.getAccessToken();
        if (accessToken != null) {
            statementList.add(cassandraEPByAccessTokenDao.getSaveQuery(new CassandraEPByAccessToken(accessToken, epKeyHash)));
        }
        statementList.add(getSaveQuery(profile));
        Statement saveBySdkTokenId = cassandraEPBySdkTokenDao.getSaveQuery(new CassandraEPBySdkToken(profile.getSdkToken(), epKeyHash));
        statementList.add(saveBySdkTokenId);
        Set<String> groupIdSet = getEndpointProfilesGroupIdSet(profile);
        for (String groupId : groupIdSet) {
            statementList.add(cassandraEPByEndpointGroupIdDao.getSaveQuery(
                    new CassandraEPByEndpointGroupId(groupId, epKeyHash)));
        }
        executeBatch(statementList.toArray(new Statement[statementList.size()]));
        LOG.debug("[{}] Endpoint profile saved", profile.getId());
        return profile;
    }

    private CassandraEndpointProfile update(CassandraEndpointProfile profile) {
        LOG.debug("Updating endpoint profile with id {}", profile.getId());
        ByteBuffer epKeyHash = profile.getEndpointKeyHash();
        CassandraEndpointProfile storedProfile = findByKeyHash(getBytes(epKeyHash));
        if (storedProfile != null) {
            List<Statement> statementList = new ArrayList<>();
            statementList.add(getSaveQuery(profile));
            Set<String> oldEndpointGroupIds = getEndpointProfilesGroupIdSet(storedProfile);
            Set<String> newEndpointGroupIds = getEndpointProfilesGroupIdSet(profile);

            Set<String> removeEndpointGroupIds = Sets.filter(oldEndpointGroupIds, Predicates.not(Predicates.in(newEndpointGroupIds)));
            Set<String> addEndpointGroupIds = Sets.filter(newEndpointGroupIds, Predicates.not(Predicates.in(oldEndpointGroupIds)));
            if (addEndpointGroupIds != null) {
                for (String id : addEndpointGroupIds) {
                    statementList.add(cassandraEPByEndpointGroupIdDao.getSaveQuery(new CassandraEPByEndpointGroupId(id, epKeyHash)));
                }
            }
            if (removeEndpointGroupIds != null) {
                for (String id : removeEndpointGroupIds) {
                    statementList.add(delete().from(EP_BY_ENDPOINT_GROUP_ID_COLUMN_FAMILY_NAME)
                            .where(eq(EP_BY_ENDPOINT_GROUP_ID_ENDPOINT_GROUP_ID_PROPERTY, id))
                            .and(eq(EP_BY_ENDPOINT_GROUP_ID_ENDPOINT_KEY_HASH_PROPERTY, epKeyHash)));
                }
            }
            executeBatch(statementList.toArray(new Statement[statementList.size()]));
            LOG.debug("[{}] Endpoint profile updated", profile.getId());
        } else {
            LOG.warn("Stored profile is null. Can't update endpoint profile");
        }
        return profile;
    }

    @Override
    public CassandraEndpointProfile findByKeyHash(byte[] endpointKeyHash) {
        LOG.debug("Try to find endpoint profile by key hash [{}]", endpointKeyHash);
        CassandraEndpointProfile endpointProfile = (CassandraEndpointProfile) getMapper().get(getByteBuffer(endpointKeyHash));
        LOG.debug("[{}] Found endpoint profile {}", endpointKeyHash, endpointProfile);
        return endpointProfile;
    }

    @Override
    public EndpointProfileBodyDto findBodyByKeyHash(byte[] endpointKeyHash) {
        LOG.debug("Try to find endpoint profile body by key hash [{}]", endpointKeyHash);
        String profile = null;
        String appId = null;
        ResultSet resultSet = execute(select(EP_PROFILE_PROPERTY, EP_APP_ID_PROPERTY).from(getColumnFamilyName())
                .where(eq(EP_EP_KEY_HASH_PROPERTY, getByteBuffer(endpointKeyHash))));
        Row row = resultSet.one();
        if (row != null) {
            profile = row.getString(EP_PROFILE_PROPERTY);
            appId = row.getString(EP_APP_ID_PROPERTY);
        }
        LOG.debug("[{}] Found endpoint profile body {}", endpointKeyHash, profile);
        return new EndpointProfileBodyDto(endpointKeyHash, profile, appId);
    }

    @Override
    public long getCountByKeyHash(byte[] endpointKeyHash) {
        LOG.debug("Try to check if endpoint profile exists with key hash [{}]", endpointKeyHash);
        long count = 0;
        ResultSet resultSet = execute(select().countAll().from(getColumnFamilyName())
                .where(eq(EP_EP_KEY_HASH_PROPERTY, getByteBuffer(endpointKeyHash))));
        Row row = resultSet.one();
        if (row != null) {
            count = row.getLong(0);
        }
        LOG.debug("{} endpoint profile exists with key hash [{}]", count);
        return count;
    }

    private void removeByKeyHashFromEpByEndpointGroupId(byte[] endpointKeyHash) {
        CassandraEndpointProfile storedProfile = findByKeyHash(endpointKeyHash);
        List<CassandraEndpointGroupState> cfGroupState = new ArrayList<>();
        List<String> endpointGroupIds = new ArrayList<>();
        List<Statement> statementList = new ArrayList<>();
        if (storedProfile.getCfGroupState() != null) {
            cfGroupState.addAll(storedProfile.getCfGroupState());
        }
        if (cfGroupState != null) {
            for (CassandraEndpointGroupState cf : cfGroupState) {
                endpointGroupIds.add(cf.getEndpointGroupId());
            }
        }
        if (endpointGroupIds != null) {
            for (String id : endpointGroupIds) {
                statementList.add(delete().from(EP_BY_ENDPOINT_GROUP_ID_COLUMN_FAMILY_NAME)
                        .where(eq(EP_BY_ENDPOINT_GROUP_ID_ENDPOINT_GROUP_ID_PROPERTY, id))
                        .and(eq(EP_BY_ENDPOINT_GROUP_ID_ENDPOINT_KEY_HASH_PROPERTY, getByteBuffer(endpointKeyHash))));
            }
        }
        Statement[] st = new Statement[statementList.size()];
        statementList.toArray(st);
        executeBatch(st);
    }

    @Override
    public void removeByKeyHash(byte[] endpointKeyHash) {
        LOG.debug("Remove endpoint profile by key hash [{}]", endpointKeyHash);
        CassandraEndpointProfile storedProfile = findByKeyHash(endpointKeyHash);
        removeByKeyHashFromEpByEndpointGroupId(endpointKeyHash);
        String appId = storedProfile.getApplicationId();
        if (!appId.isEmpty()) {
            Statement deleteEp = delete().from(EP_BY_APP_ID_COLUMN_FAMILY_NAME).where(eq(EP_BY_APP_ID_APPLICATION_ID_PROPERTY, appId))
                    .and(eq(EP_BY_APP_ID_ENDPOINT_KEY_HASH_PROPERTY, getByteBuffer(endpointKeyHash)));
            executeBatch(deleteEp);
        }
        getMapper().delete(getByteBuffer(endpointKeyHash));
     }

    @Override
    public void removeByAppId(String appId) {
        LOG.debug("Remove endpoint profile by application id [{}]", appId);
        Statement deleteEps = delete().from(getColumnFamilyName()).where(in(EP_EP_KEY_HASH_PROPERTY, cassandraEPByAppIdDao.getEPIdsListByAppId(appId)));
        ByteBuffer[] epKeyHashList =  cassandraEPByAppIdDao.getEPIdsListByAppId(appId);
        if (epKeyHashList != null) {
            for (ByteBuffer epKeyHash : epKeyHashList) {
                removeByKeyHashFromEpByEndpointGroupId(getBytes(epKeyHash));
            }
        }
        Statement deleteEpsByAppId = delete().from(EP_BY_APP_ID_COLUMN_FAMILY_NAME).where(eq(EP_BY_APP_ID_APPLICATION_ID_PROPERTY, appId));
        executeBatch(deleteEps, deleteEpsByAppId);
        LOG.trace("Execute statements {}, {} like batch", deleteEps, deleteEpsByAppId);
    }

    @Override
    public CassandraEndpointProfile findByAccessToken(String endpointAccessToken) {
        LOG.debug("Try to find endpoint profile by access token id [{}]", endpointAccessToken);
        CassandraEndpointProfile endpointProfile = null;
        ByteBuffer epKeyHash = cassandraEPByAccessTokenDao.findEPIdByAccessToken(endpointAccessToken);
        if (epKeyHash != null) {
            endpointProfile = (CassandraEndpointProfile) getMapper().get(epKeyHash);
        }
        LOG.trace("Found endpoint profile {} by access token [{}]", endpointProfile, endpointAccessToken);
        return endpointProfile;
    }

    private List<EndpointProfileDto> findEndpointProfilesList(ByteBuffer[] keyHashList, String endpointGroupId) {
        List<EndpointProfileDto> cassandraEndpointProfileList = new ArrayList<>();
        LOG.debug("Found {} endpoint profiles by group id {}", keyHashList != null ? keyHashList.length : 0, endpointGroupId);
        for (ByteBuffer keyHash : keyHashList) {
            CassandraEndpointProfile profile = findByKeyHash(getBytes(keyHash));
            if (profile != null) {
                cassandraEndpointProfileList.add(getDto(profile));
            } else {
                LOG.debug("Can't find endpoint profile by id {}", keyHash);
            }
        }
        return cassandraEndpointProfileList;
    }

    private List<EndpointProfileBodyDto> findEndpointProfilesBodyList(ByteBuffer[] keyHashList, String endpointGroupId) {
        List<EndpointProfileBodyDto> endpointProfilesBodyDto = new ArrayList<>();
        LOG.debug("Found {} endpoint profiles body by group id {}", keyHashList != null ? keyHashList.length : 0, endpointGroupId);
        for (ByteBuffer keyHash : keyHashList) {
            EndpointProfileBodyDto endpointProfileBodyDto = findBodyByKeyHash(getBytes(keyHash));
            if (endpointProfileBodyDto != null) {
                endpointProfilesBodyDto.add(endpointProfileBodyDto);
            } else {
                LOG.debug("Can't find endpoint profile by id {}", keyHash);
            }
        }
        return endpointProfilesBodyDto;
    }

    private EndpointProfilesPageDto createNextPage(List<EndpointProfileDto> cassandraEndpointProfileList, String endpointGroupId, String limit) {
        EndpointProfilesPageDto endpointProfilesPageDto = new EndpointProfilesPageDto();
        PageLinkDto pageLinkDto = new PageLinkDto();
        String next;
        int lim = Integer.valueOf(limit);
        if (cassandraEndpointProfileList.size() == (lim + 1)) {
            pageLinkDto.setEndpointGroupId(endpointGroupId);
            pageLinkDto.setLimit(limit);
            pageLinkDto.setOffset(Base64.encodeBase64URLSafeString(cassandraEndpointProfileList.get(lim).getEndpointKeyHash()));
            cassandraEndpointProfileList.remove(lim);
            next = null;
        } else {
            next = DaoConstants.LAST_PAGE_MESSAGE;
        }
        pageLinkDto.setNext(next);
        endpointProfilesPageDto.setPageLinkDto(pageLinkDto);
        endpointProfilesPageDto.setEndpointProfiles(cassandraEndpointProfileList);
        return endpointProfilesPageDto;
    }

    private EndpointProfilesBodyDto createNextBodyPage(List<EndpointProfileBodyDto> profilesBodyDto, String endpointGroupId, String limit) {
        EndpointProfilesBodyDto endpointProfilesBodyDto = new EndpointProfilesBodyDto();
        PageLinkDto pageLinkDto = new PageLinkDto();
        String next;
        int lim = Integer.valueOf(limit);
        if (profilesBodyDto.size() == (lim + 1)) {
            pageLinkDto.setEndpointGroupId(endpointGroupId);
            pageLinkDto.setLimit(limit);
            pageLinkDto.setOffset(Base64.encodeBase64URLSafeString(profilesBodyDto.get(lim).getEndpointKeyHash()));
            profilesBodyDto.remove(lim);
            next = null;
        } else {
            next = DaoConstants.LAST_PAGE_MESSAGE;
        }
        pageLinkDto.setNext(next);
        endpointProfilesBodyDto.setPageLinkDto(pageLinkDto);
        endpointProfilesBodyDto.setEndpointProfilesBody(profilesBodyDto);
        return endpointProfilesBodyDto;
    }

    @Override
    public EndpointProfilesPageDto findByEndpointGroupId(PageLinkDto pageLink) {
        LOG.debug("Try to find endpoint profile by endpoint group id [{}]", pageLink.getEndpointGroupId());
        EndpointProfilesPageDto endpointProfilesPageDto;
        List<EndpointProfileDto> cassandraEndpointProfileList;
        ByteBuffer[] keyHashList;
        if (pageLink.getApplicationId() != null) {
            keyHashList = cassandraEPByAppIdDao.findEPByAppId(pageLink, pageLink.getApplicationId());
        } else {
            keyHashList = cassandraEPByEndpointGroupIdDao.findEPByEndpointGroupId(pageLink);
        }
        cassandraEndpointProfileList = findEndpointProfilesList(keyHashList, pageLink.getEndpointGroupId());
        endpointProfilesPageDto = createNextPage(cassandraEndpointProfileList, pageLink.getEndpointGroupId(), pageLink.getLimit());
        return endpointProfilesPageDto;
    }

    @Override
    public EndpointProfilesBodyDto findBodyByEndpointGroupId(PageLinkDto pageLink) {
        LOG.debug("Try to find endpoint profile body by endpoint group id [{}]", pageLink.getEndpointGroupId());
        EndpointProfilesBodyDto endpointProfilesBodyDto;
        List<EndpointProfileBodyDto> profilesBodyDto;
        ByteBuffer[] keyHashList;
        if (pageLink.getApplicationId() != null) {
            keyHashList = cassandraEPByAppIdDao.findEPByAppId(pageLink, pageLink.getApplicationId());
        } else {
            keyHashList = cassandraEPByEndpointGroupIdDao.findEPByEndpointGroupId(pageLink);
        }
        profilesBodyDto = findEndpointProfilesBodyList(keyHashList, pageLink.getEndpointGroupId());
        endpointProfilesBodyDto = createNextBodyPage(profilesBodyDto, pageLink.getEndpointGroupId(), pageLink.getLimit());
        return endpointProfilesBodyDto;
    }

    @Override
    public List<CassandraEndpointProfile> findByEndpointUserId(String endpointUserId) {
        LOG.debug("Try to find endpoint profiles by endpoint user id [{}]", endpointUserId);
        List<CassandraEndpointProfile> profileList = Collections.emptyList();
        CassandraEndpointUser endpointUser = endpointUserDao.findById(endpointUserId);
        if (endpointUser != null) {
            List<String> ids = endpointUser.getEndpointIds();
            if (ids != null && !ids.isEmpty()) {
                Statement select = select().from(getColumnFamilyName()).where(QueryBuilder.in(EP_EP_KEY_HASH_PROPERTY, convertStringIds(ids)));
                LOG.trace("Execute statements {}", select);
                profileList = findListByStatement(select);
            }
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Found endpoint profiles {}", Arrays.toString(profileList.toArray()));
        }
        return profileList;
    }

    @Override
    public CassandraEndpointProfile findById(ByteBuffer key) {
        LOG.debug("Try to find endpoint profiles by key [{}]", key);
        CassandraEndpointProfile profile = null;
        if (key != null) {
            profile = findByKeyHash(key.array());
        }
        LOG.trace("Found endpoint profiles {}", profile);
        return profile;
    }

    @Override
    public void removeById(ByteBuffer key) {
        LOG.debug("Remove endpoint profiles by key [{}]", key);
        if (key != null) {
            removeByKeyHash(key.array());
        }
    }

    private ByteBuffer[] convertStringIds(List<String> ids) {
        ByteBuffer[] keyHashArray = new ByteBuffer[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            keyHashArray[i] = convertStringToKeyHash(ids.get(i));
        }
        return keyHashArray;
    }

    public EndpointUserCassandraDao getEndpointUserDao() {
        return endpointUserDao;
    }

    public void setEndpointUserDao(EndpointUserCassandraDao endpointUserDao) {
        this.endpointUserDao = endpointUserDao;
    }

    /**
     * @deprecated This method needs additional testing and thus is not
     *             recommended to use as of October, 2015.
     */
    @Override
    public List<CassandraEndpointProfile> findBySdkToken(String sdkToken) {
        LOG.debug("Trying to find endpoint profiles by SDK token {}", sdkToken);

        Statement query = QueryBuilder
                .select()
                .from(CassandraModelConstants.EP_BY_SDK_TOKEN_COLUMN_FAMILY_NAME)
                .where(QueryBuilder.eq(CassandraModelConstants.EP_BY_SDK_TOKEN_SDK_TOKEN_PROPERTY, sdkToken));

        LOG.trace("Executing statement {}", query);

        List<CassandraEndpointProfile> profiles = this.findListByStatement(query);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Endpoint profiles found: [{}]", Arrays.toString(profiles.toArray()));
        }

        return profiles;
    }

    @Override
    public boolean checkSdkToken(String sdkToken) {
        LOG.debug("Checking for endpoint profiles with SDK token {}", sdkToken);

        Statement query = QueryBuilder
                .select()
                .from(CassandraModelConstants.EP_BY_SDK_TOKEN_COLUMN_FAMILY_NAME)
                .where(QueryBuilder.eq(CassandraModelConstants.EP_BY_SDK_TOKEN_SDK_TOKEN_PROPERTY, sdkToken));

        return this.execute(query).one() != null;
    }

    private Set<String> getEndpointProfilesGroupIdSet(CassandraEndpointProfile profile) {
        Set<String> groupIdSet = new HashSet<>();
        List<CassandraEndpointGroupState> groupStateSet = new LinkedList<>();
        if (profile != null) {
            List<CassandraEndpointGroupState> cfGroupState = profile.getCfGroupState();
            if (cfGroupState != null && !cfGroupState.isEmpty()) {
                groupStateSet.addAll(cfGroupState);
            }
            List<CassandraEndpointGroupState> nfGroupState = profile.getNfGroupState();
            if (nfGroupState != null && !nfGroupState.isEmpty()) {
                groupStateSet.addAll(nfGroupState);
            }
            for (CassandraEndpointGroupState cf : groupStateSet) {
                groupIdSet.add(cf.getEndpointGroupId());
            }
        }
        return groupIdSet;
    }
}
