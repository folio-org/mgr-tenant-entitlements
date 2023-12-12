package org.folio.entitlement.controller;

import org.folio.security.configuration.SecurityConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@SpringBootConfiguration
@Import({
  ApiExceptionHandler.class,
  SecurityConfiguration.class
})
public class ControllerTestConfiguration {}
