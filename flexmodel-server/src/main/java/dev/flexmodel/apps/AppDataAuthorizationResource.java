package dev.flexmodel.apps;

import dev.flexmodel.directory.DirectoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/apps/{appId}")
@Produces(MediaType.APPLICATION_JSON)
public class AppDataAuthorizationResource {
  @Inject
  AppDataAuthorizationService appDataAuthorizationService;
  @Inject
  DirectoryService directoryService;
  @Inject
  AppService appService;

  @GET
  @Path("/directory")
  public Object directory(@PathParam("appId") String app) {
    appService.findApp(app);
    return directoryService.directory();
  }

  @GET
  @Path("/forms/{form}/authorizations")
  public Object list(@PathParam("appId") String app, @PathParam("form") String form) {
    return appDataAuthorizationService.list(app, form);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/forms/{form}/authorizations")
  public Object create(@PathParam("appId") String app, @PathParam("form") String form, Map<String, Object> request) {
    return appDataAuthorizationService.save(app, form, null, request);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/forms/{form}/authorizations/{id}")
  public Object update(@PathParam("appId") String app, @PathParam("form") String form, @PathParam("id") String id, Map<String, Object> request) {
    return appDataAuthorizationService.save(app, form, id, request);
  }

  @DELETE
  @Path("/forms/{form}/authorizations/{id}")
  public void delete(@PathParam("appId") String app, @PathParam("form") String form, @PathParam("id") String id) {
    appDataAuthorizationService.delete(app, form, id);
  }
}
