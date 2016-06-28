/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.vital.vitalcep.restApp.alert;

import static com.mongodb.client.model.Filters.eq;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.util.JSON;

import eu.vital.vitalcep.cep.CEP;
import eu.vital.vitalcep.cep.CepContainer;
import eu.vital.vitalcep.cep.CepProcess;
import eu.vital.vitalcep.conf.ConfigReader;
import eu.vital.vitalcep.conf.PropertyLoader;
import eu.vital.vitalcep.connectors.mqtt.MqttConnectorContainer;
import eu.vital.vitalcep.connectors.ppi.PPIManager;
import eu.vital.vitalcep.entities.dolceHandler.DolceSpecification;
import eu.vital.vitalcep.publisher.MQTT_connector_subscriper;
import eu.vital.vitalcep.publisher.MessageProcessor_publisher;
import eu.vital.vitalcep.security.Security;
import javax.ws.rs.OPTIONS;



// TODO: Auto-generated Javadoc
/**
 * The Class FilterFacadeREST.
 */
@Path("alerts")
public class Alerts {

    /** The Constant logger. */
    final static Logger logger = Logger.getLogger(Alerts.class);
      
    private PropertyLoader props;
    
    private final String host;
    private final String mongoURL;
    private final String mongoDB;
    private String cookie;
    private final String dmsURL;
    private final String confFile;


    
   // @Context private javax.servlet.http.HttpServletRequest hsr;
    
    public Alerts() throws IOException {

        ConfigReader configReader = ConfigReader.getInstance();
        
        mongoURL = configReader.get(ConfigReader.MONGO_URL);
        mongoDB = configReader.get(ConfigReader.MONGO_DB);
        host = configReader.get(ConfigReader.CEP_BASE_URL);
        confFile = configReader.get(ConfigReader.CEP_CONF_FILE);
        dmsURL = configReader.get(ConfigReader.DMS_URL);

    }
    
    
    /**
     * Gets the filters.
     *
     * @return the filters
     */
    @GET
    @Path("getalerts")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAlerts() {
        
        MongoClient mongo = new MongoClient(new MongoClientURI (mongoURL));

        MongoDatabase db = mongo.getDatabase(mongoDB);
        
        BasicDBObject query = new BasicDBObject(); 
        BasicDBObject fields = new BasicDBObject().append("_id",false);
        fields.append("dolceSpecification", false);
        
        FindIterable<Document> coll = db.getCollection("alerts")
                .find(query).projection(fields);
       
        final JSONArray AllJson = new JSONArray(); 
        
        coll.forEach(new Block<Document>() {
            @Override
            public void apply(final Document document) {
                AllJson.put(document);
            }
        });
        if (db!= null)
        	db = null;
        if (mongo != null){
        	mongo.close();
        	mongo = null;
        }
        return AllJson.toString();

    }
    

    public static int getPid(Process process) {
        try {
            Class<?> cProcessImpl = process.getClass();
            java.lang.reflect.Field fPid = cProcessImpl.getDeclaredField("pid");
            if (!fPid.isAccessible()) {
                fPid.setAccessible(true);
            }
            return fPid.getInt(process);
        } catch (Exception e) {
            return -1;
        }
    }
    /**
     * Creates a filter.
     *
     * @param cepico
     * @param req
     * @return the filter id 
     * @throws java.io.IOException 
     */
    @PUT
    @Path("createalert")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAlert(String cepico
        ,@Context HttpServletRequest req) throws IOException {
        
        StringBuilder ck = new StringBuilder();
        Security slogin = new Security();
                  
        JSONObject credentials = new JSONObject();
                  
        Boolean token = slogin.login(req.getHeader("name")
                ,req.getHeader("password"),false,ck);
            credentials.put("username", req.getHeader("name"));
            credentials.put("password", req.getHeader("password"));
        if (!token){
              return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        this.cookie = ck.toString(); 
                
        
        JSONObject jo = new JSONObject(cepico);
        if(!jo.has("source") ){
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        
        MongoClient mongo = new MongoClient(new MongoClientURI (mongoURL));
        MongoDatabase db = mongo.getDatabase(mongoDB);

        try {
           db.getCollection("alerts");
        } catch (Exception e) {
          //System.out.println("Mongo is down");
          mongo.close();
          return Response.status(Response
                            .Status.INTERNAL_SERVER_ERROR).build();

        }finally{
        	if (db != null)
        		db = null;
        	if (mongo != null){
        		mongo.close();
        		mongo= null;
        	}
        }
        
        // create an empty query
        BasicDBObject query = new BasicDBObject(); 
        BasicDBObject fields = new BasicDBObject().append("_id",false);
        fields.append("dolceSpecification", false);
               
        if ( jo.has("dolceSpecification")) {
            
            //Filter oFilter = new Filter(filter);
            JSONObject dsjo = jo.getJSONObject("dolceSpecification");
            String str = dsjo.toString();//"{\"dolceSpecification\": "+ dsjo.toString()+"}";
            
            try{
                
                DolceSpecification ds = new DolceSpecification(str);
                
                if(ds instanceof DolceSpecification) {
                    UUID uuid = UUID.randomUUID();
                    String randomUUIDString = uuid.toString();
                  
                    
                   

                    String mqin = RandomStringUtils.randomAlphanumeric(8);
                    String mqout = RandomStringUtils.randomAlphanumeric(8);
                    
                    Date NOW = new Date();
                   
                    JSONArray requestArray;
                    
                    try {
                        requestArray = createAlertRequests(
                        jo.getJSONArray("source"),
                        ds.getEvents(), getXSDDateTime(NOW));
                    }catch(Exception e){
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity("not available getObservation Service for this sensor ")
                                .build();
                    }
                    
                    CEP cepProcess = new CEP();
                   
                    if (!(cepProcess.CEPStart(CEP.CEPType.ALERT, ds, mqin,
                            mqout, confFile, requestArray.toString(), credentials))){
                        return Response.status(Response
                                .Status.INTERNAL_SERVER_ERROR).build();
                    }
                    
                    String clientName = cepProcess.fileName;

                    if (cepProcess.PID<1){
                        return Response.status(Response
                                .Status.INTERNAL_SERVER_ERROR).build();
                    }
                    
                              
                    DBObject dbObject = 
                            createAlertSensor(cepico, randomUUIDString, dsjo,
                            cepProcess.id);
                    
                    
                    
                 
                     Document doc = new Document(dbObject.toMap());

                    try{
                        db.getCollection("alerts").insertOne(doc);
                        
                        JSONObject opState = createOperationalStateObservation
                            (randomUUIDString);
                        String sensorId = host+"/sensor/"+randomUUIDString;
                        
                         MessageProcessor_publisher Publisher_MsgProcc 
                            = new MessageProcessor_publisher(this.dmsURL
                            ,cookie,sensorId,"alertsobservations",mongoURL,mongoDB);//555
                        MQTT_connector_subscriper publisher 
                                = new MQTT_connector_subscriper (mqout,Publisher_MsgProcc);
                        MqttConnectorContainer.addConnector(publisher.getClientName(), publisher);

                        DBObject oPut =  (DBObject)JSON.parse(opState.toString());
                        Document doc1 = new Document(oPut.toMap());

                        try{
                            db.getCollection("alertsobservations").insertOne(doc1);
                            String id = doc1.get("_id").toString();

                        }catch(MongoException ex
                                ){
                            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                    .build();
                        }

                   
                        JSONObject aOutput = new JSONObject();
                        aOutput.put("id", host+"/sensor/"
                            +randomUUIDString);
                        return Response.status(Response.Status.OK)
                            .entity(aOutput.toString()).build();
                       
                    }catch(MongoException ex
                            ){
                        return Response.status(Response.Status.BAD_REQUEST)
                                .build();
                    }
      
                }else{
                    
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
            }catch(JSONException | IOException e){
                 return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }   
        
        return Response.status(Response.Status.BAD_REQUEST).build();
          
    }

    private String getXSDDateTime(Date date) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        return  dateFormat.format(date);
    }

    private JSONObject createOperationalStateObservation
        ( String randomUUIDString) throws JSONException {
        JSONObject opState = new JSONObject();
        opState.put("@context",
                "http://vital-iot.eu/contexts/measurement.jsonld");
        opState.put("id", host+"/sensor/" + randomUUIDString + "/observation/1");
        opState.put("type","ssn:Observation");
        opState.put("ssn:featureOfInterest", host+"/sensor/" + randomUUIDString);
        JSONObject property = new JSONObject();
        property.put("type","vital:OperationalState");
        opState.put("ssn:observationProperty",property);
        JSONObject resultTime = new JSONObject();
        Date date = new Date();
        resultTime.put("time:inXSDDateTime",getXSDDateTime(date));//check format
        opState.put("ssn:observationResultTime",resultTime);
        //"time:inXSDDateTime": "2015-10-14T11:59:11+02:00"
        JSONObject hasValue = new JSONObject();
        hasValue.put( "type","ssn:ObservationValue");
        hasValue.put( "value","vital:Running");
        JSONObject observationResult = new JSONObject();
        observationResult.put("ssn:hasValue",hasValue);
        observationResult.put("type","ssn:SensorOutput");
        opState.put("ssn:observationResult",observationResult);
        return opState;
    }
    

    private DBObject createAlertSensor(String cepico, String randomUUIDString,
            JSONObject dsjo, String cepInstance) throws JSONException {
        DBObject dbObject = (DBObject) JSON.parse(cepico);
        dbObject.put("@context",
                "http://vital-iot.eu/contexts/sensor.jsonld");
        dbObject.put("id", host+"/sensor/"
                +randomUUIDString);
        dbObject.put("type", "vital:AlertSensor");
        dbObject.put("status", "vital:Running");
        JSONArray observes =  new JSONArray();
        JSONArray compl = dsjo.getJSONArray("complex");
        for (int i = 0; i < compl.length(); i++) {
            JSONObject oComplex = new JSONObject(
                    compl.get(i).toString());
            JSONObject oObserves = new JSONObject();
            
            oObserves.put("type", "vital:ComplexEvent");
            //oObserves.put("uri",  host.toString()
            //        +"/sensor/"+randomUUIDString
            //        +"/"+oComplex.getString("id").toString());
            oObserves.put("id",  host+"/sensor/"+randomUUIDString
                    +"/"+oComplex.getString("id").toString());
            
            observes.put(oObserves);
            
        }
        DBObject dbObject2 = (DBObject)JSON.parse(observes
                            .toString());
        dbObject.put("ssn:observes",dbObject2);
        dbObject.put("cepinstance",cepInstance);
        return dbObject;
    }
    
    private void createAlertObject(DBObject dbObject, String randomUUIDString, 
            JSONObject dsjo) throws JSONException {
        dbObject.put("@context",
                "http://vital-iot.eu/contexts/sensor.jsonld");
        dbObject.put("id", host+"/sensor/" + randomUUIDString);
        dbObject.put("uri", host+"/sensor/" + randomUUIDString);
        dbObject.removeField("type");
        dbObject.put("type","vital:AlertSensor");
        dbObject.put("status", "vital:Running");
        JSONArray observes =  new JSONArray();
        JSONArray compl = dsjo.getJSONArray("complex");
        for (int i = 0; i < compl.length(); i++) {
            JSONObject oComplex = new JSONObject(
                    compl.get(i).toString());
            JSONObject oObserves = new JSONObject();
            oObserves.put("type", "vital:ComplexEvent");
            oObserves.put("uri", host+"/sensor/" + randomUUIDString + "/" 
                    + oComplex.getString("id").toString());
            oObserves.put("id", host+"/sensor/" + randomUUIDString + "/" 
                    + oComplex.getString("id").toString());
            observes.put(oObserves);
        }
        DBObject dbObject2 = (DBObject)JSON.parse(observes.toString());
        dbObject.put("ssn:observes",dbObject2);
    }
    
    /**
     * Gets a filter.
     *
     * @return the filter 
     */
    @POST
    @Path("getAlert")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAlert(String id) {
        
        JSONObject jo = new JSONObject(id);
        String idjo = jo.getString("id");
          
       MongoClient mongo = new MongoClient(new MongoClientURI (mongoURL));
            MongoDatabase db = mongo.getDatabase(mongoDB);
            
            try {
               db.getCollection("alerts");
            } catch (Exception e) {
              //System.out.println("Mongo is down");
              mongo.close();
              return Response.status(Response
                                .Status.INTERNAL_SERVER_ERROR)
            .build();
                    
            }
        
        BasicDBObject searchById = new BasicDBObject("id",idjo);
        String found = null;
        BasicDBObject fields = new BasicDBObject().append("_id",false);

        FindIterable<Document> coll = db.getCollection("alerts")
                .find(searchById).projection(fields);
        
        try {
            found = coll.first().toJson();
        }catch(Exception e){
            return Response.status(Response.Status.NOT_FOUND)
            .build();
        }finally{
        	db = null;
        	if (mongo != null){
        		mongo.close();
        		mongo = null;
        	}
        }

        if (found == null){
            return Response.status(Response.Status.NOT_FOUND)
            .build();
        }else{
            return Response.status(Response.Status.OK)
                    .entity(found.toString())
            .build();
        }
        
    }
    
    /**
     * Gets a filter.
     *
     * @param filterId
     * @param req
     * @return the filter 
     */
    @DELETE
    @Path("deletealert")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAlert(String filterId
            ,@Context HttpServletRequest req) throws IOException {
    	MongoClient mongo = null;
        MongoDatabase db = null;
    	
        try{
	         StringBuilder ck = new StringBuilder();
	        Security slogin = new Security();
	                  
	        Boolean token = slogin.login(req.getHeader("name")
	                ,req.getHeader("password"),false,ck);
	        if (!token){
	              return Response.status(Response.Status.UNAUTHORIZED)
            .build();
	        }
	        this.cookie = ck.toString(); 
	        
	        JSONObject jo = new JSONObject(filterId);
	        String idjo = jo.getString("id");
	             
	        mongo = new MongoClient(new MongoClientURI (mongoURL));
	        db = mongo.getDatabase(mongoDB);
	
	        try {
	           db.getCollection("alerts");
	        } catch (Exception e) {
	          //System.out.println("Mongo is down");
	          mongo.close();
	          return Response.status(Response
	                            .Status.INTERNAL_SERVER_ERROR)
            .build();
	        }
	        
	        
	        MongoCollection<Document> coll = db.getCollection("alerts");
	        
	        Bson filter = Filters.eq("id",idjo );
	        
	        FindIterable<Document> iterable = coll.find(filter);
	        
	        String cepInstance;
	        
	        CEP cepProcess = new CEP();
	        
	        if (iterable!= null && iterable.first()!=null){
	            Document doc = iterable.first();
	            cepInstance = doc.getString("cepinstance");
	                
	            MongoCollection<Document> collInstances = db.getCollection("cepinstances");
	
	            ObjectId ci = new ObjectId(cepInstance);
	            Bson filterInstances = Filters.eq("_id",ci);
	
	            FindIterable<Document> iterable2 = collInstances.find(filterInstances);
	        
	            if (iterable2!= null){
	                Document doc2 = iterable2.first();
	                cepProcess.PID = doc2.getInteger("PID");
	                cepProcess.fileName = doc2.getString("fileName");
	                cepProcess.cepFolder = doc2.getString("cepFolder");
	                 cepProcess.type = CEP.CEPType.ALERT.toString();
	                CepProcess cp = new CepProcess(null, null,null, null);
	                cp.PID=doc2.getInteger("PID");
	                
	                cepProcess.cp =cp;
	                
	                if (!cepProcess.cepDispose()){
	                    java.util.logging.Logger.getLogger
	                    (Alerts.class.getName()).log(Level.SEVERE, 
	                    "bcep Instance not terminated" );
	                }else{
	    
	                    Bson filter1 = Filters.eq("_id",ci);
	                    Bson update =  new Document("$set"
	                            ,new Document("status","terminated"));
	                    UpdateOptions options = new UpdateOptions().upsert(false);
	                    UpdateResult updateDoc =  db.getCollection("cepinstances")
	                            .updateOne(filter1,update,options);
	  
	                };
	                CepContainer.deleteCepProcess(cp.PID);
	                
	            }
	        }else{
	            return Response.status(Response.Status.NOT_FOUND)
            .build();
	        }
	       
	        
	        DeleteResult deleteResult = coll.deleteOne(eq("id",idjo));     
	        
	        if (deleteResult.getDeletedCount() < 1){
	            return Response.status(Response.Status.NOT_FOUND)
            .build();
	        }else{
	            return Response.status(Response.Status.OK)
                        .build();
	        }
        }catch (Exception e){
        	return Response.status(Response
                    .Status.INTERNAL_SERVER_ERROR)
            .build();
        }finally{
        	db = null;
        	if (mongo != null){
        		mongo.close();
        		mongo= null;
        	}
        }
    }
	
    
    
    
    
    private JSONArray createAlertRequests (JSONArray sources,
            JSONArray propeties,String from)
            
        throws FileNotFoundException, IOException,
            UnsupportedEncodingException, KeyManagementException, 
            NoSuchAlgorithmException, KeyStoreException, Exception{
        
        Logger logger = Logger.getLogger(this.getClass().getName());
        
        String body 
        = "{\"type\": [\"http://vital-iot.eu/ontology/ns/ObservationService\"]}";


        JSONArray ppis = new JSONArray();

        for (int i = 0; i < sources.length(); i++) {
            int sensorIndex = sources.getString(i).indexOf("/sensor");
            String ppiString = sources.getString(i).substring(0,sensorIndex);
            ppis.put(ppiString);
        }

        JSONArray distictPPIs = new JSONArray();
        for(int i=0;i<ppis.length();i++){
            boolean isDistinct = false;
            for(int j=0;j<i;j++){
                if(ppis.getString(i).equals(ppis.getString(j))){
                    isDistinct = true;
                    break;
                }
            }
            if(!isDistinct){
                distictPPIs.put(ppis.getString(i));
            }
        }
        

        JSONArray requests = new JSONArray();
        for (int i = 0; i<distictPPIs.length(); i++) {
            
            String auxbaseURL = distictPPIs.getString(i);
            
            PPIManager oPPI = new PPIManager(cookie);
                
                
            JSONArray services ;

            services = oPPI.getServices(auxbaseURL,body);
            
            Boolean hasObserationService = false;
            
            String getObservationAddress = null;

            if (services.length()>0){

                JSONArray operations;
                JSONObject service0;

                service0 = services.getJSONObject(0);

                operations = service0.getJSONArray("operations");
                for (int x = 0; x < operations.length(); x++) {

                    if (operations.getJSONObject(x).getString("type")
                            .equals("vital:GetObservations")){
                        hasObserationService = true;
                        getObservationAddress = operations.getJSONObject(x)
                                .getString("hrest:hasAddress");
                        break;
                    }  

                }
              
            }
            
            if (!hasObserationService){
                 throw new Exception();
            }
            
            for (int j = 0; j < propeties.length(); j++) {
                
                JSONObject request = new JSONObject();
                                         
                request.put("ppiURL",auxbaseURL);           
               
                JSONArray sensor = new JSONArray();

                for (int k = 0; k<sources.length(); k++) {
                    if (sources.getString(k).contains(auxbaseURL)){
                        sensor.put(sources.getString(k));
                    }                    
                }

                JSONObject requestBody = new JSONObject();
                requestBody.put("sensor",sensor);
                requestBody.put("property",propeties.getString(j));
                requestBody.put("from",from);
                request.put("body",requestBody);
                requests.put(request);
            }
        }
        
        return requests;

    }
    
    @OPTIONS
    @Path("getalert")
    public Response getalertOptions() {
    return Response.ok("").build();
    }
    
    @OPTIONS
    @Path("createalert")
    public Response createalertOptions() {
    return Response.ok("").build();
    }
    
    @OPTIONS
    @Path("deletealert")
    public Response deletealertOptions() {
    return Response.ok("").build();
    }
    
}
