package com.lts.spring.boot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Robert HG (254963746@qq.com) on 4/9/16.
 */
@ConfigurationProperties(prefix = "lts.monitor")
public class MonitorProperties extends AbstractProperties {

}
