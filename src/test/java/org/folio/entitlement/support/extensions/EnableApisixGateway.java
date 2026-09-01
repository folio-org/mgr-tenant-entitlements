package org.folio.entitlement.support.extensions;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.folio.entitlement.support.extensions.impl.ApisixGatewayExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(ApisixGatewayExtension.class)
public @interface EnableApisixGateway {}
