package org.folio.entitlement.service;

import java.util.concurrent.ConcurrentHashMap;
import org.folio.entitlement.domain.model.RetryInformation;
import org.springframework.stereotype.Service;

@Service
public class RetryInformationService {

  private final ConcurrentHashMap<String, RetryInformation> retryInformationCache = new ConcurrentHashMap<>();

  public RetryInformationService set(String key, RetryInformation retryInformation) {
    retryInformationCache.put(key, retryInformation);
    return this;
  }

  public RetryInformation get(String key) {
    return retryInformationCache.get(key);
  }

  public RetryInformationService clear(String key) {
    retryInformationCache.remove(key);
    return this;
  }
}
