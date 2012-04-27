package org.jboss.as.test.jbossts.extension;

import org.jboss.as.arquillian.api.ServerSetupTask;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to an arquillian test to allow for server setup to be performed
 * before the deployment is performed.
 *
 * This will be run before the first deployment is performed for each server.
 *
 * This is a version dedicated for the manual mode containers.
 *
 * @author Stuart Douglas
 * @author Ivo Studensky (istudens@redhat.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ManualServerSetup {

    Class<? extends ServerSetupTask>[] value();

}
