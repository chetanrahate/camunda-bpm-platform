package org.activiti.cycle.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.activiti.cycle.ArtifactType;
import org.activiti.cycle.Content;
import org.activiti.cycle.CycleService;
import org.activiti.cycle.CycleTag;
import org.activiti.cycle.RepositoryArtifact;
import org.activiti.cycle.RepositoryArtifactLink;
import org.activiti.cycle.RepositoryConnector;
import org.activiti.cycle.RepositoryException;
import org.activiti.cycle.RepositoryFolder;
import org.activiti.cycle.RepositoryNode;
import org.activiti.cycle.RepositoryNodeCollection;
import org.activiti.cycle.RepositoryNodeNotFoundException;
import org.activiti.cycle.impl.conf.ConfigurationContainer;
import org.activiti.cycle.impl.connector.demo.DemoConnectorConfiguration;
import org.activiti.cycle.impl.connector.fs.FileSystemConnectorConfiguration;
import org.activiti.cycle.impl.connector.signavio.SignavioConnectorConfiguration;
import org.activiti.cycle.impl.db.CycleConfigurationService;
import org.activiti.cycle.impl.db.CycleLinkService;
import org.activiti.cycle.impl.db.entity.CycleLink;
import org.activiti.cycle.impl.db.impl.CycleConfigurationServiceImpl;
import org.activiti.cycle.impl.db.impl.CycleLinkServiceImpl;
import org.activiti.cycle.impl.plugin.PluginFinder;

/**
 * Connector to represent customized view for a user of cycle to hide all the
 * internal configuration and {@link RepositoryConnector} stuff from the client
 * (e.g. the webapp)
 * 
 * @author bernd.ruecker@camunda.com
 * @author Nils Preusker (nils.preusker@camunda.com)
 */
public class CycleServiceImpl implements CycleService {

  private CycleLinkService linkService;

  private List<RepositoryConnector> repositoryConnectors;

  /**
   * TODO: Check if list roots can return an empty array
   */
  private static final File fsBaseDir = File.listRoots()[0];

  public CycleServiceImpl(List<RepositoryConnector> repositoryConnectors) {

    PluginFinder.checkPluginInitialization();
    this.linkService = new CycleLinkServiceImpl();

    this.repositoryConnectors = repositoryConnectors;
  }

  // bootstrapping for cycle

  /**
   * Provides a static factory method for CycleService instances. Checks whether
   * the HttpSession contains an instance for the specified user name and
   * creates a new instance if none is found.
   * 
   * @param currentUserId the user id of the currently logged in user
   * @param session the HttpSession object from the currently logged in user
   * @return the CycleService instance for the currently logged in user
   */
  public static CycleService getCycleService(String currentUserId, HttpSession session) {
    String key = currentUserId + "_cycleService";

    CycleService cycleService = (CycleService) session.getAttribute(key);

    if (cycleService == null) {
      PluginFinder.registerServletContext(session.getServletContext());

      ConfigurationContainer configuration = loadUserConfiguration(currentUserId);
      cycleService = new CycleServiceImpl(configuration.getConnectorList());

      // TODO: Correct user / password handling!!!!
      cycleService.login(currentUserId, currentUserId);

      session.setAttribute(key, cycleService);
    }
    return cycleService;
  }

  /**
   * Loads the configuration for this user. If no configuration exists, a demo
   * configuration is created and stored in the database.
   * 
   * @param currentUserId the id of the currently logged in user
   */
  private static ConfigurationContainer loadUserConfiguration(String currentUserId) {
    PluginFinder.checkPluginInitialization();
    CycleConfigurationService configService = new CycleConfigurationServiceImpl(null);
    ConfigurationContainer configuration;
    try {
      configuration = configService.getConfiguration(currentUserId);
    } catch (RepositoryException e) {
      configuration = createDefaultDemoConfiguration(currentUserId);
      configService.saveConfiguration(configuration);
    }
    return configuration;
  }

  private static ConfigurationContainer createDefaultDemoConfiguration(String currentUserId) {
    ConfigurationContainer configuration = new ConfigurationContainer(currentUserId);
    configuration.addRepositoryConnectorConfiguration(new DemoConnectorConfiguration("demo"));
    configuration.addRepositoryConnectorConfiguration(new SignavioConnectorConfiguration("signavio", "http://localhost:8080/activiti-modeler/"));
    configuration.addRepositoryConnectorConfiguration(new FileSystemConnectorConfiguration("files", fsBaseDir));
    return configuration;
  }

  // implementation of CycleService methods

  /**
   * login into all repositories configured (if no username and password was
   * provided by the configuration already).
   * 
   * TODO: Make more sophisticated. Questions: What to do if one repo cannot
   * login?
   */
  public boolean login(String username, String password) {
    for (RepositoryConnector connector : this.repositoryConnectors) {
      // TODO: What if one repository failes? Try loading the other ones and
      // remove the failing from the repo list? Example: Online SIgnavio when
      // offile
      connector.login(username, password);
    }
    return true;
  }

  /**
   * commit pending changes in all repository connectors configured
   */
  public void commitPendingChanges(String connectorId, String comment) {
    for (RepositoryConnector connector : this.repositoryConnectors) {
      connector.commitPendingChanges(comment);
    }
  }

  public RepositoryNodeCollection getChildren(String connectorId, String nodeId) {
    // special handling for root
    if ("/".equals(connectorId)) {
      return getRepoRootFolders();
    }

    RepositoryConnector connector = getRepositoryConnector(connectorId);
    return connector.getChildren(nodeId);
  }

  public RepositoryNodeCollection getRepoRootFolders() {
    ArrayList<RepositoryNode> nodes = new ArrayList<RepositoryNode>();
    for (RepositoryConnector connector : this.repositoryConnectors) {

      RepositoryFolderImpl folder = new RepositoryFolderImpl(connector.getConfiguration().getId(), "/");
      folder.getMetadata().setName(connector.getConfiguration().getName());
      folder.getMetadata().setParentFolderId("/");
      nodes.add(folder);

    }
    return new RepositoryNodeCollectionImpl(nodes);
  }

  public RepositoryArtifact getRepositoryArtifact(String connectorId, String artifactId) {
    RepositoryConnector connector = getRepositoryConnector(connectorId);
    RepositoryArtifact repositoryArtifact = connector.getRepositoryArtifact(artifactId);
    return repositoryArtifact;
  }

  public Content getRepositoryArtifactPreview(String connectorId, String artifactId) throws RepositoryNodeNotFoundException {
    RepositoryConnector connector = getRepositoryConnector(connectorId);
    return connector.getRepositoryArtifactPreview(artifactId);
  }

  public RepositoryFolder getRepositoryFolder(String connectorId, String artifactId) {
    RepositoryConnector connector = getRepositoryConnector(connectorId);
    RepositoryFolder repositoryFolder = connector.getRepositoryFolder(artifactId);
    return repositoryFolder;
  }

  public RepositoryArtifact createArtifact(String connectorId, String containingFolderId, String artifactName, String artifactType, Content artifactContent)
          throws RepositoryNodeNotFoundException {
    return getRepositoryConnector(connectorId).createArtifact(containingFolderId, artifactName, artifactType, artifactContent);
  }

  public RepositoryArtifact createArtifactFromContentRepresentation(String connectorId, String containingFolderId, String artifactName, String artifactType,
          String contentRepresentationName, Content artifactContent) throws RepositoryNodeNotFoundException {
    return getRepositoryConnector(connectorId).createArtifactFromContentRepresentation(containingFolderId, artifactName, artifactType,
            contentRepresentationName, artifactContent);
  }

  public void updateContent(String connectorId, String artifactId, Content content) throws RepositoryNodeNotFoundException {
    RepositoryConnector connector = getRepositoryConnector(connectorId);
    connector.updateContent(artifactId, content);
  }

  public void updateContent(String connectorId, String artifactId, String contentRepresentationName, Content content) throws RepositoryNodeNotFoundException {
    RepositoryConnector connector = getRepositoryConnector(artifactId);
    connector.updateContent(artifactId, contentRepresentationName, content);
  }

  public RepositoryFolder createFolder(String connectorId, String parentFolderId, String name) throws RepositoryNodeNotFoundException {
    return getRepositoryConnector(connectorId).createFolder(parentFolderId, name);
  }

  public void deleteArtifact(String connectorId, String artifactId) {
    getRepositoryConnector(connectorId).deleteArtifact(artifactId);
  }

  public void deleteFolder(String connectorId, String folderId) {
    getRepositoryConnector(connectorId).deleteFolder(folderId);
  }

  public Content getContent(String connectorId, String artifactId, String representationName) throws RepositoryNodeNotFoundException {
    return getRepositoryConnector(connectorId).getContent(artifactId, representationName);
  }

  public void executeParameterizedAction(String connectorId, String artifactId, String actionId, Map<String, Object> parameters) throws Exception {
    RepositoryConnector connector = getRepositoryConnector(connectorId);

    // TODO: (Nils Preusker, 20.10.2010), find a better way to solve this!
    for(String key : parameters.keySet()) {
      if(key.equals("targetConnectorId")) {
        RepositoryConnector targetConnector = getRepositoryConnector((String)parameters.get(key));
        parameters.put(key, targetConnector);
      }
    }
    
    connector.executeParameterizedAction(artifactId, actionId, parameters);
  }

  public List<ArtifactType> getSupportedArtifactTypes(String connectorId, String folderId) {
    if (folderId == null || folderId.length() <= 1) {
      // "virtual" root folder doesn't support any artifact types
      return new ArrayList<ArtifactType>();
    }
    return getRepositoryConnector(connectorId).getSupportedArtifactTypes(folderId);
  }

  // RepositoryArtifactLink specific methods

  public void addArtifactLink(RepositoryArtifactLink repositoryArtifactLink) {
    CycleLink cycleLink = new CycleLink();

    cycleLink.setId(repositoryArtifactLink.getId());

    // set source artifact attributes
    cycleLink.setSourceConnectorId(repositoryArtifactLink.getSourceArtifact().getConnectorId());
    cycleLink.setSourceArtifactId(repositoryArtifactLink.getSourceArtifact().getOriginalNodeId());
    cycleLink.setSourceElementId(repositoryArtifactLink.getSourceElementId());
    cycleLink.setSourceElementName(repositoryArtifactLink.getSourceElementName());
    cycleLink.setSourceRevision(repositoryArtifactLink.getSourceArtifact().getArtifactType().getRevision());

    // set target artifact attributes
    cycleLink.setTargetConnectorId(repositoryArtifactLink.getTargetArtifact().getConnectorId());
    cycleLink.setTargetArtifactId(repositoryArtifactLink.getTargetArtifact().getOriginalNodeId());
    cycleLink.setTargetElementId(repositoryArtifactLink.getTargetElementId());
    cycleLink.setTargetElementName(repositoryArtifactLink.getTargetElementName());
    cycleLink.setTargetRevision(repositoryArtifactLink.getTargetArtifact().getArtifactType().getRevision());

    // TODO: decide whether we will use these attributes or get rid of them.
    // Just setting defaults to prevent null values for now.
    cycleLink.setDescription("");
    cycleLink.setLinkedBothWays(false);
    cycleLink.setLinkType("");

    this.linkService.updateCycleLink(cycleLink);
  }

  public List<RepositoryArtifactLink> getArtifactLinks(String sourceConnectorId, String sourceArtifactId) {
    List<RepositoryArtifactLink> artifactLinks = new ArrayList<RepositoryArtifactLink>();

    // TODO: query should be updated to use connectorId and artifactId
    List<CycleLink> linkResultList = this.linkService.getCycleLinks(sourceArtifactId);

    if (linkResultList != null && !linkResultList.isEmpty()) {
      for (CycleLink entity : linkResultList) {
        artifactLinks.add(createLinkDtoFromLinkEntity(entity));
      }
    }

    return artifactLinks;
  }

  public void deleteLink(long linkId) {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }
  public List<RepositoryArtifactLink> getArtifactLinks(String sourceArtifactId) {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  public List<RepositoryArtifactLink> getArtifactLinks(String sourceArtifactId, Long sourceRevision) {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  public List<RepositoryArtifactLink> getArtifactLinks(String sourceArtifactId, Long sourceRevision, String type) {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  // Tag specific methods

  public void addTag(String nodeId, String tagName) {

    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  public void addTag(String nodeId, String tagName, String alias) {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  public void deleteTag(String nodeId, String tagName) {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  public List<CycleTag> getAllTags() {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  public List<CycleTag> getAllTagsIgnoreAlias() {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  public List<CycleTag> getTags(String nodeId) throws RepositoryNodeNotFoundException {
    // TODO: implement
    throw new RuntimeException("Not implemented yet");
  }

  // Private convenience methods

  private RepositoryArtifactLink createLinkDtoFromLinkEntity(CycleLink entity) {
    RepositoryArtifactLink repositoryArtifactLink = new RepositoryArtifactLinkImpl();
    repositoryArtifactLink.setId(entity.getId());
    repositoryArtifactLink.setSourceElementId(entity.getSourceElementId());
    repositoryArtifactLink.setSourceElementName(entity.getSourceElementName());
    repositoryArtifactLink.setTargetElementId(entity.getTargetElementId());
    repositoryArtifactLink.setTargetElementName(entity.getTargetElementName());

    RepositoryArtifact sourceArtifact = null;
    RepositoryArtifact targetArtifact = null;

    for (RepositoryConnector conn : this.repositoryConnectors) {
      if (conn.getConfiguration().getId().equals(entity.getSourceConnectorId())) {
        sourceArtifact = conn.getRepositoryArtifact(entity.getSourceArtifactId());
      }
      if (conn.getConfiguration().getId().equals(entity.getTargetConnectorId())) {
        targetArtifact = conn.getRepositoryArtifact(entity.getTargetArtifactId());
      }
    }

    // TODO: think about exception handling :)
    if (sourceArtifact == null) {
      throw new RuntimeException("Source-artifact with id '" + entity.getSourceArtifactId() + "' not found for artifact-link with id '" + entity.getId() + "'");
    }
    if (targetArtifact == null) {
      throw new RuntimeException("Target-artifact with id '" + entity.getSourceArtifactId() + "' not found for artifact-link with id '" + entity.getId() + "'");
    }

    repositoryArtifactLink.setSourceArtifact(sourceArtifact);
    repositoryArtifactLink.setTargetArtifact(targetArtifact);

    return repositoryArtifactLink;
  }

  private RepositoryConnector getRepositoryConnector(String connectorId) {
    for (RepositoryConnector connector : this.repositoryConnectors) {
      if (connector.getConfiguration().getId().equals(connectorId)) {
        return connector;
      }
    }
    throw new RepositoryException("Couldn't find Repository Connector with id '" + connectorId + "'");
  }
}
