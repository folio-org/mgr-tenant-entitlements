package org.folio.entitlement.configuration;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class JpaConfiguration {

  @Bean
  public TransactionTemplate newTransactionTemplate(PlatformTransactionManager transactionManager) {
    var transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    return transactionTemplate;
  }
}
