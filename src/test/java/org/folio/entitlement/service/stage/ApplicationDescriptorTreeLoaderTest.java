package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.EntitlementRepository;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.validator.PlatformApplicationNotExistInRequestValidator;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDescriptorTreeLoaderTest {

  private static final String TOKEN = "token";

  @InjectMocks private ApplicationDescriptorTreeLoader loader;
  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private EntitlementRepository entitlementRepository;
  @Mock private PlatformApplicationNotExistInRequestValidator platformApplicationNotExistInRequestValidator;

  @Test
  void execute_positive_validatesRequestedDescriptorsAndLoadsTree() {
    var descriptor = appDescriptor("folio-app1-1.0.0");
    var request = request("folio-app1-1.0.0");
    when(applicationManagerService.getApplicationDescriptors(request.getApplications(), TOKEN))
      .thenReturn(List.of(descriptor));

    var context = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    loader.execute(context);

    verify(platformApplicationNotExistInRequestValidator).validate(List.of(descriptor));
    assertThat(context.getApplicationDescriptors()).containsExactly(descriptor);
    assertThat(context.getApplicationAndDependencyDescriptors()).containsExactly(descriptor);
  }

  @Test
  void execute_negative_platformApplicationRejectedBeforeTreeIsBuilt() {
    var descriptor = appDescriptor("platform-app-1.0.0");
    var request = request("platform-app-1.0.0");
    when(applicationManagerService.getApplicationDescriptors(request.getApplications(), TOKEN))
      .thenReturn(List.of(descriptor));
    doThrow(new RequestValidationException("Tenant request contains platform-scoped applications", "key", "value"))
      .when(platformApplicationNotExistInRequestValidator).validate(List.of(descriptor));

    var context = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), emptyMap());

    assertThatThrownBy(() -> loader.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Tenant request contains platform-scoped applications");

    verify(applicationManagerService).getApplicationDescriptors(request.getApplications(), TOKEN);
    verifyNoMoreInteractions(applicationManagerService);
    verifyNoInteractions(entitlementRepository);
  }

  private static EntitlementRequest request(String applicationId) {
    return EntitlementRequest.builder()
      .type(EntitlementRequestType.ENTITLE)
      .tenantId(TENANT_ID)
      .applications(List.of(applicationId))
      .okapiToken(TOKEN)
      .build();
  }
}
