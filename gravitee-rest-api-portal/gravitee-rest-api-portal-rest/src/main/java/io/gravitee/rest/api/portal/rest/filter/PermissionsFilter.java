/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.rest.api.portal.rest.filter;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gravitee.repository.management.model.MembershipDefaultReferenceId;
import io.gravitee.repository.management.model.MembershipReferenceType;
import io.gravitee.repository.management.model.RoleScope;
import io.gravitee.rest.api.model.ApplicationEntity;
import io.gravitee.rest.api.model.RoleEntity;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.portal.rest.resource.AbstractResource;
import io.gravitee.rest.api.portal.rest.security.Permission;
import io.gravitee.rest.api.portal.rest.security.Permissions;
import io.gravitee.rest.api.service.ApiService;
import io.gravitee.rest.api.service.ApplicationService;
import io.gravitee.rest.api.service.GroupService;
import io.gravitee.rest.api.service.MembershipService;
import io.gravitee.rest.api.service.RoleService;
import io.gravitee.rest.api.service.exceptions.ForbiddenAccessException;
import io.gravitee.rest.api.service.exceptions.UnauthorizedAccessException;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Provider
@Priority(100)
public class PermissionsFilter implements ContainerRequestFilter {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Context
    protected ResourceInfo resourceInfo;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private MembershipService membershipService;

    @Inject
    private ApplicationService applicationService;

    @Inject
    private ApiService apiService;

    @Inject
    private RoleService roleService;

    @Inject
    private GroupService groupService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (    securityContext.isUserInRole(AbstractResource.MANAGEMENT_ADMIN) ||
                securityContext.isUserInRole(AbstractResource.PORTAL_ADMIN)) {
            logger.debug("User [{}] has full access because of its ADMIN role",
                    securityContext.getUserPrincipal().getName());
            return;
        }

        filter(getRequiredPermission(), requestContext);
    }

    protected void filter(Permissions permissions, ContainerRequestContext requestContext) {
        if (permissions != null && permissions.value().length > 0) {
            Principal principal = securityContext.getUserPrincipal();
            if (principal != null) {
                String username = principal.getName();
                for (Permission permission : permissions.value()) {
                    if(hasPermission(requestContext, username, permission)) {
                        return;
                    }
                }
            }
            sendSecurityError();
        }
    }

    protected boolean hasPermission(ContainerRequestContext requestContext, String username, Permission permission) {
        RoleEntity role;
        Map<String, char[]> memberPermissions;
        switch (permission.value().getScope()) {
            case MANAGEMENT:
                role = membershipService.getRole(MembershipReferenceType.MANAGEMENT, MembershipDefaultReferenceId.DEFAULT.name(), username, RoleScope.MANAGEMENT);
                return roleService.hasPermission(role.getPermissions(), permission.value().getPermission(), permission.acls());
            case PORTAL:
                role = membershipService.getRole(MembershipReferenceType.PORTAL, MembershipDefaultReferenceId.DEFAULT.name(), username, RoleScope.PORTAL);
                return roleService.hasPermission(role.getPermissions(), permission.value().getPermission(), permission.acls());
            case APPLICATION:
                ApplicationEntity application = getApplication(requestContext);
                memberPermissions = membershipService.getMemberPermissions(application, username);
                return roleService.hasPermission(memberPermissions, permission.value().getPermission(), permission.acls());
            case API:
                ApiEntity api = getApi(requestContext);
                memberPermissions = membershipService.getMemberPermissions(api, username);
                return roleService.hasPermission(memberPermissions, permission.value().getPermission(), permission.acls());
            default:
                sendSecurityError();
        }
        return false;
    }

    private ApiEntity getApi(ContainerRequestContext requestContext) {
        String apiId = getId("apiId", requestContext);
        if (apiId == null) {
            return null;
        }
        return apiService.findById(apiId);
    }

    private ApplicationEntity getApplication(ContainerRequestContext requestContext) {
        String applicationId = getId("applicationId", requestContext);
        if (applicationId == null) {
            return null;
        }
        return applicationService.findById(applicationId);
    }

    private String getId(String key, ContainerRequestContext requestContext) {
        List<String> pathParams = requestContext.getUriInfo().getPathParameters().get(key);
        if (pathParams != null) {
            return pathParams.iterator().next();
        } else {
            List<String> queryParams = requestContext.getUriInfo().getQueryParameters().get(key);
            if (queryParams != null) {
                return queryParams.iterator().next();
            }
        }
        return null;
    }

    private void sendSecurityError() {
        Principal principal = securityContext.getUserPrincipal();
        if (principal != null) {
            throw new ForbiddenAccessException();
        } else {
            throw new UnauthorizedAccessException();
        }
    }

    private Permissions getRequiredPermission() {
        Permissions permission = resourceInfo.getResourceMethod().getDeclaredAnnotation(Permissions.class);

        if (permission == null) {
            return resourceInfo.getResourceClass().getDeclaredAnnotation(Permissions.class);
        }

        return permission;
    }
}
