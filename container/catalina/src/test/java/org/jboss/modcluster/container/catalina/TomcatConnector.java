package org.jboss.modcluster.container.catalina;

import java.net.InetAddress;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.IntrospectionUtils;
import org.jboss.modcluster.container.catalina.CatalinaConnector;

public class TomcatConnector extends CatalinaConnector {

    public TomcatConnector(Connector connector) {
        super(connector);
    }

    @Override
    public int getMaxThreads() {
        Integer result = (Integer) IntrospectionUtils.getProperty(this.connector.getProtocolHandler(), "maxThreads");
        return (result != null) ? result.intValue() : 0;
    }
    
    @Override
    protected Object getEndpoint() {
        return this.getProtocolHandlerProperty("endpoint");
    }

    public void setAddress(InetAddress address) {
        IntrospectionUtils.setProperty(this.connector.getProtocolHandler(), "address", address.getHostAddress());
    }
}
