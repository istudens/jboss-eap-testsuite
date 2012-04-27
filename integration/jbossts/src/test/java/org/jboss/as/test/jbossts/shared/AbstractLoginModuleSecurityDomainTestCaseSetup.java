package org.jboss.as.test.jbossts.shared;

import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.dmr.ModelNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CODE;
import static org.jboss.as.security.Constants.*;

/**
 * Stolen from org.jboss.as.test.integration.jca.security
 * @author Stuart Douglas
 */
public abstract class AbstractLoginModuleSecurityDomainTestCaseSetup extends AbstractSecurityDomainSetup {
    @Override
    protected abstract String getSecurityDomainName();

    protected abstract String getLoginModuleName();

    protected abstract boolean isRequired();

    protected abstract Map<String, String> getModuleOptions();

    @Override
    public void setup(final ManagementClient managementClient, String containerId) throws Exception {
            final List<ModelNode> updates = new ArrayList<ModelNode>();
            ModelNode op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SUBSYSTEM, "security");
            op.get(OP_ADDR).add(SECURITY_DOMAIN, getSecurityDomainName());
            updates.add(op);
            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SUBSYSTEM, "security");
            op.get(OP_ADDR).add(SECURITY_DOMAIN, getSecurityDomainName());

            op.get(OP_ADDR).add(AUTHENTICATION, CLASSIC);


            ModelNode loginModule = op.get(LOGIN_MODULES).add();

            loginModule.get(CODE).set(getLoginModuleName());
            if (!isRequired()) {
                loginModule.get(FLAG).set("optional");

            } else {
                loginModule.get(FLAG).set("required");
            }

            loginModule.get(MODULE_OPTIONS).add("password-stacking", "useFirstPass");

            Map<String, String> options = getModuleOptions();
            Set<String> keys = options.keySet();

            for (String key : keys) {
                loginModule.get(MODULE_OPTIONS).add(key, options.get(key));
            }

            op.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
            updates.add(op);

            applyUpdates(managementClient.getControllerClient(), updates);
    }
}
