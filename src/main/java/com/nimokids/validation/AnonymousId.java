package com.nimokids.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the player's UUID from the X-Anonymous-Id header into a controller parameter.
 * A missing or malformed header fails with ANONYMOUS_PLAYER_REQUIRED.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AnonymousId {
}
