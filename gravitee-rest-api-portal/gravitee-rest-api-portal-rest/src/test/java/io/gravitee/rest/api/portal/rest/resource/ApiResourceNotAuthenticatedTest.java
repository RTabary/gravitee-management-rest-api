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
package io.gravitee.rest.api.portal.rest.resource;

import static io.gravitee.common.http.HttpStatusCode.OK_200;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.Before;
import org.junit.Test;

import io.gravitee.rest.api.model.PageEntity;
import io.gravitee.rest.api.model.PlanEntity;
import io.gravitee.rest.api.model.Visibility;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.portal.rest.model.Api;
import io.gravitee.rest.api.portal.rest.model.Page;
import io.gravitee.rest.api.portal.rest.model.Plan;

/**
 * @author Florent CHAMFROY (forent.chamfroy at graviteesource.com)
 */
public class ApiResourceNotAuthenticatedTest extends AbstractResourceTest {

    @Override
    protected String contextPath() {
        return "apis/";
    }
    
    @Override
    protected void decorate(ResourceConfig resourceConfig) {
        resourceConfig.register(AuthenticationFilter.class);
    }
    
    @Priority(50)
    public static class AuthenticationFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestContext) throws IOException {
            requestContext.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    return null;
                }
                @Override
                public boolean isUserInRole(String string) {
                    return false;
                }
                @Override
                public boolean isSecure() { return false; }
                
                @Override
                public String getAuthenticationScheme() { return "BASIC"; }
            });
        }
    }

    private static final String API = "my-api";

    private ApiEntity mockApi;
    
    @Before
    public void init() {
        reset(apiService);
        reset(groupService);
        reset(pageService);
        reset(planService);
        reset(apiMapper);
        reset(pageMapper);
        reset(planMapper);
        
        
        mockApi = new ApiEntity();
        mockApi.setId(API);
        mockApi.setVisibility(Visibility.PUBLIC);
        doReturn(mockApi).when(apiService).findById(API);

        doReturn(Arrays.asList(new PageEntity())).when(pageService).search(any());
        
        PlanEntity plan1 = new PlanEntity();
        plan1.setId("A");

        PlanEntity plan2 = new PlanEntity();
        plan2.setId("B");
        
        doReturn(new HashSet<PlanEntity>(Arrays.asList(plan1, plan2))).when(planService).findByApi(API);        
        
        
        doReturn(new Api()).when(apiMapper).convert(any());
        doReturn(new Page()).when(pageMapper).convert(any());
        doReturn(new Plan()).when(planMapper).convert(any());

    }
    
    @Test
    public void shouldGetApiWithPagesAndPlansIncluded() {
        doReturn(true).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(true).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        callResourceAndCheckResult(1, 2);
    }
    
    @Test
    public void shouldGetApiWithPlansIncluded() {
        doReturn(true).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(false).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        callResourceAndCheckResult(0, 2);
    }
    
    @Test
    public void shouldGetApiWithNoElementsIncluded() {
        // case 1
        doReturn(false).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(true).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        callResourceAndCheckResult(0, 0);
        
        // case 2
        doReturn(false).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(false).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        callResourceAndCheckResult(0, 0);
    }
    
    private void callResourceAndCheckResult(Integer expectedTotalPage, Integer expectedTotalPlan) {
        final Response response = target(API).queryParam("include", "pages","plans") .request().get();
        assertEquals(OK_200, response.getStatus());

        Api responseApi = response.readEntity(Api.class);
        assertNotNull(responseApi);
        
        List<Page> pages = responseApi.getPages();
        assertNotNull(pages);
        assertEquals(expectedTotalPage.intValue(), pages.size());
        
        List<Plan> plans = responseApi.getPlans();
        assertNotNull(plans);
        assertEquals(expectedTotalPlan.intValue(), plans.size());
    }
}
