package org.folio.entitlement.support.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.folio.entitlement.support.extensions.impl.KongGatewayExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(KongGatewayExtension.class)
public @interface EnableKongGateway {
  boolean enableWiremock() default false;
}
