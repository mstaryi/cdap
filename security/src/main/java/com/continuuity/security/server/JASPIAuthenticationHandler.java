package com.continuuity.security.server;

import com.continuuity.common.conf.CConfiguration;
import com.google.inject.Inject;
import org.apache.geronimo.components.jaspi.impl.ServerAuthConfigImpl;
import org.apache.geronimo.components.jaspi.impl.ServerAuthContextImpl;
import org.apache.geronimo.components.jaspi.model.AuthModuleType;
import org.apache.geronimo.components.jaspi.model.ServerAuthConfigType;
import org.apache.geronimo.components.jaspi.model.ServerAuthContextType;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.jaspi.JaspiAuthenticator;
import org.eclipse.jetty.security.jaspi.JaspiAuthenticatorFactory;
import org.eclipse.jetty.security.jaspi.ServletCallbackHandler;
import org.eclipse.jetty.security.jaspi.modules.BasicAuthModule;
import org.eclipse.jetty.util.security.Constraint;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;
import javax.security.auth.message.module.ServerAuthModule;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 */
public class JASPIAuthenticationHandler extends ConstraintSecurityHandler {
  private final CConfiguration configuration;
  private static final String configBase = "security\\.authentication\\.handler\\.";

  @Inject
  public JASPIAuthenticationHandler(CConfiguration configuration) throws Exception {
    super();
    this.configuration = configuration;

    Constraint constraint = new Constraint();
    constraint.setRoles(new String[] {"*"});
    constraint.setAuthenticate(true);

    ConstraintMapping constraintMapping = new ConstraintMapping();
    constraintMapping.setConstraint(constraint);
    constraintMapping.setPathSpec("/*");

    URL realmFile = getClass().getResource("/realm.properties");
    HashLoginService loginService = new HashLoginService();
    loginService.setConfig(realmFile.toExternalForm());
    loginService.loadUsers();

    DefaultIdentityService identityService = new DefaultIdentityService();

    loginService.setIdentityService(identityService);
    JaspiAuthenticatorFactory jaspiAuthenticatorFactory = new JaspiAuthenticatorFactory();
    jaspiAuthenticatorFactory.setLoginService(loginService);

    HashMap<String, ServerAuthContext> serverAuthContextMap= new HashMap<String, ServerAuthContext>();
    ServletCallbackHandler callbackHandler = new ServletCallbackHandler(loginService);
    ServerAuthModule authModule = new BasicAuthModule(callbackHandler, "JAASRealm");
    serverAuthContextMap.put("authenticationContextID", new ServerAuthContextImpl(Collections.singletonList(authModule)));

    ServerAuthContextType serverAuthContextType = new ServerAuthContextType("HTTP", "server *",
                                                                            "authenticationContextID",
                                                                            new AuthModuleType<ServerAuthModule>());
    ServerAuthConfigType serverAuthConfigType = new ServerAuthConfigType(serverAuthContextType, true);
    ServerAuthConfig serverAuthConfig = new ServerAuthConfigImpl(serverAuthConfigType,serverAuthContextMap);
    JaspiAuthenticator jaspiAuthenticator = new JaspiAuthenticator(serverAuthConfig, null, callbackHandler,
                                                                   new Subject(), true, identityService);

    this.setStrict(false);
    this.setIdentityService(identityService);
    this.setAuthenticator(jaspiAuthenticator);
    this.setLoginService(loginService);

    this.setConstraintMappings(new ConstraintMapping[]{constraintMapping});
  }

  /**
   * Dynamically load the configuration properties set by the user for a JASPI plugin.
   * @return
   */
  protected Configuration getConfiguration() {
    return new Configuration() {
      @Override
      public AppConfigurationEntry[] getAppConfigurationEntry(String s) {
        HashMap<String, String> map = new HashMap<String, String>();

        HashMap<String, String> configurables = (HashMap<String, String>) configuration.getValByRegex(configBase.concat("."));

        Iterator it = configurables.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry pairs = (Map.Entry)it.next();
          String key = pairs.getKey().toString();
          String value = pairs.getValue().toString();
          map.put(key.substring(key.lastIndexOf('.') + 1).trim(), value);
        }

        return new AppConfigurationEntry[] {
          new AppConfigurationEntry(configuration.get("security.authentication.loginmodule.className"),
                                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, map)
        };
      }
    };
  }
}
