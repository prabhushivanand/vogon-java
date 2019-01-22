/*
 * Vogon personal finance/expense analyzer.
 * Licensed under Apache license: http://www.apache.org/licenses/LICENSE-2.0
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.vogon.web;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Tomcat (via reflection for compatibility with both Tomcat 7 and
 * Tomcat 8)
 *
 * @author Dmitry Zolotukhin [zlogic@gmail.com]
 */
@Configuration
public class TomcatConfigurer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

	/**
	 * The logger
	 */
	private final static org.slf4j.Logger log = LoggerFactory.getLogger(TomcatConfigurer.class);

	/**
	 * Localization messages
	 */
	private static final ResourceBundle messages = ResourceBundle.getBundle("org/zlogic/vogon/web/messages");

	/**
	 * The ServerTypeDetector instance
	 */
	@Autowired
	private ServerTypeDetector serverTypeDetector;
	/**
	 * Unicode charset
	 */
	private static final Charset utf8Charset = Charset.forName("utf-8");//NOI18N

	/**
	 * Configures URI encoding for Tomcat container
	 *
	 * @param factory ConfigurableServletWebServerFactory instance to
	 * configure
	 */
	private void configureUriEncoding(ConfigurableServletWebServerFactory factory) {
		try {
			log.debug(messages.getString("CONFIGURING_ENCODING_FOR_TOMCAT_8"));
			factory.getClass().getMethod("setUriEncoding", Charset.class).invoke(factory, utf8Charset); //NOI18N
			return;
		} catch (NoSuchMethodException ex) {
			log.debug(messages.getString("SERVER_IS_NOT_TOMCAT_8"));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new RuntimeException(messages.getString("CANNOT_CONFIGURE_ENCODING_FOR_TOMCAT_8"), ex);
		}
		throw new RuntimeException(messages.getString("CANNOT_CONFIGURE_ENCODING_FOR_TOMCAT_8"));
	}

	/**
	 * Configures SSL for Tomcat container
	 *
	 * @param factory ConfigurableServletWebServerFactory instance to
	 * configure
	 */
	private void configureSSL(ConfigurableServletWebServerFactory factory) {
		if (serverTypeDetector.getKeystoreFile().isEmpty() || serverTypeDetector.getKeystorePassword().isEmpty()) {
			log.debug(messages.getString("KEYSTORE_FILE_OR_PASSWORD_NOT_DEFINED"));
			return;
		}

		log.info(MessageFormat.format(messages.getString("USING_KEYSTORE_WITH_PASSWORD"), new Object[]{serverTypeDetector.getKeystoreFile(), serverTypeDetector.getKeystorePassword().replaceAll(".{1}", "*")})); //NOI18N
		Object connector;
		Class<?> connectorClass = null;
		try {
			log.debug(messages.getString("CONFIGURING_CONNECTOR"));
			connectorClass = getClass().getClassLoader().loadClass("org.apache.catalina.connector.Connector"); //NOI18N
			connector = connectorClass.getDeclaredConstructor().newInstance();
			connectorClass.getMethod("setPort", Integer.TYPE).invoke(connector, 8443); //NOI18N
			connectorClass.getMethod("setSecure", Boolean.TYPE).invoke(connector, true); //NOI18N
			connectorClass.getMethod("setScheme", String.class).invoke(connector, "https"); //NOI18N
			connectorClass.getMethod("setURIEncoding", String.class).invoke(connector, utf8Charset.name()); //NOI18N
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException ex) {
			throw new RuntimeException(messages.getString("CANNOT_CONFIGURE_CONNECTOR"), ex);
		}

		Object proto;
		try {
			log.debug(messages.getString("CONFIGURING_PROTOCOLHANDLER_PARAMETERS"));
			proto = connectorClass.getMethod("getProtocolHandler").invoke(connector); //NOI18N
			Class<?> protoClass = proto.getClass();
			log.debug(java.text.MessageFormat.format(messages.getString("CONFIGURING_PROTOCOLHANDLER_CLASS"), new Object[]{protoClass.getCanonicalName()}));
			protoClass.getMethod("setSSLEnabled", Boolean.TYPE).invoke(proto, true); //NOI18N
			protoClass.getMethod("setKeystorePass", String.class).invoke(proto, serverTypeDetector.getKeystorePassword()); //NOI18N
			protoClass.getMethod("setKeystoreType", String.class).invoke(proto, "JKS"); //NOI18N
			protoClass.getMethod("setKeyAlias", String.class).invoke(proto, "vogonkey"); //NOI18N
			protoClass.getMethod("setKeystoreFile", String.class).invoke(proto, new File(serverTypeDetector.getKeystoreFile()).getAbsolutePath()); //NOI18N
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new RuntimeException(messages.getString("CANNOT_CONFIGURE_PROTOCOLHANDLER"), ex);
		}

		try {
			log.debug(messages.getString("ADDING_CONNECTOR_TO_TOMCATEMBEDDEDSERVLETCONTAINERFACTORY"));
			Object connectors = Array.newInstance(connectorClass, 1);
			Array.set(connectors, 0, connector);
			factory.getClass().getMethod("addAdditionalTomcatConnectors", connectors.getClass()).invoke(factory, connectors); //NOI18N
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new RuntimeException(messages.getString("CANNOT_ADD_CONNECTOR_TO_TOMCATEMBEDDEDSERVLETCONTAINERFACTORY"), ex);
		}
	}

	/**
	 * Customizes the ConfigurableEmbeddedServletContainer by configuring Tomcat
	 * options
	 *
	 * @param factory the ConfigurableServletWebServerFactory to customize
	 */
	@Override
	public void customize(ConfigurableServletWebServerFactory factory) {
		if (factory instanceof TomcatServletWebServerFactory) {
			configureUriEncoding(factory);
			configureSSL(factory);
		}
	}
}
