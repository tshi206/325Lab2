package nz.ac.auckland.concert.services;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.Response.ResponseBuilder;

import nz.ac.auckland.concert.common.Config;
import nz.ac.auckland.concert.domain.Concert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to implement a simple REST Web service for managing Concerts.
 *
 */
@Path("/concerts")
public class ConcertResource {

	private static Logger _logger = LoggerFactory
			.getLogger(ConcertResource.class);

	// Declare necessary instance variables.
	private Map<Long, Concert> _concertDB = new ConcurrentHashMap<Long, Concert>();
	private AtomicLong _idCounter = new AtomicLong();
 
	/**
	 * Retrieves a Concert based on its unique id. The HTTP response message 
	 * has a status code of either 200 or 404, depending on whether the 
	 * specified Concert is found. 
	 * 
	 * When clientId is null, the HTTP request message doesn't contain a cookie 
	 * named clientId (Config.CLIENT_COOKIE), this method generates a new 
	 * cookie, whose value is a randomly generated UUID. This method returns 
	 * the new cookie as part of the HTTP response message.
	 * 
	 * This method maps to the URI pattern <base-uri>/concerts/{id}.
	 * 
	 * @param id the unique ID of the Concert.
	 * 
	 * @param clientId a cookie named Config.CLIENT_COOKIE that may be sent 
	 * by the client.
	 * 
	 * @return a Response object containing the required Concert.
	 */
	@GET
	@Path("{id}")
	@Produces("application/java-serialization")
	public Response retrieveConcert(@PathParam("id") long id, @CookieParam(Config.CLIENT_COOKIE) Cookie clientId) {
		_logger.info("Retrieving concert with id: " + id);
		// Lookup the Parolee within the in-memory data structure.
		final Concert concert = _concertDB.get(id);
		if (concert == null) {
			// Return a HTTP 404 response if the specified Parolee isn't found.
			throw new WebApplicationException(Response.Status.NOT_FOUND);
		}
		
		if (clientId == null){
			NewCookie newClientId = makeCookie(clientId);
			return Response.ok(concert).cookie(newClientId).build();
		}

		return Response.ok(concert).build();
		
	}
	

	/**
	 * Retrieves a collection of Concerts, where the "start" query parameter
     * identifies an index position, and "size" represents the maximum number
     * of successive Concerts to return. The HTTP response message returns 200.
     * 
     * When clientId is null, the HTTP request message doesn't contain a cookie 
	 * named clientId (Config.CLIENT_COOKIE), this method generates a new 
	 * cookie, whose value is a randomly generated UUID. This method returns 
	 * the new cookie as part of the HTTP response message.
	 * 
	 * This method maps to the URI pattern <base-uri>/concerts?start&size.
	 * 
	 * @param start the ID of a Concert from which to start retrieving 
	 * Concerts.
	 * 
	 * @param size the maximum number of Concerts to retrieve.
	 * 
	 * @param clientId a cookie named Config.CLIENT_COOKIE that may be sent 
	 * by the client.
	 * 
	 * @return a Response object containing a List of Concerts. The List may be
	 * empty.
	 */
	@GET
	@Produces("application/java-serialization")
	public Response retrieveConcerts(@QueryParam("start") long start, @QueryParam("size") int size, @CookieParam(Config.CLIENT_COOKIE) Cookie clientId) {
		// The Response object should store an ArrayList<Concert> entity. The 
		// ArrayList can be empty depending on the start and size arguments, 
		// and Concerts stored.
		//
		// Because of type erasure with Java Generics, any generically typed 
		// entity needs to be wrapped by a javax.ws.rs.core.GenericEntity that 
		// stores the generic type information. Hence to add an ArrayList as a
		// Response object's entity, you should use the following code:
		//
		// List<Concert> concerts = new ArrayList<Concert>();
		// GenericEntity<List<Concert>> entity = new GenericEntity<List<Concert>>(concerts) {};
		// ResponseBuilder builder = Response.ok(entity);
	
		List<Concert> concerts = new ArrayList<Concert>();
		for (long i=start; i<(start+size); i++){
			if (_concertDB.get(i) != null){
				concerts.add(_concertDB.get(i));
				_logger.info("Retrieving concert with id: " + i + " start at :" + start + " size :" + size);
			}			
		}
		
		GenericEntity<List<Concert>> entity = new GenericEntity<List<Concert>>(concerts) {};
		ResponseBuilder builder = Response.ok(entity);
		
		if (clientId == null){
			NewCookie newClientId = makeCookie(clientId);
			return builder.cookie(newClientId).build();
		}
		
		return builder.build();
	}
	
	
	/**
	 * Creates a new Concert. This method assigns an ID to the new Concert and
	 * stores it in memory. The HTTP Response message returns a Location header 
	 * with the URI of the new Concert and a status code of 201.
	 * 
	 * When clientId is null, the HTTP request message doesn't contain a cookie 
	 * named clientId (Config.CLIENT_COOKIE), this method generates a new 
	 * cookie, whose value is a randomly generated UUID. This method returns 
	 * the new cookie as part of the HTTP response message.
	 * 
	 * This method maps to the URI pattern <base-uri>/concerts.
	 * 
	 * @param concert the new Concert to create.
	 * 
	 * @param clientId a cookie named Config.CLIENT_COOKIE that may be sent 
	 * by the client.
	 * 
	 * @return a Response object containing the status code 201 and a Location
	 * header.
	 */
	@POST
	@Produces("application/java-serialization")
	@Consumes("application/java-serialization")
	public Response createConcert(Concert concert, @CookieParam(Config.CLIENT_COOKIE) Cookie clientId) {
		
		if (concert == null){
			throw new WebApplicationException(Response.Status.BAD_REQUEST);
		}

		Concert newConcert = new Concert(_idCounter.incrementAndGet(), concert.getTitle(), concert.getDate());

		_concertDB.put(newConcert.getId(), newConcert);

		_logger.info("Created new concert with id: " + newConcert.getId());

		if (clientId == null){
			NewCookie newClientId = makeCookie(clientId);
			return Response.created(URI.create("/concerts/" + newConcert.getId())).status(201).cookie(newClientId).build();
		}
		
		return Response.created(URI.create("/concerts/" + newConcert.getId())).status(201)
				.build();
	}


	/**
	 * Deletes all Concerts, returning a status code of 204.  
	 * 
	 * When clientId is null, the HTTP request message doesn't contain a cookie 
	 * named clientId (Config.CLIENT_COOKIE), this method generates a new 
	 * cookie, whose value is a randomly generated UUID. This method returns 
	 * the new cookie as part of the HTTP response message.
	 * 
	 * This method maps to the URI pattern <base-uri>/concerts.
	 * 
	 * @param clientId a cookie named Config.CLIENT_COOKIE that may be sent 
	 * by the client.
	 * 
	 * @return a Response object containing the status code 204.
	 */
	@DELETE
	public Response deleteAllConcerts(@CookieParam(Config.CLIENT_COOKIE) Cookie clientId) {
		
		_concertDB.clear();
		_idCounter = new AtomicLong();
		
		ResponseBuilder builder = Response.status(204);
		_logger.info("Clear all concerts.");
		
		if (clientId == null){
			NewCookie newClientId = makeCookie(clientId);
			return builder.cookie(newClientId).build();
		}
		
		return builder.build();
	}
	
	/**
	 * Helper method that can be called from every service method to generate a 
	 * NewCookie instance, if necessary, based on the clientId parameter.
	 * 
	 * @param userId the Cookie whose name is Config.CLIENT_COOKIE, extracted 
	 * from a HTTP request message. This can be null if there was no cookie 
	 * named Config.CLIENT_COOKIE present in the HTTP request message. 
	 * 
	 * @return a NewCookie object, with a generated UUID value, if the clientId 
	 * parameter is null. If the clientId parameter is non-null (i.e. the HTTP 
	 * request message contained a cookie named Config.CLIENT_COOKIE), this 
	 * method returns null as there's no need to return a NewCookie in the HTTP
	 * response message. 
	 */
	private NewCookie makeCookie(Cookie clientId){
		NewCookie newCookie = null;
		
		if(clientId == null) {
			newCookie = new NewCookie(Config.CLIENT_COOKIE, UUID.randomUUID().toString());
			_logger.info("Generated cookie: " + newCookie.getValue());
		} 
		
		return newCookie;
	}
}
