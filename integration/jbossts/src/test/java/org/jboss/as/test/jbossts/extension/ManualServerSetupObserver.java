package org.jboss.as.test.jbossts.extension;

import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.event.container.AfterUnDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeDeploy;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.context.ClassContext;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.logging.Logger;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author Stuart Douglas
 * @author Ivo Studensky (istudens@redhat.com)
 */
public class ManualServerSetupObserver {
    private static final Logger log = Logger.getLogger(ManualServerSetupObserver.class);

    @Inject
    private Instance<ManagementClient> managementClient;

    @Inject
    private Instance<ClassContext> classContextInstance;

    private final List<ServerSetupTask> current = new ArrayList<ServerSetupTask>();
    private final Map<String, ManagementClient> active = new HashMap<String, ManagementClient>();
    private Map<String, Integer> deployed;

    public synchronized void handleBeforeDeployment(@Observes BeforeDeploy event, Container container) throws Exception {
        log.debug("ManualServerSetupObserver.handleBeforeDeployment");
        if (deployed == null) {
            deployed = new HashMap<String, Integer>();
            current.clear();
        }
        if (deployed.containsKey(container.getName())) {
            deployed.put(container.getName(), deployed.get(container.getName()) + 1);
        } else {
            deployed.put(container.getName(), 1);
        }
        if (active.containsKey(container.getName())) {
            return;
        }

        final ClassContext classContext = classContextInstance.get();
        if (classContext == null) {
            return;
        }

        final Class<?> currentClass = classContext.getActiveId();

        ManualServerSetup setup = currentClass.getAnnotation(ManualServerSetup.class);
        if (setup == null) {
            return;
        }
        final Class<? extends ServerSetupTask>[] classes = setup.value();
        if (current.isEmpty()) {
            for (Class<? extends ServerSetupTask> clazz : classes) {
                Constructor<? extends ServerSetupTask> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                current.add(ctor.newInstance());
            }
        } else {
            //this should never happen
            for (int i = 0; i < current.size(); ++i) {
                if (classes[i] != current.get(i).getClass()) {
                    throw new RuntimeException("Mismatched ServerSetupTask current is " + current + " but " + currentClass + " is expecting " + Arrays.asList(classes));
                }
            }
        }

        final ManagementClient client = managementClient.get();
        for (ServerSetupTask instance : current) {
            instance.setup(client, container.getName());
        }
        active.put(container.getName(), client);
    }

    public synchronized void handleAfterUndeploy(@Observes AfterUnDeploy afterDeploy, final Container container) throws Exception {
        log.debug("ManualServerSetupObserver.handleAfterUndeploy");

        int count = deployed.get(container.getName());
        deployed.put(container.getName(), --count);
        if (count == 0) {
            for (final ServerSetupTask instance : current) {
                instance.tearDown(managementClient.get(), container.getName());
            }
            active.remove(container.getName());
            deployed.remove(container.getName());
        }
        if (deployed.isEmpty()) {
            deployed = null;
            current.clear();
        }
    }

}
