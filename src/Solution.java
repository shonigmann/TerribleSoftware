import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import logist.plan.Action;
import logist.plan.Action.Delivery;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
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
			for(TaskWrapper task : simpleTaskList){
				City origin; //TODO: set origin city
				completeVehicleAgenda.get(vehicle).addAll(getTaskActions(origin,task));
			}
		}
		
		vehicleAgendas = completeVehicleAgenda;
	}
	
	private ArrayList<Action> getTaskActions(City origin, TaskWrapper task) {

		ArrayList<Action> actions = new ArrayList<Action>();
		ArrayList<City> pathCities = (ArrayList<City>) origin.pathTo(task.deliveryCity);

		actions.add(new Pickup(task));

		for (City pathCity : pathCities) {
			actions.add(new Move(pathCity));
		}

		actions.add(new Delivery(task));
		return actions;
	}

}
