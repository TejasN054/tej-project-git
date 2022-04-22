import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.customfields.manager.OptionsManager
import com.atlassian.jira.issue.customfields.option.Options
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.index.IssueIndexingService
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParameters
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.ModifiedValue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.workflow.TransitionOptions

def issueIndexingService = ComponentAccessor.getComponentOfType(IssueIndexingService.class);
ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
IssueManager issueManager = ComponentAccessor.getIssueManager();
Issue immutableIssue = event.issue as Issue;
MutableIssue issue = issueManager.getIssueObject(immutableIssue.getKey());

def customersDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Customer(s)");
def documentationDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Documentation");
def impactAreasDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Impact Areas");
def epicLinkDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Epic Link");
def moduleDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Module");
def teamDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_15102"); //15308 jira-test //jira 15102
def productLineDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Product Line");
def productSuiteDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Product Suite");
def domainDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Domain");

def isFeatureBug = issue.issueType.name.equals("Feature Bug")
def isKnownIssue = issue.issueType.name.equals("Known Issue")
def isProductionBug = issue.issueType.name.equals("Production Bug")
def isProductionPatch = issue.issueType.name.equals("Production Patch")
def isRCBug = issue.issueType.name.equals("RC Bug")

def isEpic = issue.issueType.name.equals("Epic");
def isStory = issue.issueType.name.equals("Story");

def isBugInEpic = isFeatureBug || isKnownIssue;
def isBug = isBugInEpic || isProductionBug || isProductionPatch || isRCBug;

log.error("... issue: " + issue.key);

if (isStory || isBug) {
    log.error("... is bug or story: " + issue.key);
    def storyOrBugFieldChanged = false;
    
    if (isBug) {
       // bug target version
       def cfDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Target Version");
       def cfValue = issue.getCustomFieldValue(cfDef);
       if (isFeatureBug || isKnownIssue) {
          if (cfValue != null) {
             issue.setCustomFieldValue(cfDef, null);
             storyOrBugFieldChanged = true;
          }
       }
       if (isFeatureBug) {    
          cfDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Target Version");
          cfValue = issue.getCustomFieldValue(cfDef);
          if (cfValue != null) {
             issue.setCustomFieldValue(cfDef, null);
             storyOrBugFieldChanged = true;
          }
       }
       // bug origin phase
       cfDef = ComponentAccessor.customFieldManager.getCustomFieldObjectByName("Origin Phase");
       def cfConfig = cfDef.getRelevantConfig(issue);
       Option originPhaseOption = (Option) immutableIssue.getCustomFieldValue(cfDef);
       if (originPhaseOption == null || originPhaseOption.getValue() == null) {
           if (isProductionBug || isProductionPatch || isKnownIssue) {
               def value = ComponentAccessor.optionsManager.getOptions(cfConfig)?.find {it.toString() == 'Production'};
               issue.setCustomFieldValue(cfDef, value);
               storyOrBugFieldChanged = true;
           } else if (isRCBug) {
               def value = ComponentAccessor.optionsManager.getOptions(cfConfig)?.find {it.toString() == 'RCT'};
               issue.setCustomFieldValue(cfDef, value);
               storyOrBugFieldChanged = true;
           } else if (isFeatureBug) {
               def value = ComponentAccessor.optionsManager.getOptions(cfConfig)?.find {it.toString() == 'Feature'};
               issue.setCustomFieldValue(cfDef, value);
               storyOrBugFieldChanged = true;
           }
       }
    }
       
    if (isStory || isBugInEpic) {
        //roll up Documentation to parent Epic
        //roll up Impact Areas to parent Epic
        //roll up Customer(s) to parent epic
        def epicLinkVal = issue.getCustomFieldValue(epicLinkDef);

        if (epicLinkVal != null) {
            def epicLearnedDocumentation = false;
            def epicLearnedImpactAreas = false;
            def epicLearnedCustomers = false;

            String epicKey = epicLinkVal;
            log.error(" epic key: " + epicKey);
            MutableIssue epic = issueManager.getIssueObject(epicKey);
            
            //collect the docmentation already on the parent epic
            Set<Option> epicDocumentation = new HashSet<Option>();
            Object documentationValue = epic.getCustomFieldValue(documentationDef);
            if (documentationValue instanceof Collection) {
                Collection<Option> coll = (Collection)documentationValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        epicDocumentation.add(opt);
                    }
                }
            }            

            //collect the impact areas already on the parent epic
            Set<Option> epicImpactAreas = new HashSet<Option>();
            Object impactAreasValue = epic.getCustomFieldValue(impactAreasDef);
            if (impactAreasValue instanceof Collection) {
                Collection<Option> coll = (Collection)impactAreasValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        epicImpactAreas.add(opt);
                    }
                }
            }

            //collect the customers already on the parent epic
            Set<Option> epicCustomers = new HashSet<Option>();
            Object customersValue = epic.getCustomFieldValue(customersDef);
            if (customersValue instanceof Collection) {
                Collection<Option> coll = (Collection)customersValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        epicCustomers.add(opt);
                    }
                }
            }

            //iterate the documentation from this issue and add to parent collection
            documentationValue = issue.getCustomFieldValue(documentationDef);
            if (documentationValue instanceof Collection) {
                Collection<Option> coll = (Collection)documentationValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        if (!"No Documentation Required".equals(opt.getValue() as String)) {
                            if (epicDocumentation.add(opt)) {
                               log.error("... epic to learn documentation: " + opt.getValue());
                               epicLearnedDocumentation = true; //indicate we should update the parent epic; parent to documentation from this issue
                            }
                        }
                    }
                }
            }

            //iterate the impact areas from this issue and add to parent collection
            impactAreasValue = issue.getCustomFieldValue(impactAreasDef);
            if (impactAreasValue instanceof Collection) {
                Collection<Option> coll = (Collection)impactAreasValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        if (epicImpactAreas.add(opt)) {
                           log.error("... epic to learn impact: " + opt.getValue());
                           epicLearnedImpactAreas = true; //indicate we should update the parent epic; parent to learn an impact from this issue
                        }
                    }
                }
            }

            //iterate the customers from this issue and add to parent collection
            customersValue = issue.getCustomFieldValue(customersDef);
            if (customersValue instanceof Collection) {
                Collection<Option> coll = (Collection)customersValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        if (epicCustomers.add(opt)) {
                           log.error("... epic to learn customers: " + opt.getValue());
                           epicLearnedCustomers = true; //indicate we should update the parent epic; parent to learn an customers from this issue
                        }
                    }
                }
            }
            
            if (isStory) {
               //copy driver from epic if only if story has no driver
                Object driverDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Driver");
                Object cfVal = issue.getCustomFieldValue(driverDef)
                HashMap<String, Option> driverEntries = (HashMap<String, Option>) cfVal
                if (!driverEntries) {
                    Object epicDriver = epic.getCustomFieldValue(driverDef);
                    issue.setCustomFieldValue(driverDef, epicDriver); 
                    storyOrBugFieldChanged = true;
                }

                //copy qamanager from epic if only if story has no qamanager
                Object qamgrDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("QA Managers");
                Object qacfVal = issue.getCustomFieldValue(qamgrDef) as ArrayList <ApplicationUser>
                if (!qacfVal) {
                    Object epicQamgr = epic.getCustomFieldValue(qamgrDef) as ArrayList <ApplicationUser>;
                    issue.setCustomFieldValue(qamgrDef, epicQamgr); 
                    storyOrBugFieldChanged = true;
                }


            }

            //copy fields from epic down to this issue        
            Option epicModule = (Option)epic.getCustomFieldValue(moduleDef);
            Option issueModule = (Option)issue.getCustomFieldValue(moduleDef);

            Option epicTeam = (Option)epic.getCustomFieldValue(teamDef);
            Option issueTeam = (Option)issue.getCustomFieldValue(teamDef);

            Option epicProductLine = (Option)epic.getCustomFieldValue(productLineDef);
            Option issueProductLine = (Option)issue.getCustomFieldValue(productLineDef);

            Option epicProductSuite = (Option)epic.getCustomFieldValue(productSuiteDef);
            Option issueProductSuite = (Option)issue.getCustomFieldValue(productSuiteDef);

            Option epicDomain = (Option)epic.getCustomFieldValue(domainDef);
            Option issueDomain = (Option)issue.getCustomFieldValue(domainDef);

            //copy fields from epic down to this issue if they do not match
            if (epicModule == null || issueModule == null || !epicModule.getValue().equals(issueModule.getValue())) {
                //update Module on this issue from parent epic
                issue.setCustomFieldValue(moduleDef, epicModule);
                storyOrBugFieldChanged = true;
            }
            if (epicTeam == null || issueTeam == null || !epicTeam.getValue().equals(issueTeam.getValue())) {
                //update Team on this issue from parent epic
                issue.setCustomFieldValue(teamDef, epicTeam);
                storyOrBugFieldChanged = true;
            }
            if (epicProductLine == null || issueProductLine == null || !epicProductLine.getValue().equals(issueProductLine.getValue())) {
                //update ProductLine on this issue from parent epic
                issue.setCustomFieldValue(productLineDef, epicProductLine);
                storyOrBugFieldChanged = true;
            }
            if (epicProductSuite == null || issueProductSuite == null || !epicProductSuite.getValue().equals(issueProductSuite.getValue())) {
                //update ProductSuite on this issue from parent epic
                issue.setCustomFieldValue(productSuiteDef, epicProductSuite);
                storyOrBugFieldChanged = true;
            }
            if (epicDomain == null || issueDomain == null || !epicDomain.getValue().equals(issueDomain.getValue())) {
                //update Domain on this issue from parent epic
                issue.setCustomFieldValue(domainDef, epicDomain);
                storyOrBugFieldChanged = true;
            }
            
            //update documentation on parent Epic
            if (epicLearnedDocumentation) {
                epic.setCustomFieldValue(documentationDef, epicDocumentation);
            }
            //update impact areas on parent Epic
            if (epicLearnedImpactAreas) {
                epic.setCustomFieldValue(impactAreasDef, epicImpactAreas);
            }
            //update customers on parent Epic
            if (epicLearnedCustomers) {
                epic.setCustomFieldValue(customersDef, epicCustomers);
            }
            if (epicLearnedDocumentation || epicLearnedImpactAreas || epicLearnedCustomers) {
               //save the epic
               issueIndexingService.reIndex(issueManager.updateIssue(user, epic, EventDispatchOption.DO_NOT_DISPATCH, false));  
               log.error("... epic updated: " + epic.key);
            }

        }   
    }
    
    if (storyOrBugFieldChanged) {
       //update this story or bug issue
       issueIndexingService.reIndex(issueManager.updateIssue(user, issue, EventDispatchOption.DO_NOT_DISPATCH, false));  
       log.error("... issue updated: " + issue.key);
    }
    
} else if (isEpic) {
    log.error("... is epic: " + issue.key);
    MutableIssue epic = issueManager.getIssueObject(issue.id);

    //catpure the values for Module, Domain, Team, Product Line, Product Suite on the Epic
    Option module = (Option)epic.getCustomFieldValue(moduleDef);
    Option team = (Option)epic.getCustomFieldValue(teamDef);
    Option productLine = (Option)epic.getCustomFieldValue(productLineDef);
    Option productSuite = (Option)epic.getCustomFieldValue(productSuiteDef);
    Option domain = (Option)epic.getCustomFieldValue(domainDef);

    //collect the docmentation already on the Epic
    Set<Option> epicDocumentation = new HashSet<Option>();
    Object documentationValue = epic.getCustomFieldValue(documentationDef);
    if (documentationValue instanceof Collection) {
        Collection<Option> coll = (Collection)documentationValue;
        for (Object obj : coll) {
            if (obj instanceof Option) {
                Option opt = (Option) obj;
                epicDocumentation.add(opt);
            }
        }
    }            

    //collect the Impact Areas already on the Epic
    Set<Option> impactAreas = new HashSet<Option>();
    Object value = epic.getCustomFieldValue(impactAreasDef);
    if (value instanceof Collection) {
        Collection<Option> coll = (Collection)value;
        for (Object obj : coll) {
            if (obj instanceof Option) {
                Option opt = (Option) obj;
                impactAreas.add(opt);
            }
        }
    }

    //collect the Customers already on the Epic
    Set<Option> epicCustomers = new HashSet<Option>();
    Object customersValue = epic.getCustomFieldValue(customersDef);
    if (customersValue instanceof Collection) {
        Collection<Option> coll = (Collection)customersValue;
        for (Object obj : coll) {
            if (obj instanceof Option) {
                Option opt = (Option) obj;
                epicCustomers.add(opt);
            }
        }
    }

    //get issues in Epic
    def epicLearnedDocumentation = false;
    def epicLearnedImpactAreas = false;
    def epicLearnedCustomers = false;
    Collection<MutableIssue> issuesToUpdate = new ArrayList<MutableIssue>();
    IssueLinkManager issueLinkManager = ComponentAccessor.issueLinkManager
    issueLinkManager.getOutwardLinks(issue.id).each {issueLink ->
        if (issueLink.issueLinkType.name == "Epic-Story Link" || issueLink.issueLinkType.name == "Epic-Feature Bug Link" || issueLink.issueLinkType.name == "Epic-Known Issue Link") {
            Issue linkedIssue = issueLink.destinationObject;
            
            //capture the driver from epic if only if story has no driver
            Object driverDef = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Driver");
            Object epicDriverToPopulateStoryWith = null;
            if (isStory) {
                Option storyDriver = (Option)issue.getCustomFieldValue(driverDef);
                if (!storyDriver) {
                    epicDriverToPopulateStoryWith = epic.getCustomFieldValue(driverDef);                   
                }
            }

            //catpure the values for Module, Domain, Team, Product Line, Product Suite on the linked Story or GTBug
            Option linkedModule = (Option)linkedIssue.getCustomFieldValue(moduleDef);
            Option linkedTeam = (Option)linkedIssue.getCustomFieldValue(teamDef);
            Option linkedProductLine = (Option)linkedIssue.getCustomFieldValue(productLineDef);
            Option linkedProductSuite = (Option)linkedIssue.getCustomFieldValue(productSuiteDef);
            Option linkedDomain = (Option)linkedIssue.getCustomFieldValue(domainDef);

            //copy down Module, Domain, Team, Product Line, Product Suite from Epic to linked
            def fieldChanged = false;
            if (module == null || linkedModule == null || !module.getValue().equals(linkedModule.getValue())) {
                log.error("... module difference: " + linkedIssue.key);
                fieldChanged = true;
            } 
            if (team == null || linkedTeam == null || !team.getValue().equals(linkedTeam.getValue())) {
                log.error("... team difference: " + linkedIssue.key);
                fieldChanged = true;
            }
            if (productLine == null || linkedProductLine == null || !productLine.getValue().equals(linkedProductLine.getValue())) {
                log.error("... productLine difference: " + linkedIssue.key);
                fieldChanged = true;
            }
            if (productSuite == null || linkedProductSuite == null || !productSuite.getValue().equals(linkedProductSuite.getValue())) {
                log.error("... productSuite difference: " + linkedIssue.key);
                fieldChanged = true;
            }
            if (domain == null || linkedDomain == null || !domain.getValue().equals(linkedDomain.getValue())) {            
                log.error("... domain difference: " + linkedIssue.key);
                fieldChanged = true;                
            }
            if (fieldChanged || epicDriverToPopulateStoryWith != null) {
                MutableIssue storyOrBug = issueManager.getIssueObject(linkedIssue.id); //this seems to be an expensive call                
                storyOrBug.setCustomFieldValue(driverDef, epicDriverToPopulateStoryWith); //driver pop is only for stories and only when driver is not set on story
                storyOrBug.setCustomFieldValue(moduleDef, module);
                storyOrBug.setCustomFieldValue(teamDef, team);
                storyOrBug.setCustomFieldValue(productLineDef, productLine);
                storyOrBug.setCustomFieldValue(productSuiteDef, productSuite);
                storyOrBug.setCustomFieldValue(domainDef, domain);
                issuesToUpdate.add(storyOrBug);
            }

            //copy up the Documentation from each issue in epic to Epic            
            documentationValue = linkedIssue.getCustomFieldValue(documentationDef);
            if (documentationValue instanceof Collection) {
                Collection<Option> coll = (Collection)documentationValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        if (!"No Documentation Required".equals(opt.getValue() as String)) {
                            if (epicDocumentation.add(opt)) {
                               log.error("... epic to learn documentation: " + opt.getValue());
                               epicLearnedDocumentation = true; //indicate we should update the parent epic; parent to documentation from this issue
                            }
                        }
                    }
                }
            }

            //copy up the Impact Areas from each issue in epic to Epic
            value = linkedIssue.getCustomFieldValue(impactAreasDef);
            if (value instanceof Collection) {
                Collection<Option> coll = (Collection)value;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        if (impactAreas.add(opt)) {
                            epicLearnedImpactAreas = true;
                        }
                    }
                }
            }

            //copy up the Customers from each issue in epic to Epic            
            customersValue = linkedIssue.getCustomFieldValue(customersDef);
            if (customersValue instanceof Collection) {
                Collection<Option> coll = (Collection)customersValue;
                for (Object obj : coll) {
                    if (obj instanceof Option) {
                        Option opt = (Option) obj;
                        if (epicCustomers.add(opt)) {
                               log.error("... epic to learn customers: " + opt.getValue());
                               epicLearnedCustomers = true; //indicate we should update the parent epic; parent to customers from this issue
                        }
                    }
                }
            }

        }
    } //end issue link discovery loop
    
    //adjust, persist and reindex any issues discovered
    Collection<Issue> issuesToIndex = new ArrayList<Issue>();
    if (epicLearnedDocumentation) {
        log.error("... epic learned documentation");
        epic.setCustomFieldValue(documentationDef, epicDocumentation);
    }
    if (epicLearnedImpactAreas) {
        log.error("... epic learned impact");
        epic.setCustomFieldValue(impactAreasDef, impactAreas);
    }
    if (epicLearnedCustomers) {
        log.error("... epic learned customers");
        epic.setCustomFieldValue(customersDef, epicCustomers);
    }
    if (epicLearnedDocumentation || epicLearnedImpactAreas || epicLearnedCustomers) {
        log.error("... saving epic");
        issuesToIndex.add(issueManager.updateIssue(user, epic, EventDispatchOption.DO_NOT_DISPATCH, false));              
    }
    
    if (!issuesToUpdate.isEmpty()) {
        //update each linked issue in this epic issue
        for (MutableIssue storyOrBug : issuesToUpdate) {
            log.error("... updating issue: " + storyOrBug.key);   
            issuesToIndex.add(issueManager.updateIssue(user, storyOrBug, EventDispatchOption.DO_NOT_DISPATCH, false));  
        }
    }

    if (!issuesToIndex.isEmpty()) {
        log.error("... indexing " + issuesToIndex.size() + " issues");
        //reindex
        issueIndexingService.reIndexIssueObjects(issuesToIndex);
    }

}