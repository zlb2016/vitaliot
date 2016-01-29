package utils;

import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {
	
	private static ConfigReader instance;
	private Properties config;
	
	public static final String IDP_HOST = "IDP_HOST";
	public static final String IDP_PORT = "IDP_PORT";
	public static final String PROXY_HOST = "PROXY_HOST";
	public static final String PROXY_PORT = "PROXY_PORT";
	public static final String SNMP_PORT = "SNMP_PORT";
	public static final String SSO_TOKEN = "SSO_TOKEN";
	public static final String ALT_TOKEN = "ALT_TOKEN";
	
	private ConfigReader() {
		config = new Properties();
		InputStream is = this.getClass().getResourceAsStream("/config.properties");
		
		try {
			config.load(is);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static ConfigReader getInstance() {
		if (instance == null) {
			instance = new ConfigReader();
		}
		return instance;
	}
	
	public String get(String key) {
		return config.getProperty(key);
	}
	
	
}
