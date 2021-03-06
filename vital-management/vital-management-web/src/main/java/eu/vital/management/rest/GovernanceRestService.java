package eu.vital.management.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.vital.management.service.GovernanceService;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/governance")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class GovernanceRestService {

	@Inject
	private Logger log;

	@Inject
	private GovernanceService governanceService;

	@Inject
	private ObjectMapper objectMapper;

	@GET
	@Path("/boundaries")
	public Response getBoundaries(@Context HttpHeaders hh) throws Exception {
		JsonNode boundaries = governanceService.getBoundaries(hh);
		return Response.ok(boundaries).build();
	}

	@POST
	@Path("/boundaries")
	public Response saveBoundaries(@Context HttpHeaders hh,JsonNode boundaries) throws Exception {
		JsonNode saved = governanceService.saveBoundaries(hh,boundaries);
		return Response.ok(saved).build();
	}

	@GET
	@Path("/access/{groupName}")
	public Response getAccess(@Context HttpHeaders hh,@PathParam("groupName") String groupName) throws Exception {
		JsonNode boundaries = governanceService.getAccess(hh,groupName);
		return Response.ok(boundaries).build();
	}

	@PUT
	@Path("/access/{groupName}")
	public Response saveAccess(@Context HttpHeaders hh,@PathParam("groupName") String groupName, JsonNode access) throws Exception {
		JsonNode saved = governanceService.saveAccess(hh,groupName, access);
		return Response.ok(saved).build();
	}

	@GET
	@Path("/sla/{groupName}/{slaType}")
	public Response getSLAObservations(@PathParam("groupName") String groupName, @PathParam("slaType") String slaType) throws Exception {
		JsonNode result = governanceService.getSLAObservations(groupName, slaType);
		return Response.ok(result).build();
	}
	

}