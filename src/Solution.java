import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import logist.plan.Action;
import logist.plan.Action.Delivery;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.topology.Topology.City;

public class Solution {
	private HashMap<Vehicle, ArrayList<Action>> vehicleAgendas;
	private HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas;
	private List<Vehicle> vehicles;
	
	public Solution(List<Vehicle> vehicles, HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {
		this.simpleVehicleAgendas = simpleVehicleAgendas;
		this.vehicles = vehicles;
		generateCompleteTaskList();
		
	}
	
	//TODO make methods for handy neighboring solution generation

	public HashMap<Vehicle,ArrayList<Action>> getVehicleAgendas(){
		return vehicleAgendas;
	}
	
	/**
	 * 
	 * @return the total cost of this solution
	 */
	public double getTotalCost() {
		double totalCostOfThisSolution = 0;
		for (Vehicle vehicle : this.vehicleAgendas.keySet()) {
			Plan planOfThisVehicle = new Plan(vehicle.getCurrentCity(), this.vehicleAgendas.get(vehicle));
			double totalCostOfThisVehicle = planOfThisVehicle.totalDistance() * vehicle.costPerKm();
			totalCostOfThisSolution += totalCostOfThisVehicle;
		}
		return totalCostOfThisSolution;
	}	
	
	private void generateCompleteTaskList(){
		
		HashMap<Vehicle,ArrayList<Action>> completeVehicleAgenda = new HashMap<Vehicle,ArrayList<Action>>();
		
		//Generate complete vehicle task lists from simplified version
		for(Vehicle vehicle : this.vehicles){
			ArrayList<TaskWrapper> simpleTaskList = simpleVehicleAgendas.get(vehicle);
			City origin = vehicle.getCurrentCity(); //TODO: set origin city
			for(TaskWrapper task : simpleTaskList){
				if(completeVehicleAgenda.containsKey(vehicle)){
					completeVehicleAgenda.get(vehicle).addAll(getTaskActions(origin,task));	
				}
				else{
					completeVehicleAgenda.put(vehicle,getTaskActions(origin,task));		
				}
				origin = task.getEndCity();
			}
		}
		
		vehicleAgendas = completeVehicleAgenda;
	}
	
	private ArrayList<Action> getTaskActions(City origin, TaskWrapper task) {

		ArrayList<Action> actions = new ArrayList<Action>();
		ArrayList<City> pathCities = (ArrayList<City>) origin.pathTo(task.getEndCity());

		//first add actions for movement from vehicle's current location to target city
		for (City pathCity : pathCities) {
			actions.add(new Move(pathCity));
		}

		//then add action for specific task component
		if(task.isPickup()){
			actions.add(new Pickup(task.getTask()));
		}
		else{
			actions.add(new Delivery(task.getTask()));	
		}
		
		return actions;
	}

}
