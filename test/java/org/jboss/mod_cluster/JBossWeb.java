/*
 *  mod_cluster
 *
 *  Copyright(c) 2008 Red Hat Middleware, LLC,
 *  and individual contributors as indicated by the @authors tag.
 *  See the copyright.txt in the distribution for a
 *  full listing of individual contributors.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library in the file COPYING.LIB;
 *  if not, write to the Free Software Foundation, Inc.,
 *  59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 *
 * @author Jean-Frederic Clere
 * @version $Revision$
 */

package org.jboss.mod_cluster;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.catalina.startup.Embedded;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Context;
import org.apache.catalina.*;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.*;
import org.apache.catalina.startup.HostConfig;

import org.apache.catalina.LifecycleListener;

public class JBossWeb extends Embedded {

    private String route = null;

    private void copyFile(File in, File out) throws IOException {
        FileInputStream fis  = new FileInputStream(in);
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[1024];
        int i = 0;
        while((i=fis.read(buf))!=-1) {
            fos.write(buf, 0, i);
        }
        fis.close();
        fos.close();
    }

    private static void copyFiles(File src, File dest) throws IOException {
	if (src.isDirectory()) 	{
		if (!dest.exists())
			dest.mkdirs();

		String list[] = src.list();
		for (int i = 0; i < list.length; i++) {
			File dest1 = new File(dest, list[i]);
			File src1 = new File(src, list[i]);
			copyFiles(src1 , dest1);
		}
	} else { 
		//This was not a directory, so lets just copy the file
		FileInputStream fin = null;
		FileOutputStream fout = null;
		byte[] buffer = new byte[4096]; //Buffer 4K at a time (you can change this).
		int bytesRead;
		//open the files for input and output
		fin =  new FileInputStream(src);
		fout = new FileOutputStream (dest);
		//while bytesRead indicates a successful read, lets write...
		while ((bytesRead = fin.read(buffer)) >= 0) {
			fout.write(buffer,0,bytesRead);
		}
                fout.close();
                fin.close();
	}
    }
    private void copyNativeDir(String route) throws IOException {
        File in = new File("bin/");
	if (!in.exists()) {
            return;
        }
        File ou = new File("node1/bin");
	if (!ou.exists()) {
            ou.mkdirs();
        }
        copyFiles(in, ou);
    }

    public JBossWeb(String route, String host, boolean nat, String webapp, String[] Aliases) throws IOException {

        // Copy native tree...
        if (nat) {
            copyNativeDir(route);
        }

        setCatalinaBase(route);
        setCatalinaHome(route);
        this.route = route;

        //Create an Engine
        Engine baseEngine = createEngine();

        baseEngine.setName(host + "Engine" + route);
        baseEngine.setDefaultHost(host);
        baseEngine.setJvmRoute(route);
        baseEngine.setRealm(null);

        // Create node1/webapps/ROOT and index.html
        File fd = new File ( route + "/webapps/" + webapp);
        fd.mkdirs();
        String docBase = fd.getAbsolutePath();
        String appBase = fd.getParent();
        fd = new File (route + "/webapps/" + webapp, "index.html");
        FileWriter out = new FileWriter(fd);
        out.write(route + ":This is a test\n");
        out.close();

        // Copy a small servlet for testing.
        fd = new File ( route + "/webapps/" + webapp + "/WEB-INF/classes");
        fd.mkdirs();
        fd = new File (route + "/webapps/"  + webapp + "/WEB-INF/classes" , "MyCount.class");
        File fdin = new File ("MyCount.class");
        copyFile(fdin, fd);
        

        //Create Host
        Host baseHost =  createHost( host, appBase);
        baseHost.setDeployOnStartup(true);
        baseHost.setBackgroundProcessorDelay(1);
        StandardHost stdhost = (StandardHost)baseHost;
        stdhost.setDeployXML(true);
        stdhost.setConfigClass("org.apache.catalina.startup.ContextConfig");
        stdhost.setUnpackWARs(true);
        if (Aliases != null && Aliases.length>0) {
            for (int j = 0; j < Aliases.length; j++) {
                stdhost.addAlias(Aliases[j]);
            }
        }
        HostConfig hostConfig = new HostConfig();
        stdhost.addLifecycleListener(hostConfig);
        baseEngine.addChild( baseHost );

        //Create default context
        Context rootContext;
        if (webapp.equals("ROOT"))
            rootContext = createContext("/",docBase );
        else
            rootContext = createContext("/" + webapp, docBase );
        rootContext.setIgnoreAnnotations(true);
        rootContext.setPrivileged(true);

        Wrapper wrapper = rootContext.createWrapper();
        wrapper.setName("MyCount");
        wrapper.setServletClass("MyCount");
        rootContext.addChild(wrapper);
        rootContext.addServletMapping("/MyCount", "MyCount");

        baseHost.addChild( rootContext );
        addEngine( baseEngine );
        baseEngine.setService(this);
        this.setName(host + "Engine" + route);
    }
    void AddContext(String path, String docBase, String servletName) {

        File fd = new File ( route + "/webapps/" + docBase);
        fd.mkdirs();
        docBase = fd.getAbsolutePath();

        Context context = createContext(path, docBase);
        if (servletName != null) {
             // Copy a small servlet for testing.
             fd = new File ( route + "/webapps/" + docBase + "/WEB-INF/classes");
             fd.mkdirs();
             fd = new File (route + "/webapps/"  + docBase + "/WEB-INF/classes" , servletName + ".class");
             File fdin = new File (servletName + ".class");
             try {
                 copyFile(fdin, fd);
             } catch(IOException ex) {
             }
             /* add mapping */
             Wrapper wrapper = context.createWrapper();
             wrapper.setName(servletName);
             wrapper.setServletClass(servletName);
             context.addChild(wrapper);
             context.addServletMapping("/" + servletName, servletName);
        }
        context.setIgnoreAnnotations(true);
        context.setPrivileged(true);

        Engine engine = (Engine) getContainer();
        Container[] containers = engine.findChildren();
        for (int j = 0; j < containers.length; j++) {
            if (containers[j] instanceof Host) {
                Host host = (Host) containers[j];
                host.addChild(context);
            }
        }
    }
    void AddContext(String path, String docBase) {
        AddContext(path, docBase, null);
    }
    void AddContext(String path, String docBase, boolean hasservlet) {
        if (hasservlet)
            AddContext(path, docBase, "MyCount");
        else
            AddContext(path, docBase, null);
    }

    public JBossWeb(String route, String host) throws IOException {
        this(route, host, false);
    }

    public JBossWeb(String route, String host, boolean nat) throws IOException {
        this(route, host, nat, "ROOT");
    }

    public JBossWeb(String route, String host, boolean nat, String webapp) throws IOException {
        this(route, host, nat, webapp, null);
    }

    public void addWAR(String file, String route) throws IOException {
        File fd = new File ( route + "/" +  route + "/webapps");
        fd.mkdirs();

        String sep = System.getProperty("file.separator");
        String [] paths = file.split(sep);
        
        fd = new File (route + "/" +  route + "/webapps", paths[paths.length-1]);
        File fdin = new File (file);
        
        copyFile(fdin, fd);
    }

    public Connector addConnector(int port) throws IOException {
        return addConnector(port, "ajp");
    }

    public Connector addConnector(int port, String scheme) throws IOException {
        return addConnector(port, scheme, null);
    }

    public Connector addConnector(int port, String scheme, String address) throws IOException {
    

        Connector connector = createConnector( address,
                                              port, scheme);

        // Look in StandardService to see why it works ;-)
        addConnector( connector );

        return connector;
    }
    public void removeContext(String path) {
        Engine engine = (Engine) getContainer();
        Container[] containers = engine.findChildren();
        for (int j = 0; j < containers.length; j++) {
            if (containers[j] instanceof StandardHost) {
                StandardHost host = (StandardHost) containers[j];
                Context context = host.map(path);
                containers[j].removeChild(context);
            }
        }
    }


    /* Test */
    public static void main(String[] args) {

        StandardServer server = null;
        JBossWeb service = null;
        JBossWeb service2 = null;
        try {
            server = (StandardServer) ServerFactory.getServer();

            service = new JBossWeb("node1",  "localhost");
            service.addConnector(8009);
            server.addService(service);
 
            service2 = new JBossWeb("node2",  "localhost");
            service2.addConnector(8010);
            server.addService(service2);

            LifecycleListener cluster = Maintest.createClusterListener("232.0.0.2", 23364, false);
            server.addLifecycleListener(cluster);

            Service[] services = server.findServices();
            for (int i = 0; i < services.length; i++) {
               System.out.println("service[" + i + "]: " + services[i]);
               Engine engine = (Engine) services[i].getContainer();
               System.out.println("engine: " + engine);
               // System.out.println("service: " + engine.getService());
               // System.out.println("connectors: " + engine.getService().findConnectors());
               System.out.println("JFC connectors: " + services[i].findConnectors());
               Connector [] connectors = services[i].findConnectors();
               for (int j = 0; j < connectors.length; j++) {
                    System.out.println("JFC connector: " + connectors[j]);
               }
            }

            server.start();

        } catch(IOException ex) {
            ex.printStackTrace();
        } catch (LifecycleException ex) {
            ex.printStackTrace();
        }

        // Wait until httpd as received the nodes information.
        try {
            Thread.sleep(15000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        // Start the client and wait for it.
        Client client = new Client();

        // Wait for it.
        try {
            client.runit("/ROOT/MyCount", 10, true);
            client.start();
            client.join();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (client.getresultok())
            System.out.println("Test DONE");
        else
            System.out.println("Test FAILED");

        // Stop the server or services.
        try {
            server.stop();
            // service.stop();
            // service2.stop();
        } catch (LifecycleException ex) {
            ex.printStackTrace();
        }

        // Wait until httpd as received the stop messages.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}
