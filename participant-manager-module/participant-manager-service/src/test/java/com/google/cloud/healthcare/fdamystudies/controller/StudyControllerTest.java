/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.controller;

import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.CLOSE_STUDY;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.ENROLLED_STATUS;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.OPEN;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.OPEN_STUDY;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.USER_ID_HEADER;
import static com.google.cloud.healthcare.fdamystudies.common.JsonUtils.asJsonString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.cloud.healthcare.fdamystudies.beans.UpdateTargetEnrollmentRequest;
import com.google.cloud.healthcare.fdamystudies.common.ApiEndpoint;
import com.google.cloud.healthcare.fdamystudies.common.BaseMockIT;
import com.google.cloud.healthcare.fdamystudies.common.ErrorCode;
import com.google.cloud.healthcare.fdamystudies.common.IdGenerator;
import com.google.cloud.healthcare.fdamystudies.common.MessageCode;
import com.google.cloud.healthcare.fdamystudies.common.Permission;
import com.google.cloud.healthcare.fdamystudies.common.SiteStatus;
import com.google.cloud.healthcare.fdamystudies.common.TestConstants;
import com.google.cloud.healthcare.fdamystudies.helper.TestDataHelper;
import com.google.cloud.healthcare.fdamystudies.model.AppEntity;
import com.google.cloud.healthcare.fdamystudies.model.LocationEntity;
import com.google.cloud.healthcare.fdamystudies.model.ParticipantRegistrySiteEntity;
import com.google.cloud.healthcare.fdamystudies.model.ParticipantStudyEntity;
import com.google.cloud.healthcare.fdamystudies.model.SiteEntity;
import com.google.cloud.healthcare.fdamystudies.model.StudyEntity;
import com.google.cloud.healthcare.fdamystudies.model.StudyPermissionEntity;
import com.google.cloud.healthcare.fdamystudies.model.UserRegAdminEntity;
import com.google.cloud.healthcare.fdamystudies.repository.SiteRepository;
import com.google.cloud.healthcare.fdamystudies.service.StudyService;
import com.jayway.jsonpath.JsonPath;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

public class StudyControllerTest extends BaseMockIT {

  @Autowired private StudyService studyService;

  @Autowired private StudyController controller;

  @Autowired private TestDataHelper testDataHelper;

  @Autowired private SiteRepository siteRepository;

  private UserRegAdminEntity userRegAdminEntity;

  private SiteEntity siteEntity;

  private StudyEntity studyEntity;

  protected MvcResult result;

  private ParticipantRegistrySiteEntity participantRegistrySiteEntity;

  private ParticipantStudyEntity participantStudyEntity;

  private AppEntity appEntity;

  private LocationEntity locationEntity;

  @BeforeEach
  public void setUp() {
    userRegAdminEntity = testDataHelper.createUserRegAdminEntity();
    appEntity = testDataHelper.createAppEntity(userRegAdminEntity);
    studyEntity = testDataHelper.createStudyEntity(userRegAdminEntity, appEntity);
    siteEntity = testDataHelper.createSiteEntity(studyEntity, userRegAdminEntity, appEntity);
    participantRegistrySiteEntity =
        testDataHelper.createParticipantRegistrySite(siteEntity, studyEntity);
    participantStudyEntity =
        testDataHelper.createParticipantStudyEntity(
            siteEntity, studyEntity, participantRegistrySiteEntity);
  }

  @Test
  public void contextLoads() {
    assertNotNull(controller);
    assertNotNull(mockMvc);
    assertNotNull(studyService);
  }

  @Test
  public void shouldReturnStudies() throws Exception {
    HttpHeaders headers = newCommonHeaders();
    headers.add(TestConstants.USER_ID_HEADER, userRegAdminEntity.getId());

    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDIES.getPath()).headers(headers).contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studies").isArray())
        .andExpect(jsonPath("$.studies", hasSize(1)))
        .andExpect(jsonPath("$.studies[0].id").isNotEmpty())
        .andExpect(jsonPath("$.studies[0].type").value(studyEntity.getType()))
        .andExpect(jsonPath("$.sitePermissionCount").value(1));
  }

  @Test
  public void shouldReturnBadRequestForGetStudies() throws Exception {
    HttpHeaders headers = newCommonHeaders();

    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDIES.getPath()).headers(headers).contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations").isArray())
        .andExpect(jsonPath("$.violations[0].path").value("userId"))
        .andExpect(jsonPath("$.violations[0].message").value("header is required"));
  }

  @Test
  public void shouldReturnStudyNotFound() throws Exception {
    HttpHeaders headers = newCommonHeaders();
    headers.add(TestConstants.USER_ID_HEADER, IdGenerator.id());

    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDIES.getPath()).headers(headers).contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.error_description").value(ErrorCode.STUDY_NOT_FOUND.getDescription()));
  }

  @Test
  public void shouldReturnStudyNotFoundForStudyParticipants() throws Exception {
    HttpHeaders headers = newCommonHeaders();
    headers.add(TestConstants.USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDY_PARTICIPANT.getPath(), IdGenerator.id())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.error_description").value(ErrorCode.STUDY_NOT_FOUND.getDescription()));
  }

  @Test
  public void shouldReturnAppNotFoundForStudyParticipants() throws Exception {
    HttpHeaders headers = newCommonHeaders();
    headers.add(TestConstants.USER_ID_HEADER, userRegAdminEntity.getId());

    StudyPermissionEntity studyPermission = studyEntity.getStudyPermissions().get(0);
    studyPermission.setApp(null);
    studyEntity = testDataHelper.getStudyRepository().saveAndFlush(studyEntity);
    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDY_PARTICIPANT.getPath(), studyEntity.getId())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error_description").value(ErrorCode.APP_NOT_FOUND.getDescription()));
  }

  @Test
  public void shouldReturnAccessDeniedForStudyParticipants() throws Exception {
    HttpHeaders headers = newCommonHeaders();
    headers.add(TestConstants.USER_ID_HEADER, userRegAdminEntity.getId());

    StudyEntity study = testDataHelper.newStudyEntity();
    testDataHelper.getStudyRepository().saveAndFlush(study);
    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDY_PARTICIPANT.getPath(), study.getId())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.error_description")
                .value(ErrorCode.STUDY_PERMISSION_ACCESS_DENIED.getDescription()));
  }

  @Test
  public void shouldReturnStudyParticipants() throws Exception {
    HttpHeaders headers = newCommonHeaders();
    headers.add(TestConstants.USER_ID_HEADER, userRegAdminEntity.getId());
    locationEntity = testDataHelper.createLocation();
    studyEntity.setType(OPEN_STUDY);
    siteEntity.setLocation(locationEntity);
    siteEntity.setTargetEnrollment(0);
    siteEntity.setStudy(studyEntity);
    participantRegistrySiteEntity.setEmail(TestConstants.EMAIL_VALUE);
    participantStudyEntity.setStudy(studyEntity);
    participantStudyEntity.setStatus(ENROLLED_STATUS);
    participantStudyEntity.setParticipantRegistrySite(participantRegistrySiteEntity);
    testDataHelper.getParticipantStudyRepository().saveAndFlush(participantStudyEntity);

    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDY_PARTICIPANT.getPath(), studyEntity.getId())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.participantRegistryDetail.studyId").value(studyEntity.getId()))
        .andExpect(jsonPath("$.participantRegistryDetail.registryParticipants").isArray())
        .andExpect(jsonPath("$.participantRegistryDetail.registryParticipants", hasSize(1)))
        .andExpect(
            jsonPath("$.participantRegistryDetail.registryParticipants[0].siteId")
                .value(siteEntity.getId()))
        .andExpect(
            jsonPath("$.participantRegistryDetail.registryParticipants[0].locationName")
                .value(locationEntity.getName()))
        .andExpect(
            jsonPath("$.participantRegistryDetail.registryParticipants[0].enrollmentStatus")
                .value(participantStudyEntity.getStatus()))
        .andExpect(
            jsonPath("$.participantRegistryDetail.targetEnrollment")
                .value(siteEntity.getTargetEnrollment()));
  }

  @Test
  public void shouldReturnUserNotFound() throws Exception {
    HttpHeaders headers = newCommonHeaders();

    mockMvc
        .perform(
            get(ApiEndpoint.GET_STUDY_PARTICIPANT.getPath(), studyEntity.getId())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations").isArray())
        .andExpect(jsonPath("$.violations[0].path").value("userId"))
        .andExpect(jsonPath("$.violations[0].message").value("header is required"));
  }

  public HttpHeaders newCommonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Test
  public void shouldUpdateTargetEnrollment() throws Exception {
    // Step 1:Set request body
    UpdateTargetEnrollmentRequest targetEnrollmentRequest = newUpdateEnrollmentTargetRequest();

    // Step 2: Call API and expect TARGET_ENROLLMENT_UPDATE_SUCCESS message
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    result =
        mockMvc
            .perform(
                patch(ApiEndpoint.UPDATE_TARGET_ENROLLMENT.getPath(), studyEntity.getId())
                    .headers(headers)
                    .content(asJsonString(targetEnrollmentRequest))
                    .contextPath(getContextPath()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteId", notNullValue()))
            .andExpect(
                jsonPath(
                    "$.message", is(MessageCode.TARGET_ENROLLMENT_UPDATE_SUCCESS.getMessage())))
            .andReturn();

    String siteId = JsonPath.read(result.getResponse().getContentAsString(), "$.siteId");

    // Step 3: verify updated values
    Optional<SiteEntity> optSiteEntity = siteRepository.findById(siteId);
    SiteEntity siteEntity = optSiteEntity.get();
    assertNotNull(siteEntity);
    assertEquals(siteEntity.getStudy().getId(), studyEntity.getId());
    assertEquals(siteEntity.getTargetEnrollment(), targetEnrollmentRequest.getTargetEnrollment());
  }

  @Test
  public void shouldReturnNotFoundForUpdateTargetEnrollment() throws Exception {
    // Step 1:Set studyId to invalid
    UpdateTargetEnrollmentRequest targetEnrollmentRequest = newUpdateEnrollmentTargetRequest();
    StudyEntity study = testDataHelper.newStudyEntity();
    study.setCustomId("CovidStudy1");
    study.setApp(appEntity);
    siteEntity.setStudy(study);
    testDataHelper.getSiteRepository().saveAndFlush(siteEntity);

    // Step 2: Call API and expect SITE_NOT_FOUND error
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            patch(ApiEndpoint.UPDATE_TARGET_ENROLLMENT.getPath(), studyEntity.getId())
                .headers(headers)
                .content(asJsonString(targetEnrollmentRequest))
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error_description", is(ErrorCode.SITE_NOT_FOUND.getDescription())));
  }

  @Test
  public void shouldReturnStudyPermissionAccessDeniedForUpdateTargetEnrollment() throws Exception {
    // Step 1:Set permission to READ_VIEW
    UpdateTargetEnrollmentRequest targetEnrollmentRequest = newUpdateEnrollmentTargetRequest();
    StudyPermissionEntity studyPermissionEntity = studyEntity.getStudyPermissions().get(0);
    studyPermissionEntity.setEdit(Permission.VIEW);
    studyEntity = testDataHelper.getStudyRepository().saveAndFlush(studyEntity);

    // Step 2: Call API and expect STUDY_PERMISSION_ACCESS_DENIED error
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            patch(ApiEndpoint.UPDATE_TARGET_ENROLLMENT.getPath(), studyEntity.getId())
                .headers(headers)
                .content(asJsonString(targetEnrollmentRequest))
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath(
                "$.error_description",
                is(ErrorCode.STUDY_PERMISSION_ACCESS_DENIED.getDescription())));
  }

  @Test
  public void shouldReturnCannotUpdateTargetEnrollmentForCloseStudy() throws Exception {
    // Step 1:Set study type to close
    UpdateTargetEnrollmentRequest targetEnrollmentRequest = newUpdateEnrollmentTargetRequest();
    studyEntity.setType(CLOSE_STUDY);
    testDataHelper.getStudyRepository().saveAndFlush(studyEntity);

    // Step 2: Call API and expect CANNOT_UPDATE_ENROLLMENT_TARGET_FOR_CLOSE_STUDY error
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            patch(ApiEndpoint.UPDATE_TARGET_ENROLLMENT.getPath(), studyEntity.getId())
                .headers(headers)
                .content(asJsonString(targetEnrollmentRequest))
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(
                "$.error_description",
                is(ErrorCode.CANNOT_UPDATE_ENROLLMENT_TARGET_FOR_CLOSE_STUDY.getDescription())));
  }

  @Test
  public void shouldReturnCannotUpdateTargetEnrollmentForDeactiveSite() throws Exception {
    // Step 1:Set site status to DEACTIVE
    UpdateTargetEnrollmentRequest targetEnrollmentRequest = newUpdateEnrollmentTargetRequest();
    siteEntity.setStatus(SiteStatus.DEACTIVE.value());
    testDataHelper.getSiteRepository().saveAndFlush(siteEntity);

    // Step 2: Call API and expect CANNOT_UPDATE_ENROLLMENT_TARGET_FOR_DEACTIVE_SITE error
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            patch(ApiEndpoint.UPDATE_TARGET_ENROLLMENT.getPath(), studyEntity.getId())
                .headers(headers)
                .content(asJsonString(targetEnrollmentRequest))
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(
                "$.error_description",
                is(
                    ErrorCode.CANNOT_UPDATE_ENROLLMENT_TARGET_FOR_DECOMMISSIONED_SITE
                        .getDescription())));
  }

  @AfterEach
  public void clean() {
    testDataHelper.cleanUp();
  }

  private UpdateTargetEnrollmentRequest newUpdateEnrollmentTargetRequest() {
    UpdateTargetEnrollmentRequest request = new UpdateTargetEnrollmentRequest();
    request.setTargetEnrollment(150);
    studyEntity.setType(OPEN);
    testDataHelper.getStudyRepository().saveAndFlush(studyEntity);
    return request;
  }
}
