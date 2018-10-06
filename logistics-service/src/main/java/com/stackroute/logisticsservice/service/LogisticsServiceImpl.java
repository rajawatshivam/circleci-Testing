package com.stackroute.logisticsservice.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import com.stackroute.logisticsservice.domain.DateLogistics;
import com.stackroute.logisticsservice.domain.Location;
import com.stackroute.logisticsservice.domain.Order;
import com.stackroute.logisticsservice.domain.Route;
import com.stackroute.logisticsservice.domain.Slot;
import com.stackroute.logisticsservice.domain.Vehicle;
import com.stackroute.logisticsservice.exception.MongoConnectionException;
import com.stackroute.logisticsservice.repository.LogisticsRepository;

@Primary
@Service
@PropertySource(value = {"classpath:bootstrap.yml","classpath:logistics.properties"})
public class LogisticsServiceImpl implements LogisticsService {

	@Value("#{'${vehicle.capacity}'}")
	private int vehicleCapacity;

	@Value("#{'${vehicle.routeDurationLimit}'}")
	private int routeDurationLimit;

	@Value("#{'${slot.numberOfSlots}'}")
	private int noOfSlots;

	@Value("#{'${slot.numberOfVehicles}'}")
	private int noOfVehicles;

	@Value("#{'${slot.duration}'}")
	private int slotDuration;

	@Value("#{'${slot.startTime}'.split(',')}")
	private String[] slotStartTime;

	@Value("#{'${slot.endTime}'.split(',')}")
	private String[] slotEndTime;

	private LogisticsRepository logisticsRepository;

	@Autowired
	public LogisticsServiceImpl(LogisticsRepository logisticsRepository) {
		this.logisticsRepository = logisticsRepository;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.stackroute.logisticsservice.service.LogisticsService#getDateDetails(com.
	 * stackroute.logisticsservice.domain.Order)
	 */
	@Override
	public DateLogistics getDateDetails(Order order) throws MongoConnectionException {
		DateLogistics selectedDate;
		String date = order.getOrderDate();
		selectedDate = getDateLogistics(date);
		if (selectedDate == null) {
			// creates slots for date if date doesn't already exist
			Slot[] newSlots = new Slot[noOfSlots];
			for (int i = 0; i < noOfSlots; i++) {
				// creates vehicles for slots if date doesn't already exist
				Vehicle[] newVehicles = new Vehicle[noOfVehicles];
				for (int j = 0; j < noOfVehicles; j++) {
					Vehicle vehicle = new Vehicle(String.valueOf(j), String.valueOf(vehicleCapacity), null, null,
							String.valueOf(routeDurationLimit),null, null);
					newVehicles[j] = vehicle;
				}

				Slot slot = new Slot(String.valueOf(i), String.valueOf(slotDuration), slotStartTime[i], slotEndTime[i],
						String.valueOf(noOfVehicles), null, newVehicles);
				newSlots[i] = slot;
			}
			// creates a logistics of particular date if date doesn't exist
			DateLogistics newDate = new DateLogistics(order.getOrderDate(), newSlots);
			logisticsRepository.save(newDate);
			selectedDate = newDate;
			System.out.println(newDate.getDate());
		}
		return selectedDate;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.stackroute.logisticsservice.service.LogisticsService#saveOrderDetails(com
	 * .stackroute.logisticsservice.domain.Logistics)
	 */
	@Override
	public boolean saveOrderDetails(Route selectedRoute) throws MongoConnectionException {
		boolean isSaved;
		DateLogistics existingDateLogistics;
		DateLogistics updatedDateLogistics;
		String selectedSlotId;
		Slot selectedSlots[];
		Slot selectedSlot;
		Slot existingDateSlots[];

		selectedSlotId = selectedRoute.getNewOrder().getSelectedSlot();
		selectedSlots = selectedRoute.getDateLogistics().getSlots();
		selectedSlot = selectedSlots[Integer.parseInt(selectedSlotId)];
		
		//fetch date logistics of particular date
		existingDateLogistics = getDateLogistics(selectedRoute.getNewOrder().getOrderDate());
		
		//save route only for the selected slot
		if (existingDateLogistics != null) {
			existingDateSlots = existingDateLogistics.getSlots();
			existingDateSlots[Integer.parseInt(selectedSlotId)] = selectedSlot;
			existingDateLogistics.setSlots(existingDateSlots);
			updatedDateLogistics = existingDateLogistics;
			logisticsRepository.save(updatedDateLogistics);
			isSaved = true;
		} else {
			isSaved = false;
		}

		return isSaved;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.stackroute.logisticsservice.service.LogisticsService#deleteOrderDetails(
	 * com.stackroute.logisticsservice.domain.Order)
	 */
	@Override
	public boolean removeOrderDetails(Order order) throws MongoConnectionException {
		DateLogistics orderDateLogistics;
		DateLogistics updatedDate;
		Slot[] orderDateSlots; 
		String toDeleteOrderId;		
		boolean isDeleted = true;
		
		//fetch order details to be deleted
		orderDateLogistics = getDateLogistics(order.getOrderDate());
		orderDateSlots = orderDateLogistics.getSlots();		
		toDeleteOrderId = order.getOrderId();
		
		
		for(int i=0; i<noOfSlots; i++) {
			Vehicle vehicles[] = orderDateSlots[i].getSlotVehicle();
			for(int j=0; j<noOfVehicles; j++) {
				Order orders[] = vehicles[j].getVehicleRoute();
				for(int k=0; k<orders.length;k++) {
					if(orders[k].getOrderId()==toDeleteOrderId) {
						orders[k].setOrderId(null);
						orders=deleteOrder(orders);
						break;
					}
				}
				vehicles[j].setVehicleRoute(orders);
			}
			orderDateSlots[i].setSlotVehicle(vehicles);
		}
		
		
		updatedDate = new DateLogistics(order.getOrderDate(), orderDateSlots);
		logisticsRepository.save(updatedDate);
		
		
		return isDeleted;
	}
	
	public Location[] getOrderLocation(String date, String slotId, String vehicleId) throws MongoConnectionException{
		Location[] locations = null;
		DateLogistics selectedDate = getDateLogistics(date);
		Slot slot[] = selectedDate.getSlots();
		
		for(int i=0; i<noOfSlots; i++) {
			if(slotId.equals(String.valueOf(i))) {
				Vehicle[] vehicles = slot[i].getSlotVehicle();
				for(int j=0; j<noOfVehicles; j++) {
					if(vehicleId.equals(String.valueOf(j))) {
						Order order[]=vehicles[j].getVehicleRoute();
						locations= new Location[order.length];
						for(int k=0;k<order.length;k++) {
							locations[k]=order[k].getOrderLocation();
						}
					}
				}
			}
		}
		return locations;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.stackroute.logisticsservice.service.LogisticsService#getDateLogistics(
	 * java.lang.String)
	 */
	@Override
	public DateLogistics getDateLogistics(String date) throws MongoConnectionException {
		DateLogistics selectedDate;
		Optional<DateLogistics> optionalSelectedDate = logisticsRepository.findById(date);
		if (optionalSelectedDate.isPresent()) {
			// gets logistics details of specific date
			selectedDate = optionalSelectedDate.get();
		} else {
			selectedDate = null;
			System.out.println("null");
		}
		return selectedDate;
	}
	
	
	
	
	
	
	
	/*
	 * Service method to remove order from route
	 */
	public static Order[] deleteOrder(Order orders[]) {
		int j=0;
		Order updatedOrders[] = new Order[orders.length-1];
		
		for(int i = 0; i<updatedOrders.length; i++) {
	        if(orders[i].getOrderId()!=null)
	        {
	            updatedOrders[j] = orders[i];
	            j++;
	        }
	    }
		return updatedOrders;
	}

}
