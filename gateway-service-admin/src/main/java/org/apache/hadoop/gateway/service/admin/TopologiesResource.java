/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.service.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.gateway.i18n.GatewaySpiMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.service.admin.beans.BeanConverter;
import org.apache.hadoop.gateway.service.admin.beans.Topology;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.topology.TopologyService;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.notModified;
import static javax.ws.rs.core.Response.status;


@Path("/api/v1")
public class TopologiesResource {

  private static final String XML_EXT  = ".xml";
  private static final String JSON_EXT = ".json";

  private static final String TOPOLOGIES_API_PATH    = "topologies";
  private static final String SINGLE_TOPOLOGY_API_PATH = TOPOLOGIES_API_PATH + "/{id}";
  private static final String PROVIDERCONFIG_API_PATH = "providerconfig";
  private static final String SINGLE_PROVIDERCONFIG_API_PATH = PROVIDERCONFIG_API_PATH + "/{name}";
  private static final String DESCRIPTORS_API_PATH    = "descriptors";
  private static final String SINGLE_DESCRIPTOR_API_PATH = DESCRIPTORS_API_PATH + "/{name}";

  private static GatewaySpiMessages log = MessagesFactory.get(GatewaySpiMessages.class);

  @Context
  private HttpServletRequest request;

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  @Path(SINGLE_TOPOLOGY_API_PATH)
  public Topology getTopology(@PathParam("id") String id) {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);

    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    for (org.apache.hadoop.gateway.topology.Topology t : ts.getTopologies()) {
      if(t.getName().equals(id)) {
        try {
          t.setUri(new URI( buildURI(t, config, request) ));
        } catch (URISyntaxException se) {
          t.setUri(null);
        }
        return BeanConverter.getTopology(t);
      }
    }
    return null;
  }

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  @Path(TOPOLOGIES_API_PATH)
  public SimpleTopologyWrapper getTopologies() {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);


    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    ArrayList<SimpleTopology> st = new ArrayList<SimpleTopology>();
    GatewayConfig conf = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);

    for (org.apache.hadoop.gateway.topology.Topology t : ts.getTopologies()) {
      st.add(getSimpleTopology(t, conf));
    }

    Collections.sort(st, new TopologyComparator());
    SimpleTopologyWrapper stw = new SimpleTopologyWrapper();

    for(SimpleTopology t : st){
      stw.topologies.add(t);
    }

    return stw;

  }

  @PUT
  @Consumes({APPLICATION_JSON, APPLICATION_XML})
  @Path(SINGLE_TOPOLOGY_API_PATH)
  public Topology uploadTopology(@PathParam("id") String id, Topology t) {

    GatewayServices gs = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    t.setName(id);
    TopologyService ts = gs.getService(GatewayServices.TOPOLOGY_SERVICE);

    ts.deployTopology(BeanConverter.getTopology(t));

    return getTopology(id);
  }

  @DELETE
  @Produces(APPLICATION_JSON)
  @Path(SINGLE_TOPOLOGY_API_PATH)
  public Response deleteTopology(@PathParam("id") String id) {
    boolean deleted = false;
    if(!"admin".equals(id)) {
      GatewayServices services = (GatewayServices) request.getServletContext()
          .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

      TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

      for (org.apache.hadoop.gateway.topology.Topology t : ts.getTopologies()) {
        if(t.getName().equals(id)) {
          ts.deleteTopology(t);
          deleted = true;
        }
      }
    }else{
      deleted = false;
    }
    return ok().entity("{ \"deleted\" : " + deleted + " }").build();
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(PROVIDERCONFIG_API_PATH)
  public HrefListing getProviderConfigurations() {
    HrefListing listing = new HrefListing();
    listing.setHref(buildHref(request));

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    List<HrefListItem> configs = new ArrayList<>();
    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);
    // Get all the simple descriptor file names
    for (File providerConfig : ts.getProviderConfigurations()){
      String id = FilenameUtils.getBaseName(providerConfig.getName());
      configs.add(new HrefListItem(buildHref(id, request), providerConfig.getName()));
    }

    listing.setItems(configs);
    return listing;
  }

  @GET
  @Produces({APPLICATION_XML})
  @Path(SINGLE_PROVIDERCONFIG_API_PATH)
  public Response getProviderConfiguration(@PathParam("name") String name) {
    Response response;

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    File providerConfigFile = null;

    for (File pc : ts.getProviderConfigurations()){
      // If the file name matches the specified id
      if (FilenameUtils.getBaseName(pc.getName()).equals(name)) {
        providerConfigFile = pc;
        break;
      }
    }

    if (providerConfigFile != null) {
      byte[] content = null;
      try {
        content = FileUtils.readFileToByteArray(providerConfigFile);
        response = ok().entity(content).build();
      } catch (IOException e) {
        log.failedToReadConfigurationFile(providerConfigFile.getAbsolutePath(), e);
        response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
      }

    } else {
      response = Response.status(Response.Status.NOT_FOUND).build();
    }
    return response;
  }

  @DELETE
  @Produces(APPLICATION_JSON)
  @Path(SINGLE_PROVIDERCONFIG_API_PATH)
  public Response deleteProviderConfiguration(@PathParam("name") String name) {
    Response response;
    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);
    if (ts.deleteProviderConfiguration(name)) {
      response = ok().entity("{ \"deleted\" : \"provider config " + name + "\" }").build();
    } else {
      response = notModified().build();
    }
    return response;
  }


  @DELETE
  @Produces(APPLICATION_JSON)
  @Path(SINGLE_DESCRIPTOR_API_PATH)
  public Response deleteSimpleDescriptor(@PathParam("name") String name) {
    Response response = null;
    if(!"admin".equals(name)) {
      GatewayServices services =
              (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

      TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);
      if (ts.deleteDescriptor(name)) {
        response = ok().entity("{ \"deleted\" : \"descriptor " + name + "\" }").build();
      }
    }

    if (response == null) {
      response = notModified().build();
    }

    return response;
  }


  @PUT
  @Consumes({APPLICATION_XML})
  @Path(SINGLE_PROVIDERCONFIG_API_PATH)
  public Response uploadProviderConfiguration(@PathParam("name") String name, String content) {
    Response response = null;

    GatewayServices gs =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = gs.getService(GatewayServices.TOPOLOGY_SERVICE);

    boolean isUpdate = configFileExists(ts.getProviderConfigurations(), name);

    String filename = name.endsWith(XML_EXT) ? name : name + XML_EXT;
    if (ts.deployProviderConfiguration(filename, content)) {
      try {
        if (isUpdate) {
          response = Response.noContent().build();
        } else{
          response = created(new URI(buildHref(request))).build();
        }
      } catch (URISyntaxException e) {
        log.invalidResourceURI(e.getInput(), e.getReason(), e);
        response = status(Response.Status.BAD_REQUEST).entity("{ \"error\" : \"Failed to deploy provider configuration " + name + "\" }").build();
      }
    }

    return response;
  }


  private boolean configFileExists(Collection<File> existing, String candidateName) {
    boolean result = false;
    for (File exists : existing) {
      if (FilenameUtils.getBaseName(exists.getName()).equals(candidateName)) {
        result = true;
        break;
      }
    }
    return result;
  }


  @PUT
  @Consumes({APPLICATION_JSON})
  @Path(SINGLE_DESCRIPTOR_API_PATH)
  public Response uploadSimpleDescriptor(@PathParam("name") String name, String content) {
    Response response = null;

    GatewayServices gs =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = gs.getService(GatewayServices.TOPOLOGY_SERVICE);

    boolean isUpdate = configFileExists(ts.getDescriptors(), name);

    String filename = name.endsWith(JSON_EXT) ? name : name + JSON_EXT;
    if (ts.deployDescriptor(filename, content)) {
      try {
        if (isUpdate) {
          response = Response.noContent().build();
        } else {
          response = created(new URI(buildHref(request))).build();
        }
      } catch (URISyntaxException e) {
        log.invalidResourceURI(e.getInput(), e.getReason(), e);
        response = status(Response.Status.BAD_REQUEST).entity("{ \"error\" : \"Failed to deploy descriptor " + name + "\" }").build();
      }
    }

    return response;
  }


  @GET
  @Produces({APPLICATION_JSON})
  @Path(DESCRIPTORS_API_PATH)
  public HrefListing getSimpleDescriptors() {
    HrefListing listing = new HrefListing();
    listing.setHref(buildHref(request));

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    List<HrefListItem> descriptors = new ArrayList<>();
    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);
    for (File descriptor : ts.getDescriptors()){
      String id = FilenameUtils.getBaseName(descriptor.getName());
      descriptors.add(new HrefListItem(buildHref(id, request), descriptor.getName()));
    }

    listing.setItems(descriptors);
    return listing;
  }


  @GET
  @Produces({APPLICATION_JSON, TEXT_PLAIN})
  @Path(SINGLE_DESCRIPTOR_API_PATH)
  public Response getSimpleDescriptor(@PathParam("name") String name) {
    Response response;

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    File descriptorFile = null;

    for (File sd : ts.getDescriptors()){
      // If the file name matches the specified id
      if (FilenameUtils.getBaseName(sd.getName()).equals(name)) {
        descriptorFile = sd;
        break;
      }
    }

    if (descriptorFile != null) {
      String mediaType = APPLICATION_JSON;

      byte[] content = null;
      try {
        if ("yml".equals(FilenameUtils.getExtension(descriptorFile.getName()))) {
          mediaType = TEXT_PLAIN;
        }
        content = FileUtils.readFileToByteArray(descriptorFile);
        response = ok().type(mediaType).entity(content).build();
      } catch (IOException e) {
        log.failedToReadConfigurationFile(descriptorFile.getAbsolutePath(), e);
        response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
      }
    } else {
      response = Response.status(Response.Status.NOT_FOUND).build();
    }

    return response;
  }


  private static class TopologyComparator implements Comparator<SimpleTopology> {
    @Override
    public int compare(SimpleTopology t1, SimpleTopology t2) {
      return t1.getName().compareTo(t2.getName());
    }
  }


  String buildURI(org.apache.hadoop.gateway.topology.Topology topology, GatewayConfig config, HttpServletRequest req){
    String uri = buildXForwardBaseURL(req);

    // Strip extra context
    uri = uri.replace(req.getContextPath(), "");

    // Add the gateway path
    String gatewayPath;
    if(config.getGatewayPath() != null){
      gatewayPath = config.getGatewayPath();
    }else{
      gatewayPath = "gateway";
    }
    uri += "/" + gatewayPath;

    uri += "/" + topology.getName();
    return uri;
  }

  String buildHref(HttpServletRequest req) {
    return buildHref((String)null, req);
  }

  String buildHref(String id, HttpServletRequest req) {
    String href = buildXForwardBaseURL(req);
    // Make sure that the pathInfo doesn't have any '/' chars at the end.
    String pathInfo = req.getPathInfo();
    while(pathInfo.endsWith("/")) {
      pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
    }

    href += pathInfo;

    if (id != null) {
      href += "/" + id;
    }

    return href;
  }

   String buildHref(org.apache.hadoop.gateway.topology.Topology t, HttpServletRequest req) {
     return buildHref(t.getName(), req);
  }

  private SimpleTopology getSimpleTopology(org.apache.hadoop.gateway.topology.Topology t, GatewayConfig config) {
    String uri = buildURI(t, config, request);
    String href = buildHref(t, request);
    return new SimpleTopology(t, uri, href);
  }

  private String buildXForwardBaseURL(HttpServletRequest req){
    final String X_Forwarded = "X-Forwarded-";
    final String X_Forwarded_Context = X_Forwarded + "Context";
    final String X_Forwarded_Proto = X_Forwarded + "Proto";
    final String X_Forwarded_Host = X_Forwarded + "Host";
    final String X_Forwarded_Port = X_Forwarded + "Port";
    final String X_Forwarded_Server = X_Forwarded + "Server";

    String baseURL = "";

    // Get Protocol
    if(req.getHeader(X_Forwarded_Proto) != null){
      baseURL += req.getHeader(X_Forwarded_Proto) + "://";
    } else {
      baseURL += req.getProtocol() + "://";
    }

    // Handle Server/Host and Port Here
    if (req.getHeader(X_Forwarded_Host) != null && req.getHeader(X_Forwarded_Port) != null){
      // Double check to see if host has port
      if(req.getHeader(X_Forwarded_Host).contains(req.getHeader(X_Forwarded_Port))){
        baseURL += req.getHeader(X_Forwarded_Host);
      } else {
        // If there's no port, add the host and port together;
        baseURL += req.getHeader(X_Forwarded_Host) + ":" + req.getHeader(X_Forwarded_Port);
      }
    } else if(req.getHeader(X_Forwarded_Server) != null && req.getHeader(X_Forwarded_Port) != null){
      // Tack on the server and port if they're available. Try host if server not available
      baseURL += req.getHeader(X_Forwarded_Server) + ":" + req.getHeader(X_Forwarded_Port);
    } else if(req.getHeader(X_Forwarded_Port) != null) {
      // if we at least have a port, we can use it.
      baseURL += req.getServerName() + ":" + req.getHeader(X_Forwarded_Port);
    } else {
      // Resort to request members
      baseURL += req.getServerName() + ":" + req.getLocalPort();
    }

    // Handle Server context
    if( req.getHeader(X_Forwarded_Context) != null ) {
      baseURL += req.getHeader( X_Forwarded_Context );
    } else {
      baseURL += req.getContextPath();
    }

    return baseURL;
  }


  static class HrefListing {
    @JsonProperty
    String href;

    @JsonProperty
    List<HrefListItem> items;

    HrefListing() {}

    public void setHref(String href) {
      this.href = href;
    }

    public String getHref() {
      return href;
    }

    public void setItems(List<HrefListItem> items) {
      this.items = items;
    }

    public List<HrefListItem> getItems() {
      return items;
    }
  }

  static class HrefListItem {
    @JsonProperty
    String href;

    @JsonProperty
    String name;

    HrefListItem() {}

    HrefListItem(String href, String name) {
      this.href = href;
      this.name = name;
    }

    public void setHref(String href) {
      this.href = href;
    }

    public String getHref() {
      return href;
    }

    public void setName(String name) {
      this.name = name;
    }
    public String getName() {
      return name;
    }
  }


  @XmlAccessorType(XmlAccessType.NONE)
  public static class SimpleTopology {

    @XmlElement
    private String name;
    @XmlElement
    private String timestamp;
    @XmlElement
    private String defaultServicePath;
    @XmlElement
    private String uri;
    @XmlElement
    private String href;

    public SimpleTopology() {}

    public SimpleTopology(org.apache.hadoop.gateway.topology.Topology t, String uri, String href) {
      this.name = t.getName();
      this.timestamp = Long.toString(t.getTimestamp());
      this.defaultServicePath = t.getDefaultServicePath();
      this.uri = uri;
      this.href = href;
    }

    public String getName() {
      return name;
    }

    public void setName(String n) {
      name = n;
    }

    public String getTimestamp() {
      return timestamp;
    }

    public void setDefaultService(String defaultServicePath) {
      this.defaultServicePath = defaultServicePath;
    }

    public String getDefaultService() {
      return defaultServicePath;
    }

    public void setTimestamp(String timestamp) {
      this.timestamp = timestamp;
    }

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getHref() {
      return href;
    }

    public void setHref(String href) {
      this.href = href;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class SimpleTopologyWrapper{

    @XmlElement(name="topology")
    @XmlElementWrapper(name="topologies")
    private List<SimpleTopology> topologies = new ArrayList<SimpleTopology>();

    public List<SimpleTopology> getTopologies(){
      return topologies;
    }

    public void setTopologies(List<SimpleTopology> ts){
      this.topologies = ts;
    }

  }
}

