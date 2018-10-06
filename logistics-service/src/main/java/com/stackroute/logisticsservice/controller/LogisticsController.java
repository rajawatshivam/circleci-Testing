package com.stackroute.logisticsservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.stackroute.logisticsservice.domain.DateLogistics;
import com.stackroute.logisticsservice.domain.Location;
import com.stackroute.logisticsservice.domain.Order;
import com.stackroute.logisticsservice.domain.Route;
import com.stackroute.logisticsservice.domain.Slot;
import com.stackroute.logisticsservice.domain.Vehicle;
import com.stackroute.logisticsservice.exception.DateNotAvailableException;
import com.stackroute.logisticsservice.exception.MongoConnectionException;
import com.stackroute.logisticsservice.exception.SlotsNotAvailableException;
import com.stackroute.logisticsservice.service.LogisticsService;

/**
 * Controller for mapping to generate a logistics service
 */
@CrossOrigin
@RestController
@RequestMapping(value = "/api/v1")
public class LogisticsController {

	private LogisticsService logisticsServiceImpl;

	@Autowired
	public LogisticsController(LogisticsService logisticsServiceImpl) {
		this.logisticsServiceImpl = logisticsServiceImpl;
	}

	/*
	 * Controller method to get available slots and cost of slots.
	 */

	@RequestMapping(value = "/slots", method = RequestMethod.GET, produces = "application/json")
	public ResponseEntity<?> getAvailableSlots(@RequestParam("orderId") String orderId,
			@RequestParam("orderConsumerName") String orderConsumerName,
			@RequestParam("orderConsumerAddress") String orderConsumerAddress,
			@RequestParam("orderConsumerPhone") String orderConsumerPhone,
			@RequestParam("orderLatitude") String orderLatitude,
			@RequestParam("orderLongitude") String orderLongitude,
			@RequestParam("orderVolume") String orderVolume,
			@RequestParam("orderDate") String orderDate) {
		
		try {
			Location newLocation = new Location(orderLatitude,orderLongitude);
			Order newOrder = new Order(orderId, orderConsumerName, orderConsumerAddress,orderConsumerPhone, newLocation,orderVolume, orderDate,false,false,null,null);
			DateLogistics selectedDate = logisticsServiceImpl.getDateDetails(newOrder);
			Route orderedRoute;
			if (selectedDate == null) {
				throw new DateNotAvailableException("Error: Date not available");
			}
			RestTemplate restTemplate = new RestTemplate();
			try {
				Route newOrderRoute = new Route(selectedDate, newOrder);
				DateLogistics dl = newOrderRoute.getDateLogistics();
				System.out.println(dl.getDate());			
				Slot sl[]=dl.getSlots();
				
				Vehicle v[] =sl[0].getSlotVehicle();
				System.out.println(v[0].getVehicleCapacity());
				System.out.println("abc");
				final String uri = "http://10.20.1.206:9078/api/v1/cvrp/slots";
				orderedRoute = restTemplate.postForObject(uri, newOrderRoute, Route.class);
			}
			catch(Exception exception) {
				return new ResponseEntity<String>("Error: Service unavailable",HttpStatus.SERVICE_UNAVAILABLE);
			}
			try {
				if (orderedRoute == null) {
					throw new SlotsNotAvailableException("Error: Slots not available");
				}
			} catch (SlotsNotAvailableException slotException) {
				return new ResponseEntity<String>(slotException.toString(), HttpStatus.GATEWAY_TIMEOUT);
			}
			return new ResponseEntity<Route>(orderedRoute, HttpStatus.CREATED);
			
		} catch (DateNotAvailableException dateException) {
			return new ResponseEntity<String>(dateException.toString(), HttpStatus.NOT_FOUND);
		} catch (MongoConnectionException connectionException) {
			return new ResponseEntity<String>("Error: Connection Issue", HttpStatus.GATEWAY_TIMEOUT);
		}
	}

	/*
	 * Controller method to save route of selected slot of particular order.
	 */
	@RequestMapping(value = "/slot", method = RequestMethod.POST, produces = "application/json")
	public ResponseEntity<?> saveRouteOrder(@RequestBody Route selectedSlot) {
		try {
			boolean slotConfirmed;
			try {
				slotConfirmed = logisticsServiceImpl.saveOrderDetails(selectedSlot);
			} catch (MongoConnectionException connectionException) {
				return new ResponseEntity<String>("Error: Connection Issue", HttpStatus.GATEWAY_TIMEOUT);
			}
			if (slotConfirmed) {
				return new ResponseEntity<String>("Slot Confirmed", HttpStatus.ACCEPTED);
			} else {
				throw new SlotsNotAvailableException("Error: Slots not available");
			}
		} catch (SlotsNotAvailableException slotException) {
			return new ResponseEntity<String>(slotException.toString(), HttpStatus.NOT_FOUND);
		}
	}
	
	/*
	 * Controller method to save route of selected slot of particular order.
	 */
	@RequestMapping(value = "/date", method = RequestMethod.GET, produces = "application/json")
	public ResponseEntity<?> getDateLogistics(@RequestParam("date") String date) {
		DateLogistics dateLogistics;
		try {
			dateLogistics = logisticsServiceImpl.getDateLogistics(date);
		} catch (MongoConnectionException connectionException) {
			return new ResponseEntity<String>("Error: Connection Issue",HttpStatus.GATEWAY_TIMEOUT);
		}
		return new ResponseEntity<DateLogistics>(dateLogistics,HttpStatus.ACCEPTED);
		
	}
	
	/*
	 * Controller method to delete order of selected slot of particular order.
	 */
	@RequestMapping(value = "/slot", method = RequestMethod.DELETE, produces = "application/json")
	public ResponseEntity<?> removeCancelledOrder(@RequestBody Order order) {
		@SuppressWarnings("unused")
		boolean isDeleted;
		try {
			isDeleted = logisticsServiceImpl.removeOrderDetails(order);
		} catch (MongoConnectionException connectionException) {
			return new ResponseEntity<String>("Error: Connection Issue", HttpStatus.GATEWAY_TIMEOUT);
		}
		return new ResponseEntity<String>("Succesfully Deleted",HttpStatus.OK);
	}
	
	
	/*
	 * Controller method to get locations all orders with specific date, vehicle and slot.
	 */
	@RequestMapping(value = "/locations", method = RequestMethod.GET, produces = "application/json")
	public ResponseEntity<?> getOrderLocations(@RequestParam("date") String date, @RequestParam("slotId") String slotId, @RequestParam("vehicleId") String vehicleId) {
		Location orderLocations[];
		try {
			orderLocations = logisticsServiceImpl.getOrderLocation(date, slotId, vehicleId);
		}
		catch(MongoConnectionException connectionException) {
			return new ResponseEntity<String>("Error: Connection Issue", HttpStatus.GATEWAY_TIMEOUT);
		}
		return new ResponseEntity<Location[]>(orderLocations,HttpStatus.ACCEPTED);
	}
	
	
	
}
