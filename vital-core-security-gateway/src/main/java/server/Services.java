package server;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;

import clients.OpenAMClient;
import jsonpojos.PPIResponse;
import jsonpojos.PPIResponseArray;
import jsonpojos.PermissionsCollection;
import jsonpojos.RegexStringList;

@Path("")
public class Services {
    
    private OpenAMClient client;
    
    public Services() {
        client = new OpenAMClient();
    }
    
    @Path("{endpoint: .+}")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response ppiget(
            @PathParam("endpoint") String endpoint,
            @CookieParam("vitalAccessToken") String vitalToken,
            String body) {
        return forwardAndFilter("GET", endpoint, vitalToken, body);
    }
    
    @Path("{endpoint: .+}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response ppipost(
            @PathParam("endpoint") String endpoint,
            @CookieParam("vitalAccessToken") String vitalToken,
            String body) {
        return forwardAndFilter("POST", endpoint, vitalToken, body);
    }
    
    private Response forwardAndFilter(String method, String endpoint, String vitalToken, String body) {
        Cookie ck;
        String internalToken;
        CloseableHttpClient httpclient;
        HttpRequestBase httpaction;
        PermissionsCollection perm;
        boolean wasEmpty;
        int code;

        httpclient = HttpClients.createDefault();

        URI uri = null;
        try {
            // Prepare to forward the request to the proxy
            uri = new URI("https://" + client.getProxyHost() + ":" + client.getProxyPort() + client.getProxyPPIPath() + "/" + endpoint);
        } catch (URISyntaxException e1) {
            e1.printStackTrace();
        }

        if (method.equals("GET")) {
            httpaction = new HttpGet(uri);
        }
        else {
            httpaction = new HttpPost(uri);
        }

        // Get token or authenticate if null or invalid
        internalToken = client.getToken();
        ck = new Cookie("vitalAccessToken", internalToken);

        httpaction.setHeader("Cookie", ck.toString());
        httpaction.setConfig(RequestConfig.custom().setConnectionRequestTimeout(3000).setConnectTimeout(3000).setSocketTimeout(3000).build());
        httpaction.setHeader("Content-Type", "application/json");
        StringEntity strEntity = new StringEntity(body, StandardCharsets.UTF_8);
        if (method.equals("POST")) {
            ((HttpPost) httpaction).setEntity(strEntity);
        }

        // Execute and get the response.
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpaction);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            try {
                // Try again with a higher timeout
                try {
                    Thread.sleep(1000); // do not retry immediately
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                httpaction.setConfig(RequestConfig.custom().setConnectionRequestTimeout(7000).setConnectTimeout(7000).setSocketTimeout(7000).build());
                response = httpclient.execute(httpaction);
            } catch (ClientProtocolException ea) {
                ea.printStackTrace();
            } catch (IOException ea) {
                try {
                    // Try again with a higher timeout
                    try {
                        Thread.sleep(1000); // do not retry immediately
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    httpaction.setConfig(RequestConfig.custom().setConnectionRequestTimeout(12000).setConnectTimeout(12000).setSocketTimeout(12000).build());
                    response = httpclient.execute(httpaction);
                } catch (ClientProtocolException eaa) {
                    ea.printStackTrace();
                } catch (IOException eaa) {
                    ea.printStackTrace();
                    return Response.ok()
                        .entity(eaa.getMessage())
                        .build();
                }
            }
        }

        HttpEntity entity = response.getEntity();
        String respString = "";

        if (entity != null) {
            try {
                respString = EntityUtils.toString(entity);
                response.close();
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } 
        }
        
        // Get user permissions from the security module
        perm = client.getPermissions(vitalToken);
        if (perm.getAdditionalProperties().containsKey("code")) {
            if (perm.getAdditionalProperties().get("code").getClass() == Integer.class) {
                code = (Integer) perm.getAdditionalProperties().get("code");
                if (code >= 400 || code < 500) {
                    return Response.status(Status.BAD_REQUEST)
                        .entity(perm)
                        .build();
                } else if (code >= 500 || code < 600) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(perm)
                        .build();
                }
            }
        }
        
        // Convert string to object and filter by specific fields values (you may get an array or not)
        if (respString.charAt(0) == '[') {
            respString = "{ \"documents\": " + respString + " }";
            PPIResponseArray array = null;
            try {
                array = (PPIResponseArray) utils.JsonUtils.deserializeJson(respString, PPIResponseArray.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            List<PPIResponse> docsExpanded = new ArrayList<PPIResponse>();
            if (array != null) {
                for (int ai = 0; ai < array.getDocuments().size(); ai++) {
                    try {
                        JsonLdOptions options = new JsonLdOptions();
                        Object jsonObject = JsonUtils.fromString(utils.JsonUtils.serializeJson(array.getDocuments().get(ai)));
                        Object result = JsonLdProcessor.expand(jsonObject, options);
                        docsExpanded.add((PPIResponse) utils.JsonUtils.deserializeJson(JsonUtils.toPrettyString(result), PPIResponse.class));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                wasEmpty = docsExpanded.isEmpty();
                // Check dynamically on specified attributes
                Iterator<Map.Entry<String, RegexStringList>> it = perm.getRetrieve().getDenied().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, RegexStringList> pair = it.next();
                    docsExpanded.removeIf(p -> 
                        !getSubObject(p, pair.getKey()).isEmpty() && ((RegexStringList) pair.getValue()).contains(getSubObject(p, pair.getKey())));
                }
                it = perm.getRetrieve().getAllowed().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, RegexStringList> pair = it.next();
                    docsExpanded.removeIf(p -> 
                        !getSubObject(p, pair.getKey()).isEmpty() && !((RegexStringList) pair.getValue()).contains(getSubObject(p, pair.getKey())));
                }
                try {
                    List<PPIResponse> tmpDocs = new ArrayList<PPIResponse>();
                    tmpDocs.addAll(array.getDocuments());
                    for (int ai = 0; ai < array.getDocuments().size(); ai++) {
                        for (int aj = 0; aj < docsExpanded.size(); aj++) {
                            if (array.getDocuments().get(ai).getProperties().get("id").equals(docsExpanded.get(aj).getProperties().get("@id")))
                                tmpDocs.remove(ai);
                        }
                    }
                    array.setDocuments(tmpDocs);
                    if (!wasEmpty && array.getDocuments().isEmpty()) {
                        return Response.status(Status.FORBIDDEN)
                            .entity("{ \"code\": 403, \"reason\": \"Forbidden\", \"message\": \"Not enough permissions to access the requested data!\"}")
                            .build();
                    } else {
                        respString = utils.JsonUtils.serializeJson(array.getDocuments());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (respString.charAt(0) == '{') {
            PPIResponse resp = null;
            try {
                resp = (PPIResponse) utils.JsonUtils.deserializeJson(respString, PPIResponse.class);
                if (resp.getProperties().containsKey("code")) {
                    if (resp.getProperties().get("code").getClass() == Integer.class) {
                        code = (Integer) resp.getProperties().get("code");
                        if (code >= 400 || code < 500) {
                            return Response.status(Status.BAD_REQUEST)
                                .entity(resp)
                                .build();
                        } else if (code >= 500 || code < 600) {
                            return Response.status(Status.INTERNAL_SERVER_ERROR)
                                .entity(resp)
                                .build();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (resp != null) {
                System.out.println(respString);
                JsonLdOptions options = new JsonLdOptions();
                try {
                    Object jsonObject = JsonUtils.fromString(respString);
                    Object result = JsonLdProcessor.expand(jsonObject, options);
                    System.out.println(JsonUtils.toPrettyString(result));
                    resp = (PPIResponse) utils.JsonUtils.deserializeJson(JsonUtils.toPrettyString(result), PPIResponse.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Iterator<Map.Entry<String, RegexStringList>> it = perm.getRetrieve().getDenied().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, RegexStringList> pair = it.next();
                    List<Object> objects = getSubObject(resp, pair.getKey());
                    //System.out.println("Denied " + pair.getKey() + " " + resp.getProperties().containsKey(pair.getKey()) + " " + ((RegexStringList) pair.getValue()).contains(resp.getProperties().get(pair.getKey())));
                    if(!objects.isEmpty() && ((RegexStringList) pair.getValue()).contains(objects))
                        return Response.status(Status.FORBIDDEN)
                            .entity("{ \"code\": 403, \"reason\": \"Forbidden\", \"message\": \"Not enough permissions to access the requested data!\"}")
                            .build();
                }
                it = perm.getRetrieve().getAllowed().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, RegexStringList> pair = it.next();
                    List<Object> objects = getSubObject(resp, pair.getKey());
                    //System.out.println("Allowed " + pair.getKey() + " " + resp.getProperties().containsKey(pair.getKey()) + " " + ((RegexStringList) pair.getValue()).contains(resp.getProperties().get(pair.getKey())));
                    if(!objects.isEmpty() && !((RegexStringList) pair.getValue()).contains(objects))
                        return Response.status(Status.FORBIDDEN)
                            .entity("{ \"code\": 403, \"reason\": \"Forbidden\", \"message\": \"Not enough permissions to access the requested data!\"}")
                            .build();
                }
            }
        }

        return Response.ok()
            .entity(respString)
            .build();
    }

    private List<Object> getSubObject(PPIResponse resp, String key) {
        PPIResponse inner = new PPIResponse(resp);
        List<Object> values = new ArrayList<Object>();
        
        String[] chain = key.split("\\.");
        
        for (int i = 0; i < chain.length - 1; i++) {
            if (!inner.getProperties().containsKey(chain[i])) {
                inner = null;
                break;
            }
            PPIResponse hey = new PPIResponse(inner.getProperties().get(chain[i]));
            if (hey.getProperties().isEmpty()) {
                List<LinkedHashMap<String, Object>> array;
                array = new ArrayList<LinkedHashMap<String, Object>>();
                array.addAll((ArrayList<LinkedHashMap<String, Object>>) inner.getProperties().get(chain[i]));
                for (int j = 0; j < array.size(); j++) {
                    String subkey = "";
                    for (int k = i + 1; k < chain.length; k++)
                        subkey = subkey + chain[k];
                    List<Object> objs = getSubObject(new PPIResponse(array.get(j)), subkey);
                    if (objs != null)
                        values.addAll(objs);
                }
                return values;
            } else {
                inner = hey;
            }
        }
        
        if (inner != null)
            values.add(inner.getProperties().get(chain[chain.length - 1]));
        
        return values;
    }
}
