/**
 *  Copyright 2014-2016 CyberVision, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.kaaproject.kaa.server.node.service.registration;

import java.util.Optional;

import org.apache.commons.lang3.Validate;
import org.kaaproject.kaa.common.dto.credentials.EndpointRegistrationDto;
import org.kaaproject.kaa.server.common.dao.impl.EndpointRegistrationDao;
import org.kaaproject.kaa.server.common.dao.model.EndpointRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The default implementation of the {@link EndpointRegistrationService}
 * interface.
 *
 * @author Andrew Shvayka
 * @author Bohdan Khablenko
 *
 * @since v0.9.0
 */
@Service
@Transactional
public final class EndpointRegistrationServiceImpl implements EndpointRegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointRegistrationServiceImpl.class);

    @Autowired
    private EndpointRegistrationDao<EndpointRegistration> endpointRegistrationDao;

    @Override
    public EndpointRegistrationDto saveEndpointRegistration(EndpointRegistrationDto endpointRegistration) throws EndpointRegistrationServiceException {
        try {
            Validate.notNull(endpointRegistration, "Invalid endpoint registration provided!");
            return this.endpointRegistrationDao.save(endpointRegistration).toDto();
        } catch (Exception cause) {
            LOG.error("An unexpected exception occured while searching for endpoint registration!", cause);
            throw new EndpointRegistrationServiceException(cause);
        }
    }

    @Override
    public Optional<EndpointRegistrationDto> findEndpointRegistrationByEndpointId(String endpointId) throws EndpointRegistrationServiceException {
        try {
            Validate.notBlank(endpointId, "Invalid endpoint ID provided!");
            Optional<EndpointRegistration> endpointRegistration = this.endpointRegistrationDao.findByEndpointId(endpointId);
            return endpointRegistration.isPresent() ? Optional.of(endpointRegistration.get().toDto()) : Optional.ofNullable(null);
        } catch (Exception cause) {
            LOG.error("An unexpected exception occured while searching for endpoint registration!", cause);
            throw new EndpointRegistrationServiceException(cause);
        }
    }

    @Override
    public Optional<EndpointRegistrationDto> findEndpointRegistrationByCredentialsId(String credentialsId) throws EndpointRegistrationServiceException {
        try {
            Validate.notBlank(credentialsId, "Invalid credentials ID provided!");
            Optional<EndpointRegistration> endpointRegistration = this.endpointRegistrationDao.findByCredentialsId(credentialsId);
            return endpointRegistration.isPresent() ? Optional.of(endpointRegistration.get().toDto()) : Optional.ofNullable(null);
        } catch (Exception cause) {
            LOG.error("An unexpected exception occured while searching for endpoint registration!", cause);
            throw new EndpointRegistrationServiceException(cause);
        }
    }

    @Override
    public void removeEndpointRegistrationByEndpointId(String endpointId) throws EndpointRegistrationServiceException {
        try {
            Validate.notBlank(endpointId, "Invalid endpoint ID provided!");
            this.endpointRegistrationDao.removeByEndpointId(endpointId);
        } catch (Exception cause) {
            LOG.error("An unexpected exception occured while removing endpoint registration!", cause);
            throw new EndpointRegistrationServiceException(cause);
        }
    }
}
