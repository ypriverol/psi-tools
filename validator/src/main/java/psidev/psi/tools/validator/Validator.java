package psidev.psi.tools.validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import psidev.psi.tools.cvrReader.CvRuleReader;
import psidev.psi.tools.cvrReader.CvRuleReaderException;
import psidev.psi.tools.cvrReader.mapping.jaxb.CvMapping;
import psidev.psi.tools.objectRuleReader.ObjectRuleReader;
import psidev.psi.tools.objectRuleReader.ObjectRuleReaderException;
import psidev.psi.tools.objectRuleReader.mapping.jaxb.*;
import psidev.psi.tools.ontology_manager.OntologyManager;
import psidev.psi.tools.ontology_manager.impl.local.OntologyLoaderException;
import psidev.psi.tools.validator.preferences.UserPreferences;
import psidev.psi.tools.validator.rules.codedrule.ObjectRule;
import psidev.psi.tools.validator.rules.cvmapping.CvRule;
import psidev.psi.tools.validator.rules.cvmapping.CvRuleManager;
import psidev.psi.tools.validator.util.ValidatorReport;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * <b>Semantic XML Validator</b>.
 * <p/>
 * Validates a XML document against a set of rules. </p>
 *
 * @author Samuel Kerrien (skerrien@ebi.ac.uk)
 * @author Matthias Oesterheld
 * @version $Id: Validator.java 668 2007-06-29 16:44:18 +0100 (Fri, 29 Jun 2007) skerrien $
 * @since 1.0
 */
public abstract class Validator {

    /**
     * Sets up a logger for that class.
     */
    public static final Log log = LogFactory.getLog( Validator.class );

    private static final Properties validationProps = loadValidationProperties();

    private static Properties loadValidationProperties() {

        //check to see if we have a project-specific configuration file
        URL resource = Validator.class.getClassLoader().getResource("validation.properties");
        //if not, use default config
        if (resource == null) {
            resource = Validator.class.getClassLoader().getResource("config/defaultValidation.properties");
        }
        if (resource == null) {
            log.error("Could not find properties file!");
            throw new IllegalStateException("Could not find properties file!");
        }
        log.info("Validation configuration file: " + resource.toString());

        Properties props = new Properties();
        try {
            props.load(resource.openStream());
        } catch (IOException e) {
            log.error("Could not load properties file: " + resource.toString());
            throw new IllegalStateException("Could not load properties file: " + resource.toString());
        }

        return props;
    }

    private static boolean validationSuccessReporting = loadValidationSuccessReporting();

    public static boolean loadValidationSuccessReporting() {
        String propValue = null;
        if (validationProps != null) {
            propValue = validationProps.getProperty("validation.success.reporting");
        }

        return propValue != null && propValue.equalsIgnoreCase("true");
    }

    public static boolean isValidationSuccessReporting() {
        return validationSuccessReporting;
    }

    public static void setValidationSuccessReporting(boolean validationSuccessReporting) {
        Validator.validationSuccessReporting = validationSuccessReporting;
    }

    /**
     * User preferences.
     * <p/>
     * Initialise to the default values.
     */
    protected UserPreferences userPreferences = new UserPreferences();

    protected OntologyManager ontologyMngr;

    /**
     * The set of rules specific to that Validator.
     * List of ObjectRuleS
     */
    private Set<ObjectRule> rules = new HashSet<>();

    /**
     * The map containing the set of Rules excluded by each imported object rule file
     */
    private Map<String, Set<String>> excludedRules = new HashMap<>();

    /**
     * The list contains all the excluded rules (recursively) for one import. It will be cleaned at each time we start the first import
     */
    private Stack<Set<String>> stackOfExcludedRulesPerImport = new Stack<>();

    /**
     * Contains the URL for the rules to import
     */
    private HashMap<String, String> urlsForTheImportedRules = new HashMap<>();

    /**
     * The type of the file to import in the object-rule config file is a resource
     */
    private static final String RESOURCE = "resource";

    /**
     * The type of the file to import in the object-rule config file is a local file
     */
    private static final String LOCAL_FILE = "file";

    /**
     * The type of the file to import in the object-rule config file is a file
     */
    private static final String FILE = "url";

    /**
     * List holding the CvRuleS.
     */
    private CvRuleManager cvRuleManager;

    //////////////////////
    // Constructor

    public Validator( InputStream ontoConfig, InputStream cvRuleConfig, InputStream objectRuleConfig ) throws ValidatorException, OntologyLoaderException {
        this( ontoConfig, cvRuleConfig );

        // if specified, load objectRules
        setObjectRules( objectRuleConfig );
    }

    public Validator( InputStream ontoConfig, InputStream cvRuleConfig ) throws ValidatorException, OntologyLoaderException {
        // load the ontologies
        this( ontoConfig );

        // if specified, load cvRules
        if ( cvRuleConfig != null ) {
            try {
                setCvMappingRules( cvRuleConfig );
            } catch ( CvRuleReaderException e ) {
                throw new ValidatorException( "CvMappingException while trying to load the CvRules.", e );
            }
        }
    }

    public Validator( InputStream ontoConfig ) throws OntologyLoaderException {
        // load the ontologies
        setOntologyManager( ontoConfig );
    }

    /**
     * Create a new Validator with preinstantiated OntlogyManager, cvMapping rules and object rules
     * @param ontologyManager : a preinstantiated OntologyManager. Can't be null
     * @param cvMapping : the cvMapping
     * @param objectRules : the collection of preinstantiated ObjectRules
     */
    public Validator (OntologyManager ontologyManager, CvMapping cvMapping, Collection<ObjectRule> objectRules){
        setOntologyManager(ontologyManager);
        setCvMappingRules(ontologyManager, cvMapping);
        setObjectRules(objectRules);
    }

    ////////////////////////
    // Getters and Setters

    public OntologyManager getOntologyMngr() {
        return ontologyMngr;
    }

    public void setOntologyManager( InputStream ontoConfig ) throws OntologyLoaderException {
        ontologyMngr = new OntologyManager( ontoConfig );
    }

    /**
     * Set the ontology manager of this object. If the ontologyManager is null, throws an IllegalArgumentException.
     * @param ontoManager : the preinstantiated ontology manager. Can't be null
     */
    public void setOntologyManager( OntologyManager ontoManager ){

        if (ontoManager == null){
            throw new IllegalArgumentException("The OntologyManager of a Validator can't be null.");
        }

        ontologyMngr = ontoManager;
    }

    public CvRuleManager getCvRuleManager() {
        return cvRuleManager;
    }

    protected void instantiateCvRuleManager(OntologyManager manager, CvMapping cvMappingRules){
        this.cvRuleManager = new CvRuleManager(manager, cvMappingRules);
    }

    protected void setCvRuleManager(CvRuleManager manager){
        if (manager != null){
            this.cvRuleManager = manager;
        }
    }

    /**
     * Set a cvMapping file and build the corresponding cvRuleManager.
     *
     * @param cvIs InputStream form the configuration file defining the CV Mapping to be applied as rule.
     * @throws CvRuleReaderException if one cannot parse the given file.
     */
    public void setCvMappingRules( InputStream cvIs ) throws CvRuleReaderException {
        CvRuleReader reader = new CvRuleReader();
        instantiateCvRuleManager( ontologyMngr, reader.read( cvIs ) );
    }

    /**
     *  Set the CVRules of the CVRuleManager. If cvMappingRules is null log a warning message.
     * If the cvMappingRules doesn't contain any CVMappingRuleList object, throws an IllegalArgumentException
     * @param cvMapping : the cvMapping
     * @param ontologymanager : the ontologyManager
     */
    public void setCvMappingRules( OntologyManager ontologymanager, CvMapping cvMapping ) {

        if (cvMapping != null && ontologymanager != null){
            instantiateCvRuleManager(ontologymanager, cvMapping);
        }
        else if (ontologymanager == null){
            throw new IllegalArgumentException("The OntologyManager is null, we can't create a new CvRuleManager.");
        }
        else {
            log.info("No CvMapping rule has been loaded.");
        }
    }

    public Set<ObjectRule> getObjectRules() {
        return rules;
    }

    /**
     *  Set the object rules of this validator.
     * @param objectRules : the preinstantiated object rules
     */
    public void setObjectRules(Collection<ObjectRule> objectRules){

        if (objectRules != null){
            this.rules.clear();

            for (ObjectRule rule : objectRules){
                if (rule != null){
                    this.rules.add(rule);
                }
            }
        }
        else {
            log.info("No object rule has been loaded.");
        }

        if (this.rules.isEmpty()){
            log.info("The list of object rules is empty.");
        }
    }

    /**
     *
     * @param className
     * @return  true if the className matches a the class name of one of the ObjectRules in the list of instantiated rules.
     */
    private ObjectRule isTheRuleAlreadyInstantiated(String className){

        for (ObjectRule rule : this.rules){
            if (rule.getClass().getName().equals(className)){
                return rule;
            }
        }
        return null;
    }

    private boolean isTheRuleExcludedFromImport(String className){

        if (!this.stackOfExcludedRulesPerImport.isEmpty()){
            Set<String> excludedRules = this.stackOfExcludedRulesPerImport.peek();

            for (String rule : excludedRules){

                if (rule.equals(className)){
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Instantiates the appropriate Rule from the jaxb Rule 'rule' and add it to the list of rules.
     * @param rule
     * @throws ValidatorException
     */
    private void addRule(Rule rule, String name) throws ValidatorException {
        String className = null;
        Class ruleClass = null;
        try {
            className = rule.getClazz();
            ObjectRule alreadyImportedRule = isTheRuleAlreadyInstantiated(className);
            if (alreadyImportedRule == null){
                if (!isTheRuleExcludedFromImport(className)){
                    ruleClass = Class.forName( className );
                    Constructor c = ruleClass.getConstructor( OntologyManager.class );
                    ObjectRule r = ( ObjectRule ) c.newInstance( ontologyMngr );

                    if (name != null){
                        r.setScope(name);
                    }

                    this.rules.add( r );
                    if ( log.isInfoEnabled() ) {
                        log.trace( "Added rule: " + r.getClass() );
                    }
                }
                else {
                    log.trace("Excluded Rule: " + className);
                }
            }
            else{
                log.trace( "The rule " + className + " has already been added with a scope " + alreadyImportedRule.getScope() + " and will not be reimported with a label " + name);
            }

        } catch (Exception e) {
            throw new ValidatorException( "Error instantiating rule (" + className + ")", e );
        }

    }

    /**
     * Load a file from a URL
     * @param urlName
     * @throws FileNotFoundException
     * @throws ValidatorException
     * @throws IOException
     */
    private void loadFileFrom(String urlName) throws ValidatorException, IOException {

        URL url = new URL(urlName);

        InputStream is = url.openStream();
        setObjectRules(is);
        is.close();
    }

    /**
     * Look if this file is a local file
     * @param urlName
     * @throws FileNotFoundException
     * @throws ValidatorException
     * @throws IOException
     */
    private boolean isALocalFile(String urlName) {

        File file = new File(urlName);

        if (file.exists()){
            return true;
        }
        return false;
    }

    /**
     * Load a local file
     * @param urlName
     * @throws FileNotFoundException
     * @throws ValidatorException
     * @throws IOException
     */
    private void loadLocalFileFrom(String urlName) throws ValidatorException, IOException {

        File file = new File(urlName);

        if (file.exists()){
            FileInputStream is = new FileInputStream(file);

            setObjectRules(is);

            is.close();
        }
    }

    private boolean processExcludedRulesDuringImport(Import importedRules){
        if (importedRules.getExclude() != null){
            Exclude exclusion = importedRules.getExclude();

            if (exclusion.getRule() != null){
                Set<String> excludedRulesDuringImport = new HashSet<>();

                if (!this.stackOfExcludedRulesPerImport.isEmpty()){
                    excludedRulesDuringImport.addAll(this.stackOfExcludedRulesPerImport.peek());
                }
                
                String fileName = importedRules.getRules();

                if (!this.excludedRules.containsKey(fileName)){
                    this.excludedRules.put(fileName, excludedRulesDuringImport);
                }

                for (Rule rule : exclusion.getRule()){
                    excludedRulesDuringImport.add(rule.getClazz());
                }

                this.stackOfExcludedRulesPerImport.push(excludedRulesDuringImport);

                return true;
            }
            else {
                return false;
            }
        }
        else {
            return false;
        }
    }

    /**
     * First look for a resource file of Validator, then a local file and finally a file on internet.
     *
     * If some of the imported rules are excluded, return true.
     * @param urlName
     * @param typeOfImport
     * @throws ValidatorException
     */
    private void importRulesFromFile(String urlName, String typeOfImport) throws ValidatorException{

        try {
            boolean isImportDone = false;

            if (typeOfImport != null){

                switch (typeOfImport.toLowerCase()) {
                    case RESOURCE:
                        //URL url = Validator.class.getClassLoader().getResource( urlName );
                        URL url = this.getClass().getClassLoader().getResource(urlName);

                        if (url != null) {
                            InputStream is = url.openStream();
                            setObjectRules(is);
                            isImportDone = true;
                            is.close();
                        } else {
                            log.warn(" The file (" + urlName + ") to import is a resource (" + typeOfImport + ") but was not found. Try to load this url as a local file and if not, try to read the url on internet.");
                        }
                        break;
                    case LOCAL_FILE:
                        if (isALocalFile(urlName)) {
                            loadLocalFileFrom(urlName);
                            isImportDone = true;
                        } else {
                            log.warn(" The file (" + urlName + ") to import is a local file (" + typeOfImport + ") but was not found. Try to read the url on internet.");
                        }
                        break;
                    case FILE:
                        loadFileFrom(urlName);
                        isImportDone = true;
                        break;
                    default:
                        log.warn(" The type of the file (" + urlName + ") to import " + typeOfImport + " is not known. You can choose 'resource' (resource of the validator), 'file' (local file on your machine), or 'url' (look on internet)." +
                                " First we will try to load this file as a resource. If not found, we will look the local files and then we will try on internet.");
                        break;
                }
            }
            else {
                log.warn(" The type of the file (" + urlName + ") to import " + typeOfImport + " is not precised. You can choose 'resource' (resource of the validator), 'file' (local file on your machine), or 'url' (look on internet)." +
                        " First we will try to load this file as a resource. If not found, we will look the local files and then we will try on internet.");
            }

            if (!isImportDone){
                //URL url = Validator.class.getClassLoader().getResource( urlName );
                URL url = this.getClass().getResource( urlName );

                if (url != null){
                    InputStream is = url.openStream();
                    setObjectRules(is);
                    is.close();
                }
                else{
                    if (isALocalFile(urlName)){
                        loadLocalFileFrom(urlName);
                    }
                    else {
                        loadFileFrom(urlName);
                    }
                }

            }
        } catch (MalformedURLException e) {
            throw new ValidatorException("The URL " + urlName + " is malformed and can't be read",e);
        }
        catch (IOException e){
            throw new ValidatorException("The URL " + urlName + " can't be read",e);
        }
    }

    /**
     * Parse the configuration file and update the list of Rule of the current Validator.
     * <p/>
     * Each Rule is initialised with a Map of Ontologies that have been read from the config file.
     *
     * @param configFile the configuration file.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    public void setObjectRules( InputStream configFile ) throws ValidatorException {
        // set -> replace whatever there might have been

        String name = null;

        if( configFile != null ) {
            ObjectRuleReader reader = new ObjectRuleReader();
            try {
                final ObjectRuleList rules = reader.read( configFile );
                name = rules.getName();

                ImportRuleList rulesToImport = rules.getImportRuleList();
                if(rulesToImport != null){

                    for (Import importedRules : rulesToImport.getImport()){
                        boolean hasExcludedARule = false;

                        String linkToRules = importedRules.getRules();
                        String typeOfImport = importedRules.getType();

                        if (!this.urlsForTheImportedRules.containsKey(linkToRules)){
                            hasExcludedARule = processExcludedRulesDuringImport(importedRules);

                            importRulesFromFile(linkToRules, typeOfImport);
                            this.urlsForTheImportedRules.put(linkToRules, name);

                            if (!this.stackOfExcludedRulesPerImport.isEmpty() && hasExcludedARule){
                                this.stackOfExcludedRulesPerImport.pop();
                            }
                        }
                        else{
                            log.warn("The " + name != null ? name : "" + " rules from the url " + linkToRules + " have already been imported in a previous file (name = " + this.urlsForTheImportedRules.get(linkToRules) + "). We cannot do the import twice.");
                        }

                    }
                }

                for ( Rule rule : rules.getRule() ) {
                    addRule(rule, name);
                }

                if (this.stackOfExcludedRulesPerImport.isEmpty() && !this.excludedRules.isEmpty()){

                     checkAllExcludedRules();
                     this.excludedRules.clear();
                }

            } catch ( ObjectRuleReaderException e ) {
                throw new ValidatorException( "Error during the parsing of "+ configFile.toString(), e );
            }
        } else {
            if ( log.isDebugEnabled() ) {
                log.debug( "No Object rules were configured in this validator." );
            }
        }
    }

    private void checkAllExcludedRules(){

         for (Map.Entry<String, Set<String>> entry : this.excludedRules.entrySet()){
              for (String rule : entry.getValue()){
                   for (ObjectRule objectRule : this.rules){
                       if (objectRule.getClass().getName().equals(rule)){
                           log.warn("The object rule " + rule + " were excluded from the file " + entry.getKey() + " but was imported from another file. It is maybe not what you want.");
                       }
                   }
              }
         }
    }

    public UserPreferences getUserPreferences() {
        return userPreferences;
    }

    public void setUserPreferences( UserPreferences userPreferences ) {
        this.userPreferences = userPreferences;
    }

    ////////////////////////////////////
    // Validation against object rule

    /**
     * Validates a collection of objects against all the (object) rules.
     *
     * @param col collection of objects to check on.
     * @return collection of validator messages.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    public Collection<ValidatorMessage> validate( Collection<?> col ) throws ValidatorException {
        Collection<ValidatorMessage> messages = new ArrayList<>();
        for ( ObjectRule rule : rules ) {
            messages.addAll( validate( col, rule ) );
        }
        return messages;
    }

    /**
     * Validates a single object against all the (object) rules.
     *
     * @param objectToCheck objects to check on.
     * @return collection of validator messages.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    public Collection<ValidatorMessage> validate( Object objectToCheck ) throws ValidatorException {
        Collection<ValidatorMessage> messages = new ArrayList<>();
        for ( ObjectRule rule : rules ) {
            if ( rule.canCheck( objectToCheck ) ) { // apply only if rule can handle this object
                messages.addAll( rule.check( objectToCheck ) );
            }
        }
        return messages;
    }

    /**
     * Validates a single object against a given (object) rules.
     *
     * @param objectToCheck objects to check on.
     * @return collection of validator messages.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    public Collection<ValidatorMessage> validate( Object objectToCheck, ObjectRule rule ) throws ValidatorException {
        Collection<ValidatorMessage> messages = new ArrayList<>();
        if ( rule.canCheck( objectToCheck ) ) { // apply only if rule can handle this object
            messages.addAll( rule.check( objectToCheck ) );
        }
        return messages;
    }

    /**
     * Validates a collection of objects against a single (object) rule.
     *
     * @param col  collection of objects to check on.
     * @param rule the Rule to check on
     * @return collection of validator messages.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    private Collection<ValidatorMessage> validate( Collection<?> col, ObjectRule rule ) throws ValidatorException {
        Collection<ValidatorMessage> messages = new ArrayList<>();
        for ( Object aCol : col ) {
            if ( rule.canCheck( aCol ) ) { // apply only if rule can handle this object
                messages.addAll( rule.check( aCol ) );
            }
        }
        return messages;
    }

    //////////////////////////
    // CvMapping validation

    /**
     * Run a check on the CvMappingRules to ensure syntactically correct rules will be used for the CvMapping check.
     *
     * @return collection of validator messages.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    public Collection<ValidatorMessage> checkCvMappingRules() throws ValidatorException {
        if ( cvRuleManager != null ) {
            return cvRuleManager.checkCvMapping();
        } else {
            log.warn( "The CvRuleManager has not been set up yet." );
            return new ArrayList<>();
        }
    }

    /**
     * Run a check on the CvMapping for a given Collection of Objects.
     *
     * @param col   collection of objects to check on.
     * @param xPath the xpath from the XML root to the object that is to be checked.
     * @return collection of validator messages describing the validation results.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    public Collection<ValidatorMessage> checkCvMapping( Collection<?> col, String xPath ) throws ValidatorException {
        Collection<ValidatorMessage> messages = new ArrayList<>();
        // Run cv mapping check
        if ( cvRuleManager != null ) {
            for ( CvRule rule : cvRuleManager.getCvRules() ) {
                for ( Object o : col ) {
                    if ( rule.canCheck( xPath ) ) {
                        messages.addAll( rule.check( o, xPath ) );
                    }
                    // else: rule does not apply
                }
            }
        } else {
            log.error( "The CvRuleManager has not been set up yet." );
        }
        return messages;
    }

    /**
     * Run a check on the CvMapping for a given Object.
     *
     * @param o     Object to check.
     * @param xPath the xpath from the XML root to the object that is to be checked.
     * @return collection of validator messages describing the validation results.
     * @throws ValidatorException Exception while trying to validate the input.
     */
    public Collection<ValidatorMessage> checkCvMapping( Object o, String xPath ) throws ValidatorException {
        Collection<ValidatorMessage> messages = new ArrayList<>();
        // Run cv mapping check
        if ( cvRuleManager != null ) {
            for ( CvRule rule : cvRuleManager.getCvRules() ) {
                if ( rule.canCheck( xPath ) ) {
                    messages.addAll( rule.check( o, xPath ) );
                }
                // else: rule does not apply
            }
        } else {
            log.error( "The CvRuleManager has not been set up yet." );
        }
        return messages;
    }

    public ValidatorReport getReport() {
        return new ValidatorReport( cvRuleManager.getCvRules() );
    }

    //////////////////////////
    // resetting validation

    public void resetCvRuleStatus() {
        Collection<CvRule> cvRules = this.cvRuleManager.getCvRules();
        for (CvRule cvRule : cvRules) {
            cvRule.resetStatus();
        }
    }
}